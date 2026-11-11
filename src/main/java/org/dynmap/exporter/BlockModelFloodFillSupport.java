package org.dynmap.exporter;

import java.util.ArrayDeque;
import java.util.Set;

final class BlockModelFloodFillSupport {
    static final int[][] DIRECTIONS =
            { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

    static final class FloodFillNode {
        final int x;
        final int y;
        final int z;

        FloodFillNode(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    interface QueueDecision {
        boolean shouldQueue(int x, int y, int z) throws java.io.IOException;
    }

    private BlockModelFloodFillSupport() {
    }

    static String blockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    static boolean isWithinRenderBounds(int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ,
            int minY, int maxY) {
        return (x >= rangeMinX) && (x <= rangeMaxX) && (y >= minY) && (y <= maxY) && (z >= rangeMinZ) && (z <= rangeMaxZ);
    }

    static boolean isWithinFloodFillBounds(int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ,
            int minY, int maxY) {
        return (x >= (rangeMinX - 1)) && (x <= (rangeMaxX + 1)) && (y >= minY) && (y <= maxY)
                && (z >= (rangeMinZ - 1)) && (z <= (rangeMaxZ + 1));
    }

    static void seedQueue(Set<String> visited, ArrayDeque<FloodFillNode> queue, int x, int y, int z, int rangeMinX,
            int rangeMaxX, int rangeMinZ, int rangeMaxZ, int minY, int maxY, QueueDecision queueDecision)
            throws java.io.IOException {
        if (!isWithinFloodFillBounds(x, y, z, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ, minY, maxY)) {
            return;
        }
        String key = blockKey(x, y, z);
        if (!visited.add(key)) {
            return;
        }
        if (queueDecision.shouldQueue(x, y, z)) {
            queue.addLast(new FloodFillNode(x, y, z));
        }
    }
}
