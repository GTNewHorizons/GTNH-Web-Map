package org.dynmap.modelmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.utils.TileFlags;
import org.dynmap.hdmap.TexturePackHDShader;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTileEnumCB;

public class ModelMap extends MapType {
    public static final int DEFAULT_GRANULARITY = 1;
    public static final String DEFAULT_SHADER = "stdtexture";

    public enum AssetFormat {
        GLB("glb", "model/gltf-binary");

        private final String fileExtension;
        private final String contentType;

        AssetFormat(String fileExtension, String contentType) {
            this.fileExtension = fileExtension;
            this.contentType = contentType;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getContentType() {
            return contentType;
        }
    }

    public static final class TileAddress {
        private final String prefix;
        private final int tileX;
        private final int tileZ;
        private final int granularity;
        private final AssetFormat format;

        private TileAddress(String prefix, int tileX, int tileZ, int granularity, AssetFormat format) {
            this.prefix = prefix;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.granularity = granularity;
            this.format = format;
        }

        public int getTileX() {
            return tileX;
        }

        public int getTileZ() {
            return tileZ;
        }

        public int getGranularity() {
            return granularity;
        }

        public int getMinChunkX() {
            return tileX * granularity;
        }

        public int getMinChunkZ() {
            return tileZ * granularity;
        }

        public int getMaxChunkX() {
            return getMinChunkX() + granularity - 1;
        }

        public int getMaxChunkZ() {
            return getMinChunkZ() + granularity - 1;
        }

        public String getBucketPath() {
            return (tileX >> 5) + "_" + (tileZ >> 5);
        }

        public String getBaseURI() {
            return prefix + "/" + getBucketPath() + "/" + tileX + "_" + tileZ;
        }

        public String getURI() {
            return getBaseURI() + "." + format.getFileExtension();
        }

        public String getContentType() {
            return format.getContentType();
        }
    }

    private final DynmapCore core;
    private String name;
    private String title;
    private String icon;
    private String prefix;
    private String appendToWorld;
    private int granularity;
    private TexturePackHDShader shader;

    public ModelMap(DynmapCore core, ConfigurationNode configuration) {
        this.core = core;
        name = configuration.getString("name", null);
        if (name == null) {
            Log.severe("ModelMap missing required attribute 'name' - disabled");
            return;
        }

        String shaderId = configuration.getString("shader", DEFAULT_SHADER);
        shader = resolveShader(shaderId);
        if (shader == null) {
            name = null;
            return;
        }

        granularity = configuration.getInteger("granularity", DEFAULT_GRANULARITY);
        if (granularity < 1) {
            Log.severe("ModelMap '" + name + "' set invalid granularity " + granularity + " - using " + DEFAULT_GRANULARITY);
            granularity = DEFAULT_GRANULARITY;
        }

        prefix = configuration.getString("prefix", name);
        title = configuration.getString("title", name);
        icon = configuration.getString("icon");
        appendToWorld = configuration.getString("append_to_world", "");
        setProtected(configuration.getBoolean("protected", false));
        setTileUpdateDelay(configuration.getInteger("tileupdatedelay", -1));
    }

    @Override
    public ConfigurationNode saveConfiguration() {
        ConfigurationNode cn = super.saveConfiguration();
        cn.put("title", title);
        if (icon != null) {
            cn.put("icon", icon);
        }
        cn.put("prefix", prefix);
        if (shader != null) {
            cn.put("shader", shader.getName());
        }
        cn.put("granularity", granularity);
        cn.put("append_to_world", appendToWorld);
        cn.put("protected", isProtected());
        if (tileupdatedelay > 0) {
            cn.put("tileupdatedelay", tileupdatedelay);
        }
        return cn;
    }

    private TexturePackHDShader resolveShader(String shaderId) {
        Object requestedShader = MapManager.mapman.hdmapman.shaders.get(shaderId);
        if (requestedShader == null) {
            if (!DEFAULT_SHADER.equals(shaderId)) {
                Object defaultShader = MapManager.mapman.hdmapman.shaders.get(DEFAULT_SHADER);
                if (defaultShader instanceof TexturePackHDShader) {
                    Log.severe("ModelMap '" + name + "' loading invalid shader '" + shaderId + "' - using '" + DEFAULT_SHADER + "' shader");
                    return (TexturePackHDShader) defaultShader;
                }
            }
            Log.severe("ModelMap '" + name + "' loading invalid shader '" + shaderId + "' - map disabled");
            return null;
        }
        if (!(requestedShader instanceof TexturePackHDShader)) {
            Log.severe("ModelMap '" + name + "' shader '" + shaderId + "' must extend TexturePackHDShader - map disabled");
            return null;
        }
        return (TexturePackHDShader) requestedShader;
    }

    public TexturePackHDShader getShader() {
        return shader;
    }

    public int getGranularity() {
        return granularity;
    }

    public int getChunkSpan() {
        return granularity;
    }

    public int getBlockSpan() {
        return granularity * 16;
    }

    public AssetFormat getAssetFormat() {
        return AssetFormat.GLB;
    }

    @Override
    public ImageEncoding getTileEncoding() {
        return ImageEncoding.GLB;
    }

    public String getAssetContentType() {
        return getAssetFormat().getContentType();
    }

