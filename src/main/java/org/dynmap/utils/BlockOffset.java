package org.dynmap.utils;

/**
 * Integer block-space offset from a {@link TriangleMesh} model origin.
 *
 * Offsets key the block-relative patch arrays produced by mesh export.
 * They do not represent world coordinates until the caller places the model
 * at a world position.
 */
public final class BlockOffset {
    private final int x;
    private final int y;
    private final int z;

    /**
     * Create an offset in block units.
     *
     * @param x X-axis block offset
     * @param y Y-axis block offset
     * @param z Z-axis block offset
     */
    public BlockOffset(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BlockOffset)) {
            return false;
        }
        BlockOffset other = (BlockOffset) obj;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }
}
