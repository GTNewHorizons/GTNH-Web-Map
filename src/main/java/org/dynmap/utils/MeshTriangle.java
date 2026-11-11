package org.dynmap.utils;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * A textured triangle in a {@link TriangleMesh}.
 *
 * The vertex order determines winding and therefore the front-facing side
 * for {@link SideVisible}. The constructor copies its vertices, so later
 * changes to source objects cannot alter this triangle.
 */
public final class MeshTriangle {
    private final MeshVertex vertex0;
    private final MeshVertex vertex1;
    private final MeshVertex vertex2;
    private final SideVisible sideVisible;
    private final int textureIndex;

    /**
     * Create a triangle face.
     *
     * @param vertex0 first vertex in winding order
     * @param vertex1 second vertex in winding order
     * @param vertex2 third vertex in winding order
     * @param sideVisible face visibility
     * @param textureIndex texture index passed to the render patch factory
     */
    public MeshTriangle(MeshVertex vertex0, MeshVertex vertex1, MeshVertex vertex2, SideVisible sideVisible, int textureIndex) {
        if (vertex0 == null || vertex1 == null || vertex2 == null || sideVisible == null) {
            throw new IllegalArgumentException("Triangle vertices and visibility must be provided");
        }
        this.vertex0 = new MeshVertex(vertex0);
        this.vertex1 = new MeshVertex(vertex1);
        this.vertex2 = new MeshVertex(vertex2);
        this.sideVisible = sideVisible;
        this.textureIndex = textureIndex;
    }

    public MeshTriangle(MeshTriangle other) {
        this(other.vertex0, other.vertex1, other.vertex2, other.sideVisible, other.textureIndex);
    }

    public MeshVertex getVertex0() {
        return new MeshVertex(vertex0);
    }

    public MeshVertex getVertex1() {
        return new MeshVertex(vertex1);
    }

    public MeshVertex getVertex2() {
        return new MeshVertex(vertex2);
    }

    public SideVisible getSideVisible() {
        return sideVisible;
    }

    public int getTextureIndex() {
        return textureIndex;
    }

    void translate(double x, double y, double z) {
        vertex0.translate(x, y, z);
        vertex1.translate(x, y, z);
        vertex2.translate(x, y, z);
    }

    void scale(double x, double y, double z) {
        vertex0.scale(x, y, z);
        vertex1.scale(x, y, z);
        vertex2.scale(x, y, z);
    }

    void rotateX(double sine, double cosine) {
        vertex0.rotateX(sine, cosine);
        vertex1.rotateX(sine, cosine);
        vertex2.rotateX(sine, cosine);
    }

    void rotateX90() {
        vertex0.rotateX90();
        vertex1.rotateX90();
        vertex2.rotateX90();
    }

    void rotateX180() {
        vertex0.rotateX180();
        vertex1.rotateX180();
        vertex2.rotateX180();
    }

    void rotateX270() {
        vertex0.rotateX270();
        vertex1.rotateX270();
        vertex2.rotateX270();
    }

    void rotateY(double sine, double cosine) {
        vertex0.rotateY(sine, cosine);
        vertex1.rotateY(sine, cosine);
        vertex2.rotateY(sine, cosine);
    }

    void rotateY90() {
        vertex0.rotateY90();
        vertex1.rotateY90();
        vertex2.rotateY90();
    }

    void rotateY180() {
        vertex0.rotateY180();
        vertex1.rotateY180();
        vertex2.rotateY180();
    }

    void rotateY270() {
        vertex0.rotateY270();
        vertex1.rotateY270();
        vertex2.rotateY270();
    }

    void rotateZ(double sine, double cosine) {
        vertex0.rotateZ(sine, cosine);
        vertex1.rotateZ(sine, cosine);
        vertex2.rotateZ(sine, cosine);
    }

    void rotateZ90() {
        vertex0.rotateZ90();
        vertex1.rotateZ90();
        vertex2.rotateZ90();
    }

    void rotateZ180() {
        vertex0.rotateZ180();
        vertex1.rotateZ180();
        vertex2.rotateZ180();
    }

    void rotateZ270() {
        vertex0.rotateZ270();
        vertex1.rotateZ270();
        vertex2.rotateZ270();
    }
}
