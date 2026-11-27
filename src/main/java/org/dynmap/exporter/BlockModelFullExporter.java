package org.dynmap.exporter;

import java.io.IOException;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.hdmap.HDShader;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

final class BlockModelFullExporter extends AbstractBlockModelExporter {
    BlockModelFullExporter(DynmapWorld world, DynmapCore core, HDShader shader) {
        super(world, core, shader);
    }

    @Override
    protected void exportLoadedRegion(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ, boolean[] edgeBits) throws IOException {
        MapIterator iterator = cache.getIterator(rangeMinX, getMinY(), rangeMinZ);
        for (int x = rangeMinX; x <= rangeMaxX; x++) {
            edgeBits[BlockStep.X_PLUS.ordinal()] = (x == getMinX());
            edgeBits[BlockStep.X_MINUS.ordinal()] = (x == getMaxX());

            for (int z = rangeMinZ; z <= rangeMaxZ; z++) {
                edgeBits[BlockStep.Z_PLUS.ordinal()] = (z == getMinZ());
                edgeBits[BlockStep.Z_MINUS.ordinal()] = (z == getMaxZ());

                iterator.initialize(x, getMinY(), z);
                sink.setChunk(x >> 4, z >> 4);

                edgeBits[BlockStep.Y_MINUS.ordinal()] = true;
                edgeBits[BlockStep.Y_PLUS.ordinal()] = false;
                int blockId = iterator.getBlockTypeID();
                if (blockId > 0) {
                    handleBlock(blockId, iterator, edgeBits, sink);
                }

                edgeBits[BlockStep.Y_MINUS.ordinal()] = false;
                for (int y = getMinY() + 1; y < getMaxY(); y++) {
                    iterator.setY(y);
                    blockId = iterator.getBlockTypeID();
                    if (blockId > 0) {
                        handleBlock(blockId, iterator, edgeBits, sink);
                    }
                }

                edgeBits[BlockStep.Y_PLUS.ordinal()] = true;
                iterator.setY(getMaxY());
                blockId = iterator.getBlockTypeID();
                if (blockId > 0) {
                    handleBlock(blockId, iterator, edgeBits, sink);
                }
            }
        }
    }
}
