package org.dynmap.modelmap;

import static org.dynmap.JSONUtils.a;
import static org.dynmap.JSONUtils.s;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dynmap.CustomZoomOutMapType;
import org.dynmap.Client;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.MapTypeState;
import org.dynmap.exporter.BlockModelExportMode;
import org.dynmap.exporter.GLBExport;
import org.dynmap.hdmap.TexturePackHDShader;
import org.dynmap.utils.TileFlags;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.dynmap.storage.MapStorageTileEnumCB;
import org.json.simple.JSONObject;

public class ModelMap extends MapType implements CustomZoomOutMapType {
    public enum LightingMode {
        DAY("day"),
        NIGHT("night"),
        BOTH("both");

        private final String id;

        LightingMode(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public GLBExport.LightingMode toExportLightingMode() {
            switch (this) {
                case NIGHT:
                    return GLBExport.LightingMode.NIGHT;
                case BOTH:
                    return GLBExport.LightingMode.BOTH;
                case DAY:
                default:
                    return GLBExport.LightingMode.DAY;
            }
        }

        public static LightingMode fromId(String value) {
            if (value == null) {
                return null;
            }
            for (LightingMode mode : values()) {
                if (mode.id.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return null;
        }
    }

    public static final int DEFAULT_GRANULARITY = 1;
    public static final String DEFAULT_SHADER = "stdtexture";
    public static final boolean DEFAULT_CULL_EXPORT_REGION_EDGES = true;
    public static final OutputCompression DEFAULT_OUTPUT_COMPRESSION = OutputCompression.GZIP;
    public static final LightingMode DEFAULT_LIGHTING_MODE = LightingMode.DAY;
    public static final DetailMode DEFAULT_DETAIL_MODE = DetailMode.FULL;
    public static final int DEFAULT_MAP_ZOOMOUT = 0;
    public static final int DEFAULT_SIMPLIFIED_MIN_SKYLIGHT = 7;
    public static final double DEFAULT_DAY_AMBIENT_LIGHT = 0.7;
    public static final double DEFAULT_NIGHT_AMBIENT_LIGHT = 0.14;
    public static final double DEFAULT_DAY_SUN_LIGHT = 0.8;
    public static final double DEFAULT_NIGHT_SUN_LIGHT = 0.16;
    public static final double DEFAULT_MARKER_ICON_VISIBLE_DISTANCE = 240.0;
    public static final double DEFAULT_MARKER_ICON_FADE_RANGE = 16.0;
    public static final double DEFAULT_MARKER_LABEL_DISTANCE = 16.0;
    public static final double DEFAULT_MARKER_GRID_VISIBLE_DISTANCE = 48.0;
    public static final double DEFAULT_MARKER_GRID_FADE_RANGE = 16.0;

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

    public enum DetailMode {
        FULL("full", BlockModelExportMode.FULL),
        SURFACE("surface", BlockModelExportMode.SIMPLIFIED),
        LOW_POLY("low_poly", BlockModelExportMode.LOW_POLY);

        private final String id;
        private final BlockModelExportMode exportMode;

        DetailMode(String id, BlockModelExportMode exportMode) {
            this.id = id;
            this.exportMode = exportMode;
        }

        public String getId() {
            return id;
        }

        public BlockModelExportMode getExportMode() {
            return exportMode;
        }

        public static DetailMode fromId(String value) {
            if (value == null) {
                return null;
            }
            if ("simplified".equalsIgnoreCase(value)) {
                return SURFACE;
            }
            if ("zoomout".equalsIgnoreCase(value) || "lowpoly".equalsIgnoreCase(value)) {
                return LOW_POLY;
            }
            for (DetailMode mode : values()) {
                if (mode.id.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return null;
        }
    }

    public enum OutputCompression {
        NONE("none", null),
        GZIP("gzip", "gzip");

        private final String id;
        private final String contentEncoding;

        OutputCompression(String id, String contentEncoding) {
            this.id = id;
            this.contentEncoding = contentEncoding;
        }

        public String getId() {
            return id;
        }

        public String getContentEncoding() {
            return contentEncoding;
        }

        public static OutputCompression fromId(String value) {
            if (value == null) {
                return null;
            }
            for (OutputCompression compression : values()) {
                if (compression.id.equalsIgnoreCase(value)) {
                    return compression;
                }
            }
            return null;
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
    private boolean cullExportRegionEdges;
    private OutputCompression outputCompression;
    private LightingMode lightingMode;
    private DetailMode detailMode;
    private int mapZoomOut;
    private int simplifiedMinSkyLight;
    private double dayAmbientLight;
    private double nightAmbientLight;
    private double daySunLight;
    private double nightSunLight;
    private double markerIconVisibleDistance;
    private double markerIconFadeRange;
    private double markerLabelDistance;
    private double markerGridVisibleDistance;
    private double markerGridFadeRange;

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
        cullExportRegionEdges = configuration.getBoolean("cull_export_region_edges", DEFAULT_CULL_EXPORT_REGION_EDGES);
        outputCompression =
                resolveOutputCompression(configuration.getString("compression", DEFAULT_OUTPUT_COMPRESSION.getId()));
        lightingMode = resolveLightingMode(configuration.getString("lighting_mode", DEFAULT_LIGHTING_MODE.getId()));
        detailMode = resolveDetailMode(configuration.getString("detail_mode", DEFAULT_DETAIL_MODE.getId()));
        mapZoomOut = resolveMapZoomOut(configuration.getInteger("mapzoomout", DEFAULT_MAP_ZOOMOUT));
        simplifiedMinSkyLight = resolveSimplifiedMinSkyLight(
                configuration.getInteger("simplified_min_skylight", DEFAULT_SIMPLIFIED_MIN_SKYLIGHT));
        dayAmbientLight = resolveLightLevel(configuration.getDouble("ambientlightday", DEFAULT_DAY_AMBIENT_LIGHT),
                DEFAULT_DAY_AMBIENT_LIGHT, "ambientlightday");
        nightAmbientLight = resolveLightLevel(configuration.getDouble("ambientlightnight", DEFAULT_NIGHT_AMBIENT_LIGHT),
                DEFAULT_NIGHT_AMBIENT_LIGHT, "ambientlightnight");
        daySunLight =
                resolveLightLevel(configuration.getDouble("sunlightday", DEFAULT_DAY_SUN_LIGHT), DEFAULT_DAY_SUN_LIGHT,
                        "sunlightday");
        nightSunLight = resolveLightLevel(configuration.getDouble("sunlightnight", DEFAULT_NIGHT_SUN_LIGHT),
                DEFAULT_NIGHT_SUN_LIGHT, "sunlightnight");
        markerIconVisibleDistance = resolveDistanceSetting(
                configuration.getDouble("marker_icon_visible_distance", DEFAULT_MARKER_ICON_VISIBLE_DISTANCE),
                DEFAULT_MARKER_ICON_VISIBLE_DISTANCE, "marker_icon_visible_distance");
        markerIconFadeRange = resolveDistanceSetting(
                configuration.getDouble("marker_icon_fade_range", DEFAULT_MARKER_ICON_FADE_RANGE),
                DEFAULT_MARKER_ICON_FADE_RANGE, "marker_icon_fade_range");
        markerLabelDistance = resolveDistanceSetting(
                configuration.getDouble("marker_label_distance", DEFAULT_MARKER_LABEL_DISTANCE),
                DEFAULT_MARKER_LABEL_DISTANCE, "marker_label_distance");
        markerGridVisibleDistance = resolveDistanceSetting(
                configuration.getDouble("marker_grid_visible_distance", DEFAULT_MARKER_GRID_VISIBLE_DISTANCE),
                DEFAULT_MARKER_GRID_VISIBLE_DISTANCE, "marker_grid_visible_distance");
        markerGridFadeRange = resolveDistanceSetting(
                configuration.getDouble("marker_grid_fade_range", DEFAULT_MARKER_GRID_FADE_RANGE),
                DEFAULT_MARKER_GRID_FADE_RANGE, "marker_grid_fade_range");
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
        cn.put("cull_export_region_edges", cullExportRegionEdges);
        cn.put("compression", outputCompression.getId());
        cn.put("lighting_mode", lightingMode.getId());
        cn.put("detail_mode", detailMode.getId());
        cn.put("mapzoomout", mapZoomOut);
        cn.put("simplified_min_skylight", simplifiedMinSkyLight);
        cn.put("ambientlightday", dayAmbientLight);
        cn.put("ambientlightnight", nightAmbientLight);
        cn.put("sunlightday", daySunLight);
        cn.put("sunlightnight", nightSunLight);
        cn.put("marker_icon_visible_distance", markerIconVisibleDistance);
        cn.put("marker_icon_fade_range", markerIconFadeRange);
        cn.put("marker_label_distance", markerLabelDistance);
        cn.put("marker_grid_visible_distance", markerGridVisibleDistance);
        cn.put("marker_grid_fade_range", markerGridFadeRange);
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

    private OutputCompression resolveOutputCompression(String compressionId) {
        OutputCompression resolved = OutputCompression.fromId(compressionId);
        if (resolved == null) {
            Log.severe("ModelMap '" + name + "' set invalid compression '" + compressionId + "' - using '"
                    + DEFAULT_OUTPUT_COMPRESSION.getId() + "'");
            return DEFAULT_OUTPUT_COMPRESSION;
        }
        return resolved;
    }

    private LightingMode resolveLightingMode(String lightingModeId) {
        LightingMode resolved = LightingMode.fromId(lightingModeId);
        if (resolved == null) {
            Log.severe("ModelMap '" + name + "' set invalid lighting_mode '" + lightingModeId + "' - using '"
                    + DEFAULT_LIGHTING_MODE.getId() + "'");
            return DEFAULT_LIGHTING_MODE;
        }
        return resolved;
    }

    private DetailMode resolveDetailMode(String detailModeId) {
        DetailMode resolved = DetailMode.fromId(detailModeId);
        if (resolved == null) {
            Log.severe("ModelMap '" + name + "' set invalid detail_mode '" + detailModeId + "' - using '"
                    + DEFAULT_DETAIL_MODE.getId() + "'");
            return DEFAULT_DETAIL_MODE;
        }
        return resolved;
    }

    private int resolveMapZoomOut(int value) {
        if (value < 0) {
            Log.severe("ModelMap '" + name + "' set invalid mapzoomout " + value + " - using " + DEFAULT_MAP_ZOOMOUT);
            return DEFAULT_MAP_ZOOMOUT;
        }
        return value;
    }

    private int resolveSimplifiedMinSkyLight(int value) {
        if ((value < 0) || (value > 15)) {
            Log.severe("ModelMap '" + name + "' set invalid simplified_min_skylight " + value + " - using "
                    + DEFAULT_SIMPLIFIED_MIN_SKYLIGHT);
            return DEFAULT_SIMPLIFIED_MIN_SKYLIGHT;
        }
        return value;
    }

    private double resolveLightLevel(double value, double defaultValue, String settingName) {
        if (value < 0.0) {
            Log.severe("ModelMap '" + name + "' set invalid " + settingName + " " + value + " - using " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    private double resolveDistanceSetting(double value, double defaultValue, String settingName) {
        if (value < 0.0) {
            Log.severe("ModelMap '" + name + "' set invalid " + settingName + " " + value + " - using " + defaultValue);
            return defaultValue;
        }
        return value;
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

    public DetailMode getDetailMode() {
        return detailMode;
    }

    public int getConfiguredZoomOutLevels() {
        return mapZoomOut;
    }

    public int getSimplifiedMinSkyLight() {
        return simplifiedMinSkyLight;
    }

    public int getTotalZoomOutLevels(DynmapWorld world) {
        return world.getExtraZoomOutLevels() + mapZoomOut;
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
        ArrayList<DynmapChunk> chunks = new ArrayList<DynmapChunk>((granularity + 2) * (granularity + 2));
        for (int chunkX = address.getMinChunkX() - 1; chunkX <= address.getMaxChunkX() + 1; chunkX++) {
            for (int chunkZ = address.getMinChunkZ() - 1; chunkZ <= address.getMaxChunkZ() + 1; chunkZ++) {
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
        return true;
    }

    @Override
    public int getMapZoomOutLevels() {
        return mapZoomOut;
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
                else if (tile.zoom > getTotalZoomOutLevels(world)) {
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
    public void buildClientConfiguration(JSONObject worldObject, DynmapWorld world) {
        JSONObject o = new JSONObject();
        s(o, "type", "ModelMapType");
        s(o, "name", name);
        s(o, "title", title);
        s(o, "icon", getIcon());
        s(o, "prefix", prefix);
        s(o, "protected", isProtected());
        s(o, "tileblocksize", getBlockSpan());
        s(o, "granularity", granularity);
        s(o, "tileformat", getTileFileExt());
        s(o, "compassview", "N");
        s(o, "bigmap", false);
        s(o, "modelminzoom", 0);
        s(o, "modelmaxzoom", Math.max(6, getTotalZoomOutLevels(world) + 1));
        s(o, "modelzoomout", getTotalZoomOutLevels(world));
        s(o, "detailmode", detailMode.getId());
        s(o, "lightingmode", lightingMode.getId());
        s(o, "ambientlightday", dayAmbientLight);
        s(o, "ambientlightnight", nightAmbientLight);
        s(o, "sunlightday", daySunLight);
        s(o, "sunlightnight", nightSunLight);
        s(o, "markericonvisibledistance", markerIconVisibleDistance);
        s(o, "markericonfaderange", markerIconFadeRange);
        s(o, "markerlabeldistance", markerLabelDistance);
        s(o, "markergridvisibledistance", markerGridVisibleDistance);
        s(o, "markergridfaderange", markerGridFadeRange);
        if (appendToWorld.length() > 0) {
            s(o, "append_to_world", appendToWorld);
        }
        a(worldObject, "maps", o);
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

    public boolean isCullExportRegionEdges() {
        return cullExportRegionEdges;
    }

    public LightingMode getLightingMode() {
        return lightingMode;
    }

    public OutputCompression getOutputCompression() {
        return outputCompression;
    }

    public GLBExport createExport(DynmapWorld world, int tileX, int tileZ, int zoom, String basename) {
        GLBExport export = new GLBExport(null, getShader(), world, core, basename);
        int tileChunkSpan = granularity << zoom;
        int minBlockX = tileX * granularity * 16;
        int minBlockZ = tileZ * granularity * 16;
        int maxBlockX = minBlockX + (tileChunkSpan * 16) - 1;
        int maxBlockZ = minBlockZ + (tileChunkSpan * 16) - 1;
        export.setRenderBounds(minBlockX, world.minY, minBlockZ, maxBlockX, world.worldheight - 1, maxBlockZ);
        export.setCullExportRegionEdges(cullExportRegionEdges);
        export.setLightingMode(getLightingMode().toExportLightingMode());
        export.setExportMode((zoom > 0) ? BlockModelExportMode.ZOOMOUT : detailMode.getExportMode());
        export.setSimplifiedMinSkyLight(simplifiedMinSkyLight);
        return export;
    }

    public boolean writeRenderedTile(DynmapWorld world, MapStorageTile tile, BufferOutputStream glb, String tileName) {
        boolean updated = false;
        boolean compressOutput = outputCompression == OutputCompression.GZIP;

        tile.getWriteLock();
        try {
            if (glb == null) {
                if (tile.exists() && tile.delete()) {
                    MapManager.mapman.pushUpdate(world, new Client.Tile(tile.getURI()));
                    updated = true;
                }
            } else {
                long crc = MapStorage.calculateTileHashCode(glb.buf, 0, glb.len);
                BufferOutputStream output;
                try {
                    output = compressOutput ? ModelMapTile.gzip(glb) : glb;
                } catch (IOException iox) {
                    Log.severe("ModelMap compression failed for " + tileName + ": " + iox.getMessage());
                    return false;
                }
                boolean rewrite = !tile.matchesHashCode(crc);
                if (!rewrite) {
                    TileRead existing = tile.read();
                    rewrite = (existing == null) || (ModelMapTile.isGzipCompressed(existing.image) != compressOutput);
                }
                if (rewrite && tile.write(crc, output, System.currentTimeMillis())) {
                    MapManager.mapman.pushUpdate(world, new Client.Tile(tile.getURI()));
                    updated = true;
                }
            }
        } finally {
            tile.releaseWriteLock();
        }
        return updated;
    }

    @Override
    public void processZoomOutTile(DynmapWorld world, MapTypeState state, MapStorageTile tile, boolean firstVariant) {
        if (firstVariant) {
            state.clearZoomOutInv(tile.x, tile.y, tile.zoom);
        }
        MapStorageTile zoomTile = tile.getZoomOutTile();
        if (zoomTile.zoom > getTotalZoomOutLevels(world)) {
            return;
        }

        BufferOutputStream glb = null;
        String tileName = world.getName() + ":" + getName() + "," + zoomTile.x + "," + zoomTile.y + ",z" + zoomTile.zoom;
        try {
            glb = createExport(world, zoomTile.x, zoomTile.y, zoomTile.zoom,
                    getName() + "_" + zoomTile.x + "_" + zoomTile.y + "_z" + zoomTile.zoom).exportToBuffer();
        } catch (IOException iox) {
            Log.severe("ModelMap zoomout export failed for " + tileName + ": " + iox.getMessage());
            return;
        }

        boolean updated = writeRenderedTile(world, zoomTile, glb, tileName);
        if ((zoomTile.zoom < getTotalZoomOutLevels(world)) && ((glb != null) || updated)) {
            world.enqueueZoomOutUpdate(zoomTile);
        }
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

    public boolean setCullExportRegionEdges(boolean value) {
        if (cullExportRegionEdges != value) {
            cullExportRegionEdges = value;
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

    public boolean setOutputCompression(OutputCompression value) {
        if ((value != null) && (value != outputCompression)) {
            outputCompression = value;
            return true;
        }
        return false;
    }

    public boolean setDetailMode(DetailMode value) {
        if ((value != null) && (value != detailMode)) {
            detailMode = value;
            return true;
        }
        return false;
    }

    public boolean setMapZoomOut(int value) {
        if (value < 0) {
            return false;
        }
        if (value != mapZoomOut) {
            mapZoomOut = value;
            return true;
        }
        return false;
    }

    public boolean setSimplifiedMinSkyLight(int value) {
        if ((value < 0) || (value > 15)) {
            return false;
        }
        if (value != simplifiedMinSkyLight) {
            simplifiedMinSkyLight = value;
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
