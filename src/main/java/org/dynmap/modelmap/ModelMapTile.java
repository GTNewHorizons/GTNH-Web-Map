package org.dynmap.modelmap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.dynmap.Client;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.MapType;
import org.dynmap.exporter.GLBExport;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.MapChunkCache;

public class ModelMapTile extends MapTile {
    public final ModelMap map;
    public final int tx;
    public final int tz;

    public ModelMapTile(DynmapWorld world, ModelMap map, int tx, int tz) {
        super(world);
        this.map = map;
        this.tx = tx;
        this.tz = tz;
    }

    public ModelMapTile(DynmapWorld world, String parm) throws Exception {
        super(world);

        String[] parms = parm.split(",", 3);
        if (parms.length != 3) {
            throw new Exception("wrong parameter count");
        }
        this.tx = Integer.parseInt(parms[0]);
        this.tz = Integer.parseInt(parms[1]);

        ModelMap resolvedMap = null;
        for (MapType type : world.maps) {
            if ((type instanceof ModelMap) && parms[2].equals(type.getName())) {
                resolvedMap = (ModelMap) type;
                break;
            }
        }
        if (resolvedMap == null) {
            throw new Exception("invalid model map");
        }
        this.map = resolvedMap;
    }

    @Override
    protected String saveTileData() {
        return String.format("%d,%d,%s", tx, tz, map.getName());
    }

    @Override
    public boolean render(MapChunkCache cache, String mapname) {
        DynmapCore core = map.getCore();
        ModelMap.TileAddress address = map.getTileAddress(tx, tz);
        int minBlockX = address.getMinChunkX() * 16;
        int minBlockZ = address.getMinChunkZ() * 16;
        int maxBlockX = (address.getMaxChunkX() * 16) + 15;
        int maxBlockZ = (address.getMaxChunkZ() * 16) + 15;

        GLBExport export = new GLBExport(null, map.getShader(), world, core, map.getName() + "_" + tx + "_" + tz);
        export.setRenderBounds(minBlockX, world.minY, minBlockZ, maxBlockX, world.worldheight - 1, maxBlockZ);
        export.setCullExportRegionEdges(map.isCullExportRegionEdges());
        export.setLightingMode(map.getLightingMode().toExportLightingMode());

        BufferOutputStream glb;
        try {
            glb = export.exportToBuffer(cache);
        } catch (IOException iox) {
            Log.severe("ModelMap export failed for " + toString() + ": " + iox.getMessage());
            return false;
        }

        MapStorage storage = world.getMapStorage();
        MapStorageTile tile = storage.getTile(world, map, tx, tz, 0, MapType.ImageVariant.STANDARD);
        boolean updated = false;
        boolean rendered = (glb != null);
        boolean compressOutput = map.getOutputCompression() == ModelMap.OutputCompression.GZIP;

        tile.getWriteLock();
        try {
            if (glb == null) {
                if (tile.exists()) {
                    if (tile.delete()) {
                        MapManager.mapman.pushUpdate(world, new Client.Tile(tile.getURI()));
                        updated = true;
                    }
                }
            } else {
                long crc = MapStorage.calculateTileHashCode(glb.buf, 0, glb.len);
                BufferOutputStream output;
                try {
                    output = compressOutput ? gzip(glb) : glb;
                } catch (IOException iox) {
                    Log.severe("ModelMap compression failed for " + toString() + ": " + iox.getMessage());
                    return false;
                }
                boolean rewrite = !tile.matchesHashCode(crc);
                if (!rewrite) {
                    TileRead existing = tile.read();
                    rewrite = (existing == null) || (isGzipCompressed(existing.image) != compressOutput);
                }
                if (rewrite) {
                    if (tile.write(crc, output, System.currentTimeMillis())) {
                        MapManager.mapman.pushUpdate(world, new Client.Tile(tile.getURI()));
                        updated = true;
                    }
                }
            }
        } finally {
            tile.releaseWriteLock();
        }

        MapManager.mapman.updateStatistics(this, map.getPrefix(), rendered, updated, !rendered);
        return updated;
    }

    private static BufferOutputStream gzip(BufferOutputStream input) throws IOException {
        BufferOutputStream compressed = new BufferOutputStream();
        OutputStream gzip = new GZIPOutputStream(compressed);
        try {
            gzip.write(input.buf, 0, input.len);
        } finally {
            gzip.close();
        }
        return compressed;
    }

    private static boolean isGzipCompressed(org.dynmap.utils.BufferInputStream input) {
        return (input != null) && (input.length() >= 2) && ((input.buffer()[0] & 0xFF) == 0x1F)
                && ((input.buffer()[1] & 0xFF) == 0x8B);
    }

    @Override
    public List<DynmapChunk> getRequiredChunks() {
        return map.getRequiredChunksForTile(tx, tz);
    }

    @Override
    public MapTile[] getAdjecentTiles() {
        return map.getAdjecentTiles(this);
    }

    @Override
    public int hashCode() {
        return tx ^ tz ^ map.hashCode() ^ world.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ModelMapTile) {
            return equals((ModelMapTile) obj);
        }
        return false;
    }

    public boolean equals(ModelMapTile other) {
        return (other.tx == tx) && (other.tz == tz) && (other.map == map) && (other.world == world);
    }

    @Override
    public boolean isBiomeDataNeeded() {
        return map.getShader().isBiomeDataNeeded();
    }

    @Override
    public boolean isHightestBlockYDataNeeded() {
        return map.getShader().isHightestBlockYDataNeeded();
    }

    @Override
    public boolean isRawBiomeDataNeeded() {
        return map.getShader().isRawBiomeDataNeeded();
    }

    @Override
    public boolean isBlockTypeDataNeeded() {
        return map.getShader().isBlockTypeDataNeeded();
    }

    @Override
    public int tileOrdinalX() {
        return tx;
    }

    @Override
    public int tileOrdinalY() {
        return tz;
    }

    @Override
    public String toString() {
        return world.getName() + ":" + map.getName() + "," + tx + "," + tz;
    }
}
