package org.dynmap.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * Mutable collection of textured triangles in model-space coordinates.
 *
 * Coordinates are measured in block units relative to this mesh's model
 * origin and may extend beyond one block. The mesh accepts triangles only;
 * callers must triangulate other face types before adding them.
 *
 * Transformations are applied in call order around the model origin and
 * mutate this mesh without partitioning its geometry. Use {@link #copy()} or
 * {@link #clone()} to save an independent transformed state. Only export
 * methods split geometry into block-relative {@code RenderPatch} arrays.
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
        return rotateX(getSine(degrees), getCosine(degrees));
    }

    /**
     * Rotate every vertex around the Y axis through the model origin.
     *
     * @param degrees rotation in degrees
     * @return this mesh
     */
    public TriangleMesh rotateY(double degrees) {
        return rotateY(getSine(degrees), getCosine(degrees));
    }

    /**
     * Rotate every vertex around the Z axis through the model origin.
     *
     * @param degrees rotation in degrees
     * @return this mesh
     */
    public TriangleMesh rotateZ(double degrees) {
        return rotateZ(getSine(degrees), getCosine(degrees));
    }

    /**
     * Convert this mesh into block-relative render patches.
     *
     * This is the only mesh operation that partitions triangles into
     * block-sized pieces. The mesh itself is not modified.
     *
     * @param patchFactory factory used to create render patches
     * @param autoTextureCoordinates true to derive texture coordinates from
     *        each generated patch's block-local position; false to preserve
     *        interpolated {@link MeshVertex} texture coordinates
     * @return patches grouped by their block offset
     */
    public Map<BlockOffset, RenderPatch[]> exportFullModelToRenderPatches(RenderPatchFactory patchFactory,
            boolean autoTextureCoordinates) {
        if (patchFactory == null) {
            throw new IllegalArgumentException("Patch factory must be provided");
        }

        Map<BlockOffset, List<RenderPatch>> patchesByBlock = new LinkedHashMap<BlockOffset, List<RenderPatch>>();
        for (MeshTriangle triangle : triangles) {
            exportTriangle(triangle, patchFactory, autoTextureCoordinates, patchesByBlock);
        }

        Map<BlockOffset, RenderPatch[]> result = new LinkedHashMap<BlockOffset, RenderPatch[]>();
        for (Map.Entry<BlockOffset, List<RenderPatch>> entry : patchesByBlock.entrySet()) {
            List<RenderPatch> patches = entry.getValue();
            result.put(entry.getKey(), patches.toArray(new RenderPatch[patches.size()]));
        }
        return result;
    }

    /**
     * Convert only the part of this mesh contained by one block into render
     * patches.
     *
     * The mesh itself is not modified. This method avoids splitting or
     * creating patches for blocks other than the requested offset.
     *
     * @param block block offset to export
     * @param patchFactory factory used to create render patches
     * @param autoTextureCoordinates true to derive texture coordinates from
     *        each generated patch's block-local position; false to preserve
     *        interpolated {@link MeshVertex} texture coordinates
     * @return block-relative patches, or an empty array when the mesh does not
     *         intersect the requested block
     */
    public RenderPatch[] exportSingleBlockSpaceToRenderPatches(BlockOffset block, RenderPatchFactory patchFactory,
            boolean autoTextureCoordinates) {
        if (block == null) {
            throw new IllegalArgumentException("Block offset must be provided");
        }
        if (patchFactory == null) {
            throw new IllegalArgumentException("Patch factory must be provided");
        }

        Map<BlockOffset, List<RenderPatch>> patchesByBlock = new LinkedHashMap<BlockOffset, List<RenderPatch>>();
        for (MeshTriangle triangle : triangles) {
            exportTriangleToBlock(triangle, block, patchFactory, autoTextureCoordinates, patchesByBlock);
        }
        List<RenderPatch> patches = patchesByBlock.get(block);
        return patches == null ? new RenderPatch[0] : patches.toArray(new RenderPatch[patches.size()]);
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

    private static void exportTriangle(MeshTriangle triangle, RenderPatchFactory patchFactory,
            boolean autoTextureCoordinates,
            Map<BlockOffset, List<RenderPatch>> patchesByBlock) {
        MeshVertex vertex0 = triangle.getVertex0();
        MeshVertex vertex1 = triangle.getVertex1();
        MeshVertex vertex2 = triangle.getVertex2();
        if (!isFinite(vertex0) || !isFinite(vertex1) || !isFinite(vertex2)) {
            throw new IllegalArgumentException("Mesh vertices must have finite coordinates and texture values");
        }
        List<MeshVertex> vertices = new ArrayList<MeshVertex>(3);
        vertices.add(vertex0);
        vertices.add(vertex1);
        vertices.add(vertex2);
        exportPolygon(vertices, triangle.getSideVisible(), triangle.getTextureIndex(), patchFactory,
                autoTextureCoordinates, patchesByBlock);
    }

    private static void exportTriangleToBlock(MeshTriangle triangle, BlockOffset block,
            RenderPatchFactory patchFactory, boolean autoTextureCoordinates,
            Map<BlockOffset, List<RenderPatch>> patchesByBlock) {
        MeshVertex vertex0 = triangle.getVertex0();
        MeshVertex vertex1 = triangle.getVertex1();
        MeshVertex vertex2 = triangle.getVertex2();
        if (!isFinite(vertex0) || !isFinite(vertex1) || !isFinite(vertex2)) {
            throw new IllegalArgumentException("Mesh vertices must have finite coordinates and texture values");
        }
        List<MeshVertex> vertices = new ArrayList<MeshVertex>(3);
        vertices.add(vertex0);
        vertices.add(vertex1);
        vertices.add(vertex2);
        if (!intersectsBlock(vertices, block)) {
            return;
        }
        List<MeshVertex> clipped = clipToBlock(vertices, block);
        addClippedPatches(clipped, block.getX(), block.getY(), block.getZ(), triangle.getSideVisible(),
                triangle.getTextureIndex(), patchFactory, autoTextureCoordinates, patchesByBlock);
    }

    private static boolean intersectsBlock(List<MeshVertex> vertices, BlockOffset block) {
        return containsBlockCoordinate(getBlockRange(vertices, 0), block.getX())
                && containsBlockCoordinate(getBlockRange(vertices, 1), block.getY())
                && containsBlockCoordinate(getBlockRange(vertices, 2), block.getZ());
    }

    private static boolean containsBlockCoordinate(BlockRange range, int coordinate) {
        return coordinate >= range.minimum && coordinate <= range.maximum;
    }

    private static List<MeshVertex> clipToBlock(List<MeshVertex> vertices, BlockOffset block) {
        List<MeshVertex> clipped = clipAgainstPlane(vertices, 0, block.getX(), true);
        clipped = clipAgainstPlane(clipped, 0, block.getX() + 1.0, false);
        clipped = clipAgainstPlane(clipped, 1, block.getY(), true);
        clipped = clipAgainstPlane(clipped, 1, block.getY() + 1.0, false);
        clipped = clipAgainstPlane(clipped, 2, block.getZ(), true);
        return clipAgainstPlane(clipped, 2, block.getZ() + 1.0, false);
    }

    private static void exportPolygon(List<MeshVertex> vertices, SideVisible sideVisible, int textureIndex,
            RenderPatchFactory patchFactory, boolean autoTextureCoordinates,
            Map<BlockOffset, List<RenderPatch>> patchesByBlock) {
        if (!hasArea(vertices)) {
            return;
        }

        int axis = getSplitAxis(vertices);
        if (axis < 0) {
            addClippedPatches(vertices, getBlockCoordinate(vertices, 0), getBlockCoordinate(vertices, 1),
                    getBlockCoordinate(vertices, 2), sideVisible, textureIndex, patchFactory, autoTextureCoordinates,
                    patchesByBlock);
            return;
        }

        BlockRange range = getBlockRange(vertices, axis);
        double plane = range.minimum
                + ((double) range.maximum - (double) range.minimum + 1.0) / 2.0;
        exportPolygon(clipAgainstPlane(vertices, axis, plane, false), sideVisible, textureIndex, patchFactory,
                autoTextureCoordinates, patchesByBlock);
        exportPolygon(clipAgainstPlane(vertices, axis, plane, true), sideVisible, textureIndex, patchFactory,
                autoTextureCoordinates, patchesByBlock);
    }

    private static List<MeshVertex> clipAgainstPlane(List<MeshVertex> vertices, int axis, double plane,
            boolean keepGreater) {
        if (vertices.isEmpty()) {
            return vertices;
        }

        List<MeshVertex> clipped = new ArrayList<MeshVertex>(vertices.size() + 1);
        MeshVertex previous = vertices.get(vertices.size() - 1);
        boolean previousInside = isInside(previous, axis, plane, keepGreater);
        for (MeshVertex current : vertices) {
            boolean currentInside = isInside(current, axis, plane, keepGreater);
            if (currentInside != previousInside) {
                clipped.add(interpolateAtPlane(previous, current, axis, plane));
            }
            if (currentInside) {
                clipped.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return clipped;
    }

    private static boolean isInside(MeshVertex vertex, int axis, double plane, boolean keepGreater) {
        double coordinate = getCoordinate(vertex, axis);
        return keepGreater ? coordinate >= plane : coordinate <= plane;
    }

    private static MeshVertex interpolateAtPlane(MeshVertex start, MeshVertex end, int axis, double plane) {
        double startCoordinate = getCoordinate(start, axis);
        double endCoordinate = getCoordinate(end, axis);
        double position = (plane - startCoordinate) / (endCoordinate - startCoordinate);
        return interpolate(start, end, position);
    }

    private static MeshVertex interpolate(MeshVertex start, MeshVertex end, double position) {
        return new MeshVertex(
                start.getX() + (end.getX() - start.getX()) * position,
                start.getY() + (end.getY() - start.getY()) * position,
                start.getZ() + (end.getZ() - start.getZ()) * position,
                interpolateTextureCoordinate(start.getTextureU(), end.getTextureU(), position),
                interpolateTextureCoordinate(start.getTextureV(), end.getTextureV(), position));
    }

    private static double interpolateTextureCoordinate(double start, double end, double position) {
        return start * (1.0 - position) + end * position;
    }

    private static double getCoordinate(MeshVertex vertex, int axis) {
        switch (axis) {
            case 0:
                return vertex.getX();
            case 1:
                return vertex.getY();
            case 2:
                return vertex.getZ();
            default:
                throw new IllegalArgumentException("Invalid coordinate axis");
        }
    }

    private static void addClippedPatches(List<MeshVertex> vertices, int x, int y, int z, SideVisible sideVisible,
            int textureIndex, RenderPatchFactory patchFactory,
            boolean autoTextureCoordinates,
            Map<BlockOffset, List<RenderPatch>> patchesByBlock) {
        if (vertices.size() < 3) {
            return;
        }

        BlockOffset block = new BlockOffset(x, y, z);
        List<RenderPatch> patches = patchesByBlock.get(block);
        for (int vertexIndex = 1; vertexIndex < vertices.size() - 1; vertexIndex++) {
            MeshVertex vertex0 = toLocalCoordinates(vertices.get(0), x, y, z);
            MeshVertex vertex1 = toLocalCoordinates(vertices.get(vertexIndex), x, y, z);
            MeshVertex vertex2 = toLocalCoordinates(vertices.get(vertexIndex + 1), x, y, z);
            if (!hasArea(vertex0, vertex1, vertex2)) {
                continue;
            }
            if (patches == null) {
                patches = new ArrayList<RenderPatch>();
                patchesByBlock.put(block, patches);
            }
            if (autoTextureCoordinates) {
                patches.add(patchFactory.getTriangleAutoTexCoords(
                        vertex0.getX(), vertex0.getY(), vertex0.getZ(),
                        vertex1.getX(), vertex1.getY(), vertex1.getZ(),
                        vertex2.getX(), vertex2.getY(), vertex2.getZ(),
                        sideVisible, textureIndex));
            } else {
                patches.add(patchFactory.getTriangleExplTexCoords(
                        vertex0.getX(), vertex0.getY(), vertex0.getZ(), vertex0.getTextureU(), vertex0.getTextureV(),
                        vertex1.getX(), vertex1.getY(), vertex1.getZ(), vertex1.getTextureU(), vertex1.getTextureV(),
                        vertex2.getX(), vertex2.getY(), vertex2.getZ(), vertex2.getTextureU(), vertex2.getTextureV(),
                        sideVisible, textureIndex));
            }
        }
    }

    private static MeshVertex toLocalCoordinates(MeshVertex vertex, int x, int y, int z) {
        return new MeshVertex(
                vertex.getX() - x,
                vertex.getY() - y,
                vertex.getZ() - z,
                vertex.getTextureU(), vertex.getTextureV());
    }

    private static int getSplitAxis(List<MeshVertex> vertices) {
        for (int axis = 0; axis < 3; axis++) {
            BlockRange range = getBlockRange(vertices, axis);
            if (range.minimum != range.maximum) {
                return axis;
            }
        }
        return -1;
    }

    private static int getBlockCoordinate(List<MeshVertex> vertices, int axis) {
        return getBlockRange(vertices, axis).minimum;
    }

    private static BlockRange getBlockRange(List<MeshVertex> vertices, int axis) {
        double minimum = getCoordinate(vertices.get(0), axis);
        double maximum = minimum;
        for (int index = 1; index < vertices.size(); index++) {
            double coordinate = getCoordinate(vertices.get(index), axis);
            minimum = Math.min(minimum, coordinate);
            maximum = Math.max(maximum, coordinate);
        }
        if (minimum == maximum) {
            return new BlockRange(getLowerBlockCoordinate(minimum), getLowerBlockCoordinate(minimum));
        }
        return new BlockRange(getBlockCoordinate(minimum), getLowerBlockCoordinate(maximum));
    }

    private static int getBlockCoordinate(double coordinate) {
        return checkedBlockCoordinate(Math.floor(coordinate));
    }

    private static int getLowerBlockCoordinate(double coordinate) {
        return checkedBlockCoordinate(Math.floor(Math.nextAfter(coordinate, Double.NEGATIVE_INFINITY)));
    }

    private static int checkedBlockCoordinate(double coordinate) {
        if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Mesh coordinate is outside the supported block range");
        }
        return (int) coordinate;
    }

    private static boolean hasArea(MeshVertex vertex0, MeshVertex vertex1, MeshVertex vertex2) {
        double edge1X = vertex1.getX() - vertex0.getX();
        double edge1Y = vertex1.getY() - vertex0.getY();
        double edge1Z = vertex1.getZ() - vertex0.getZ();
        double edge2X = vertex2.getX() - vertex0.getX();
        double edge2Y = vertex2.getY() - vertex0.getY();
        double edge2Z = vertex2.getZ() - vertex0.getZ();
        double edge1Scale = Math.max(Math.abs(edge1X), Math.max(Math.abs(edge1Y), Math.abs(edge1Z)));
        double edge2Scale = Math.max(Math.abs(edge2X), Math.max(Math.abs(edge2Y), Math.abs(edge2Z)));
        if (edge1Scale == 0.0 || edge2Scale == 0.0) {
            return false;
        }
        edge1X /= edge1Scale;
        edge1Y /= edge1Scale;
        edge1Z /= edge1Scale;
        edge2X /= edge2Scale;
        edge2Y /= edge2Scale;
        edge2Z /= edge2Scale;
        double normalX = edge1Y * edge2Z - edge1Z * edge2Y;
        double normalY = edge1Z * edge2X - edge1X * edge2Z;
        double normalZ = edge1X * edge2Y - edge1Y * edge2X;
        return normalX != 0.0 || normalY != 0.0 || normalZ != 0.0;
    }

    private static boolean hasArea(List<MeshVertex> vertices) {
        for (int vertexIndex = 1; vertexIndex < vertices.size() - 1; vertexIndex++) {
            if (hasArea(vertices.get(0), vertices.get(vertexIndex), vertices.get(vertexIndex + 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFinite(MeshVertex vertex) {
        return isFinite(vertex.getX()) && isFinite(vertex.getY()) && isFinite(vertex.getZ())
                && isFinite(vertex.getTextureU()) && isFinite(vertex.getTextureV());
    }

    private static boolean isFinite(double value) {
        return !Double.isInfinite(value) && !Double.isNaN(value);
    }

    private static double getSine(double degrees) {
        double normalized = degrees % 360.0;
        if (normalized == 0.0 || normalized == -0.0) {
            return 0.0;
        }
        if (normalized == 90.0 || normalized == -270.0) {
            return 1.0;
        }
        if (normalized == -90.0 || normalized == 270.0) {
            return -1.0;
        }
        if (normalized == 180.0 || normalized == -180.0) {
            return 0.0;
        }
        return Math.sin(Math.toRadians(degrees));
    }

    private static double getCosine(double degrees) {
        double normalized = degrees % 360.0;
        if (normalized == 0.0 || normalized == -0.0) {
            return 1.0;
        }
        if (normalized == 90.0 || normalized == -90.0 || normalized == 270.0 || normalized == -270.0) {
            return 0.0;
        }
        if (normalized == 180.0 || normalized == -180.0) {
            return -1.0;
        }
        return Math.cos(Math.toRadians(degrees));
    }

    private static final class BlockRange {
        private final int minimum;
        private final int maximum;

        private BlockRange(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
