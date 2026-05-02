package org.dynmap.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapted from jetp250's 2D greedy meshing gist:
 * https://gist.github.com/jetp250/9ca4d583f7f387bc4a2665e001b0c8fd
 *
 * This version emits rectangles for filled cells and meshes them tile-by-tile
 * to keep memory usage bounded for sparse, far-apart claims.
 */
public final class GreedyRectangleMesher {
    private static final int TILE_SIZE = 256;
    private static final int TILE_AREA = TILE_SIZE * TILE_SIZE;

    private GreedyRectangleMesher() {
    }

    public static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    public static List<Rectangle> mesh(Set<Long> cells) {
        if(cells.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, byte[]> tileMaps = new HashMap<>();
        for(long cell : cells) {
            int x = unpackX(cell);
            int y = unpackY(cell);
            int tileX = Math.floorDiv(x, TILE_SIZE);
            int tileY = Math.floorDiv(y, TILE_SIZE);
            int localX = Math.floorMod(x, TILE_SIZE);
            int localY = Math.floorMod(y, TILE_SIZE);

            long tileKey = pack(tileX, tileY);
            byte[] map = tileMaps.get(tileKey);
            if(map == null) {
                map = new byte[TILE_AREA];
                tileMaps.put(tileKey, map);
            }

            map[localX + localY * TILE_SIZE] = 1;
        }

        List<Rectangle> rectangles = new ArrayList<>();
        short[] skipMap = new short[TILE_AREA];
        for(Map.Entry<Long, byte[]> entry : tileMaps.entrySet()) {
            int tileX = unpackX(entry.getKey());
            int tileY = unpackY(entry.getKey());
            rectangles.addAll(greedyMesh(entry.getValue(), skipMap, tileX * TILE_SIZE, tileY * TILE_SIZE));
        }
        return rectangles;
    }

    private static List<Rectangle> greedyMesh(byte[] map, short[] skipMap, int xOffset, int yOffset) {
        List<Rectangle> rectangles = new ArrayList<>();
        Arrays.fill(skipMap, (short) 0);

        for(int x1 = 0; x1 < TILE_SIZE; ++x1) {
            int y1 = 0;
            int y2 = 0;
            while(y2 < TILE_SIZE) {
                int idx = x1 + y2 * TILE_SIZE;
                int skip = skipMap[idx];
                if(skip == 0 && map[idx] != 0) {
                    y2 += 1;
                    continue;
                }

                if(y1 == y2) {
                    int advance = Math.max(skip, 1);
                    y1 += advance;
                    y2 += advance;
                    continue;
                }

                emitRectangle(map, skipMap, rectangles, x1, y1, y2, xOffset, yOffset);

                y2 += Math.max(skip, 1);
                y1 = y2;
            }

            if(y1 != y2) {
                emitRectangle(map, skipMap, rectangles, x1, y1, y2, xOffset, yOffset);
            }
        }

        return rectangles;
    }

    private static void emitRectangle(byte[] map, short[] skipMap, List<Rectangle> rectangles,
                                      int x1, int y1, int y2, int xOffset, int yOffset) {
        int x2 = findRectangleX2(map, x1, y1, y2);
        rectangles.add(new Rectangle(x1 + xOffset, y1 + yOffset, x2 + xOffset, y2 + yOffset));

        for(int x = x1 + 1; x < x2; ++x) {
            skipMap[x + y1 * TILE_SIZE] = (short) (y2 - y1);
        }
    }

    private static int findRectangleX2(byte[] map, int x1, int y1, int y2) {
        for(int x2 = x1 + 1; x2 < TILE_SIZE; ++x2) {
            for(int y = y1; y < y2; ++y) {
                if(map[x2 + y * TILE_SIZE] == 0) {
                    return x2;
                }
            }
        }

        return TILE_SIZE;
    }

    private static int unpackX(long cell) {
        return (int) (cell >> 32);
    }

    private static int unpackY(long cell) {
        return (int) cell;
    }

    public static final class Rectangle {
        public final int x1;
        public final int y1;
        public final int x2;
        public final int y2;

        public Rectangle(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }
}
