package org.dynmap.exporter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

final class BlockModelSimplifiedExporter extends AbstractBlockModelExporter {
    BlockModelSimplifiedExporter(DynmapWorld world, DynmapCore core, HDShader shader) {
        super(world, core, shader);
    }

    @Override
    protected void exportLoadedRegion(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ, boolean[] edgeBits) throws IOException {
        MapIterator geometryIterator = cache.getIterator(rangeMinX, getMaxY(), rangeMinZ);
        Set<String> emittedBlocks = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        ArrayDeque<BlockModelFloodFillSupport.FloodFillNode> queue = new ArrayDeque<BlockModelFloodFillSupport.FloodFillNode>();

        BlockModelFloodFillSupport.seedQueue(visited, queue, rangeMinX - 1, getMaxY(), rangeMinZ - 1, rangeMinX, rangeMaxX,
                rangeMinZ, rangeMaxZ, getMinY(), getMaxY(), new BlockModelFloodFillSupport.QueueDecision() {
                    @Override
                    public boolean shouldQueue(int x, int y, int z) throws IOException {
                        return canQueueSimplifiedBlock(geometryIterator, x, y, z);
                    }
                });

        for (int z = rangeMinZ; z <= rangeMaxZ; z++) {
            for (int x = rangeMinX; x <= rangeMaxX; x++) {
                if (!visited.add(BlockModelFloodFillSupport.blockKey(x, getMaxY(), z))) {
                    continue;
                }
                processSimplifiedPosition(geometryIterator, sink, emittedBlocks, queue, x, getMaxY(), z, true);
            }
        }

        while (!queue.isEmpty()) {
            BlockModelFloodFillSupport.FloodFillNode current = queue.removeFirst();
            for (int[] direction : BlockModelFloodFillSupport.DIRECTIONS) {
                int neighborX = current.x + direction[0];
                int neighborY = current.y + direction[1];
                int neighborZ = current.z + direction[2];
                if (!BlockModelFloodFillSupport.isWithinFloodFillBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX,
                        rangeMinZ, rangeMaxZ, getMinY(), getMaxY())) {
                    continue;
                }
                String key = BlockModelFloodFillSupport.blockKey(neighborX, neighborY, neighborZ);
                if (!visited.add(key)) {
                    continue;
                }
                processSimplifiedPosition(geometryIterator, sink, emittedBlocks, queue, neighborX, neighborY, neighborZ,
                        BlockModelFloodFillSupport.isWithinRenderBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX,
                                rangeMinZ, rangeMaxZ, getMinY(), getMaxY()));
            }
        }
    }

    private void processSimplifiedPosition(MapIterator geometryIterator, BlockModelExportSink sink, Set<String> emittedBlocks,
            ArrayDeque<BlockModelFloodFillSupport.FloodFillNode> queue, int x, int y, int z, boolean render)
            throws IOException {
        geometryIterator.initialize(x, y, z);
        int blockId = geometryIterator.getBlockTypeID();
        if (render && hasRenderableGeometry(geometryIterator, blockId)) {
            emitSelectedBlock(sink, geometryIterator, x, y, z, emittedBlocks);
        }
        if (canQueueSimplifiedBlock(geometryIterator, x, y, z)) {
            queue.addLast(new BlockModelFloodFillSupport.FloodFillNode(x, y, z));
        }
    }

    private boolean hasRenderableGeometry(MapIterator map, int blockId) throws IOException {
        return (blockId > 0) && (getAnySurfaceMaterial(resolveBlock(map, blockId)) != null);
    }

    private boolean canQueueSimplifiedBlock(MapIterator geometryIterator, int x, int y, int z) {
        geometryIterator.initialize(x, y, z);
        int blockId = geometryIterator.getBlockTypeID();
        if ((blockId > 0) && (org.dynmap.hdmap.TexturePack.HDTextureMap.getTransparency(blockId) == BlockTransparency.OPAQUE)) {
            return false;
        }
        if (!getWorld().canGetSkyLightLevel()) {
            return true;
        }
        return geometryIterator.getBlockSkyLight() >= getSimplifiedMinSkyLight();
    }

    private void emitSelectedBlock(BlockModelExportSink sink, MapIterator geometryIterator, int x, int y, int z,
            Set<String> emittedBlocks) throws IOException {
        String key = BlockModelFloodFillSupport.blockKey(x, y, z);
        if (!emittedBlocks.add(key)) {
            return;
        }
        geometryIterator.initialize(x, y, z);
        sink.setChunk(x >> 4, z >> 4);
        emitCurrentBlock(geometryIterator, sink);
    }

    private void emitCurrentBlock(MapIterator map, BlockModelExportSink sink) throws IOException {
        int blockId = map.getBlockTypeID();
        if (blockId <= 0) {
            return;
        }
        boolean[] edgeBits = new boolean[6];
        fillEdgeBits(map.getX(), map.getY(), map.getZ(), edgeBits);
        handleBlock(blockId, map, edgeBits, sink);
    }

    private void fillEdgeBits(int x, int y, int z, boolean[] edgeBits) {
        edgeBits[BlockStep.X_PLUS.ordinal()] = (x == getMinX());
        edgeBits[BlockStep.X_MINUS.ordinal()] = (x == getMaxX());
        edgeBits[BlockStep.Z_PLUS.ordinal()] = (z == getMinZ());
        edgeBits[BlockStep.Z_MINUS.ordinal()] = (z == getMaxZ());
        edgeBits[BlockStep.Y_MINUS.ordinal()] = (y == getMinY());
        edgeBits[BlockStep.Y_PLUS.ordinal()] = (y == getMaxY());
    }
}
