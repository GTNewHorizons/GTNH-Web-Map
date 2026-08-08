package org.dynmap.utils;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * A textured triangle in a {@link TriangleMesh}.
 */
public final class MeshTriangle {
    private final MeshVertex vertex0;
    private final MeshVertex vertex1;
    private final MeshVertex vertex2;
    private final SideVisible sideVisible;
    private final int textureIndex;

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

    void rotateY(double sine, double cosine) {
        vertex0.rotateY(sine, cosine);
        vertex1.rotateY(sine, cosine);
        vertex2.rotateY(sine, cosine);
    }

    void rotateZ(double sine, double cosine) {
        vertex0.rotateZ(sine, cosine);
        vertex1.rotateZ(sine, cosine);
        vertex2.rotateZ(sine, cosine);
    }
}
