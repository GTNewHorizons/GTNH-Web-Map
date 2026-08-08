package org.dynmap.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable collection of textured triangles in model-space coordinates.
 *
 * Transformations are applied in call order and do not partition geometry
 * into blocks. Block-relative {@code PatchDefinition} export is performed
 * separately.
 */
public final class TriangleMesh {
    private final List<MeshTriangle> triangles = new ArrayList<MeshTriangle>();

    /**
     * Add a copy of a triangle to this mesh.
     *
     * @param triangle triangle to add
     * @return this mesh
     */
    public TriangleMesh addTriangle(MeshTriangle triangle) {
        if (triangle == null) {
            throw new IllegalArgumentException("Triangle must be provided");
        }
        triangles.add(new MeshTriangle(triangle));
        return this;
    }

    /**
     * Get the mesh triangles. Their vertex accessors return copies.
     *
     * @return read-only triangle list
     */
    public List<MeshTriangle> getTriangles() {
        return Collections.unmodifiableList(triangles);
    }

    /**
     * Translate every vertex in this mesh.
     *
     * @return this mesh
     */
    public TriangleMesh translate(double x, double y, double z) {
        for (MeshTriangle triangle : triangles) {
            triangle.translate(x, y, z);
        }
        return this;
    }

    /**
     * Scale every vertex around the model origin.
     *
     * @return this mesh
     */
    public TriangleMesh scale(double x, double y, double z) {
        for (MeshTriangle triangle : triangles) {
            triangle.scale(x, y, z);
        }
        return this;
    }

    /**
     * Rotate every vertex around the X axis through the model origin.
     *
     * @param degrees rotation in degrees
     * @return this mesh
     */
    public TriangleMesh rotateX(double degrees) {
        double radians = Math.toRadians(degrees);
        return rotateX(Math.sin(radians), Math.cos(radians));
    }

    /**
     * Rotate every vertex around the Y axis through the model origin.
     *
     * @param degrees rotation in degrees
     * @return this mesh
     */
    public TriangleMesh rotateY(double degrees) {
        double radians = Math.toRadians(degrees);
        return rotateY(Math.sin(radians), Math.cos(radians));
    }

    /**
     * Rotate every vertex around the Z axis through the model origin.
     *
     * @param degrees rotation in degrees
     * @return this mesh
     */
    public TriangleMesh rotateZ(double degrees) {
        double radians = Math.toRadians(degrees);
        return rotateZ(Math.sin(radians), Math.cos(radians));
    }

    /**
     * Create an independent snapshot of this mesh's current state.
     *
     * @return deep copy of this mesh
     */
    public TriangleMesh copy() {
        TriangleMesh copy = new TriangleMesh();
        for (MeshTriangle triangle : triangles) {
            copy.triangles.add(new MeshTriangle(triangle));
        }
        return copy;
    }

    @Override
    public TriangleMesh clone() {
        return copy();
    }

    private TriangleMesh rotateX(double sine, double cosine) {
        for (MeshTriangle triangle : triangles) {
            triangle.rotateX(sine, cosine);
        }
        return this;
    }

    private TriangleMesh rotateY(double sine, double cosine) {
        for (MeshTriangle triangle : triangles) {
            triangle.rotateY(sine, cosine);
        }
        return this;
    }

    private TriangleMesh rotateZ(double sine, double cosine) {
        for (MeshTriangle triangle : triangles) {
            triangle.rotateZ(sine, cosine);
        }
        return this;
    }
}
