package org.dynmap;

import org.dynmap.storage.MapStorageTile;

public interface CustomZoomOutMapType {
    void processZoomOutTile(DynmapWorld world, MapTypeState state, MapStorageTile tile, boolean firstVariant);
}
