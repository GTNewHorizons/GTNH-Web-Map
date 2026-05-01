package org.dynmap.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Adapted from jetp250's 2D greedy meshing gist:
 * https://gist.github.com/jetp250/9ca4d583f7f387bc4a2665e001b0c8fd
 *
 * This version emits rectangles for filled cells in an arbitrary bounding box,
 * rather than for empty cells in a fixed-size chunk-local grid.
 */
public final class GreedyRectangleMesher {
    private GreedyRectangleMesher() {
    }

    public static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    public static List<Rectangle> mesh(Set<Long> cells) {
        if(cells.isEmpty()) {
            return Collections.emptyList();
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for(long cell : cells) {
            int x = unpackX(cell);
            int y = unpackY(cell);
            if(x < minX) minX = x;
            if(y < minY) minY = y;
            if(x > maxX) maxX = x;
            if(y > maxY) maxY = y;
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        byte[] map = new byte[width * height];

        for(long cell : cells) {
            int x = unpackX(cell) - minX;
            int y = unpackY(cell) - minY;
            map[x + y * width] = 1;
        }

        List<Rectangle> rectangles = greedyMesh(map, width, height);
        for(int i = 0; i < rectangles.size(); i++) {
            Rectangle rectangle = rectangles.get(i);
            rectangles.set(i, new Rectangle(
                    rectangle.x1 + minX,
                    rectangle.y1 + minY,
                    rectangle.x2 + minX,
                    rectangle.y2 + minY));
        }

        return rectangles;
    }

    private static List<Rectangle> greedyMesh(byte[] map, int width, int height) {
        List<Rectangle> rectangles = new ArrayList<>();
        int[] skipMap = new int[width * height];

        for(int x1 = 0; x1 < width; ++x1) {
            int y1 = 0;
            int y2 = 0;
            while(y2 < height) {
                int idx = x1 + y2 * width;
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

                int x2 = findRectangleX2(map, width, height, x1, y1, y2);
                rectangles.add(new Rectangle(x1, y1, x2, y2));

                for(int x = x1 + 1; x < x2; ++x) {
                    skipMap[x + y1 * width] = y2 - y1;
                }

                y2 += Math.max(skip, 1);
                y1 = y2;
            }

            if(y1 != y2) {
                int x2 = findRectangleX2(map, width, height, x1, y1, y2);
                rectangles.add(new Rectangle(x1, y1, x2, y2));

                for(int x = x1 + 1; x < x2; ++x) {
                    skipMap[x + y1 * width] = y2 - y1;
                }
            }
        }

        return rectangles;
    }

    private static int findRectangleX2(byte[] map, int width, int height, int x1, int y1, int y2) {
        for(int x2 = x1 + 1; x2 < width; ++x2) {
            for(int y = y1; y < y2; ++y) {
                if(map[x2 + y * width] == 0) {
                    return x2;
                }
            }
        }

        return width;
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