    public String getAssetFileExtension() {
        return getAssetFormat().getFileExtension();
    }

    public TileAddress getTileAddress(int tileX, int tileZ) {
        return new TileAddress(prefix, tileX, tileZ, granularity, getAssetFormat());
    }

    public TileAddress getTileAddressForChunk(int chunkX, int chunkZ) {
        return getTileAddress(Math.floorDiv(chunkX, granularity), Math.floorDiv(chunkZ, granularity));
    }

    public TileAddress getTileAddressForBlock(int blockX, int blockZ) {
        return getTileAddressForChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }

    public List<DynmapChunk> getRequiredChunksForTile(int tileX, int tileZ) {
        TileAddress address = getTileAddress(tileX, tileZ);
        ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>(granularity * granularity);
        for (int chunkX = address.getMinChunkX(); chunkX <= address.getMaxChunkX(); chunkX++) {
            for (int chunkZ = address.getMinChunkZ(); chunkZ <= address.getMaxChunkZ(); chunkZ++) {
                chunks.add(new DynmapChunk(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    @Override
    public void addMapTiles(List<MapTile> list, DynmapWorld w, int tx, int ty) {
        list.add(new ModelMapTile(w, this, tx, ty));
    }

    @Override
    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int x, int y, int z) {
        return Collections.singletonList(new TileFlags.TileCoord(
                Math.floorDiv(Math.floorDiv(x, 16), granularity),
                Math.floorDiv(Math.floorDiv(z, 16), granularity)));
    }

    @Override
    public List<TileFlags.TileCoord> getTileCoords(DynmapWorld w, int minx, int miny, int minz, int maxx, int maxy,
            int maxz) {
        int minTileX = Math.floorDiv(Math.floorDiv(minx, 16), granularity);
        int maxTileX = Math.floorDiv(Math.floorDiv(maxx, 16), granularity);
        int minTileZ = Math.floorDiv(Math.floorDiv(minz, 16), granularity);
        int maxTileZ = Math.floorDiv(Math.floorDiv(maxz, 16), granularity);

        ArrayList<TileFlags.TileCoord> coords =
                new ArrayList<TileFlags.TileCoord>((maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1));
        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                coords.add(new TileFlags.TileCoord(tileX, tileZ));
            }
        }
        return coords;
    }

    @Override
    public MapTile[] getAdjecentTiles(MapTile tile) {
        if (!(tile instanceof ModelMapTile)) {
            return new MapTile[0];
        }
        ModelMapTile modelTile = (ModelMapTile) tile;
        DynmapWorld world = modelTile.getDynmapWorld();
        return new MapTile[] {
                new ModelMapTile(world, this, modelTile.tx - 1, modelTile.tz),
                new ModelMapTile(world, this, modelTile.tx + 1, modelTile.tz),
                new ModelMapTile(world, this, modelTile.tx, modelTile.tz - 1),
                new ModelMapTile(world, this, modelTile.tx, modelTile.tz + 1)
        };
    }

    @Override
    public List<DynmapChunk> getRequiredChunks(MapTile tile) {
        if (!(tile instanceof ModelMapTile)) {
            return Collections.emptyList();
        }
        ModelMapTile modelTile = (ModelMapTile) tile;
        return getRequiredChunksForTile(modelTile.tx, modelTile.tz);
    }

    @Override
    public int getTileSize() {
        return getBlockSpan();
    }

    @Override
    public boolean isZoomOutSupported() {
        return false;
    }

    @Override
    public void purgeOldTiles(final DynmapWorld world, final TileFlags rendered) {
        final MapStorage storage = world.getMapStorage();
        storage.enumMapTiles(world, this, new MapStorageTileEnumCB() {
            @Override
            public void tileFound(MapStorageTile tile, ImageEncoding fmt) {
                if (fmt != getTileEncoding()) {
                    tile.delete();
                }
                else if ((tile.zoom == 0) && !rendered.getFlag(tile.x, tile.y)) {
                    tile.delete();
                }
            }
        });
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<MapType> getMapsSharingRender(DynmapWorld w) {
        return Collections.<MapType>singletonList(this);
    }

    @Override
    public List<String> getMapNamesSharingRender(DynmapWorld w) {
        return Collections.singletonList(name);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon == null ? "" : icon;
    }

    public String getAppendToWorld() {
        return appendToWorld;
    }

    public boolean setPrefix(String value) {
        if (!value.equals(prefix)) {
            prefix = value;
            return true;
        }
        return false;
    }

    public boolean setTitle(String value) {
        if (!value.equals(title)) {
            title = value;
            return true;
        }
        return false;
    }

    public boolean setIcon(String value) {
        if ("".equals(value)) {
            value = null;
        }
        icon = value;
        return true;
    }

    public boolean setAppendToWorld(String value) {
        if (!value.equals(appendToWorld)) {
            appendToWorld = value;
            return true;
        }
        return false;
    }

    public boolean setGranularity(int value) {
        if (value < 1) {
            return false;
        }
        if (value != granularity) {
            granularity = value;
            return true;
        }
        return false;
    }

    public boolean setShader(TexturePackHDShader value) {
        if (value != null && shader != value) {
            shader = value;
            return true;
        }
        return false;
    }

    public DynmapCore getCore() {
        return core;
    }
}
