package org.dynmap.utils;

/**
 * A mesh vertex in model-space coordinates with texture coordinates.
 *
 * Position values are measured in block units relative to the containing
 * {@link TriangleMesh} model origin. Texture values are retained when export
 * uses explicit texture coordinates.
 */
public final class MeshVertex {
    private double x;
    private double y;
    private double z;
    private final double textureU;
    private final double textureV;

    /**
     * Create a model-space vertex.
     *
     * @param x X-axis position in block units
     * @param y Y-axis position in block units
     * @param z Z-axis position in block units
     * @param textureU texture U coordinate
     * @param textureV texture V coordinate
     */
    public MeshVertex(double x, double y, double z, double textureU, double textureV) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.textureU = textureU;
        this.textureV = textureV;
    }

    public MeshVertex(MeshVertex other) {
        this(other.x, other.y, other.z, other.textureU, other.textureV);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getTextureU() {
        return textureU;
    }

    public double getTextureV() {
        return textureV;
    }

    void translate(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    void scale(double x, double y, double z) {
        this.x *= x;
        this.y *= y;
        this.z *= z;
    }

    void rotateX(double sine, double cosine) {
        double newY = z * sine + y * cosine;
        z = z * cosine - y * sine;
        y = newY;
    }

    void rotateY(double sine, double cosine) {
        double newX = x * cosine - z * sine;
        z = x * sine + z * cosine;
        x = newX;
    }

    void rotateZ(double sine, double cosine) {
        double newX = y * sine + x * cosine;
        y = y * cosine - x * sine;
        x = newX;
    }
}
