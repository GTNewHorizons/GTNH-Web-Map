package org.dynmap.modelmap;

import java.util.List;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapWorld;
import org.dynmap.MapTile;
import org.dynmap.MapType;
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
        return false;
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
