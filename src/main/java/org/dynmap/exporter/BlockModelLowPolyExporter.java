package org.dynmap.exporter;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.dynmap.DynmapWorld;
import org.dynmap.DynmapCore;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.hdmap.TexturePack.ExportedTextureData;
import org.dynmap.hdmap.TexturePackHDShader;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

final class BlockModelLowPolyExporter extends AbstractBlockModelExporter {
    private static final double EPSILON = 1.0E-6;
    private static final int WEST = 0;
    private static final int EAST = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final BlockStep[] SIDE_STEPS =
            { BlockStep.X_MINUS, BlockStep.X_PLUS, BlockStep.Z_MINUS, BlockStep.Z_PLUS };
    private static final int MAX_LOW_POLY_GROUP_SPAN = 8;
    private static final double LOW_POLY_TIGHTEN_OVERFILL_RATIO = 1.25;
    private static final double HULL_EPSILON = 1.0E-5;

    private static final class SurfaceCell {
        int minX;
        int minZ;
        int width;
        int depth;
        int lightX;
        int lightZ;
        int lightBlockY;
        int lightBlockId;
        double topY;
        ExportMaterial topMaterial;
        final ExportMaterial[] sideMaterials = new ExportMaterial[4];
        boolean valid;
    }

    private static final class FloodFillNode {
        final int x;
        final int y;
        final int z;

        FloodFillNode(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class LowPolyBlockInfo {
        final int x;
        final int y;
        final int z;
        final int blockId;
        final String groupKey;
        final ExportMaterial topMaterial;
        final ExportMaterial westMaterial;
        final ExportMaterial eastMaterial;
        final ExportMaterial northMaterial;
        final ExportMaterial southMaterial;

        LowPolyBlockInfo(int x, int y, int z, int blockId, String groupKey, ExportMaterial topMaterial,
                ExportMaterial westMaterial, ExportMaterial eastMaterial, ExportMaterial northMaterial,
                ExportMaterial southMaterial) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.groupKey = groupKey;
            this.topMaterial = topMaterial;
            this.westMaterial = westMaterial;
            this.eastMaterial = eastMaterial;
            this.northMaterial = northMaterial;
            this.southMaterial = southMaterial;
        }
    }

    private static final class LowPolyGroup {
        final LowPolyBlockInfo seed;
        final ArrayList<LowPolyBlockInfo> members = new ArrayList<LowPolyBlockInfo>();
        int minX;
        int maxX;
        int minY;
        int maxY;
        int minZ;
        int maxZ;

        LowPolyGroup(LowPolyBlockInfo seed) {
            this.seed = seed;
            this.minX = seed.x;
            this.maxX = seed.x;
            this.minY = seed.y;
            this.maxY = seed.y;
            this.minZ = seed.z;
            this.maxZ = seed.z;
        }
    }

    private static final class HullPlane {
        final double nx;
        final double ny;
        final double nz;
        final double d;
        final ExportMaterial material;
        final boolean emit;

        HullPlane(double nx, double ny, double nz, double d, ExportMaterial material, boolean emit) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.d = d;
            this.material = material;
            this.emit = emit;
        }
    }

    private static final class HullFaceCandidate {
        final HullPlane plane;
        final List<double[]> face;

        HullFaceCandidate(HullPlane plane, List<double[]> face) {
            this.plane = plane;
            this.face = face;
        }
    }

    private final PatchDefinitionFactory patchFactory;
    private final TexturePack exportTexturePack;
    private final Map<String, ExportMaterial> solidColorMaterialCache = new HashMap<String, ExportMaterial>();
    private final Map<String, Integer> averageColorCache = new HashMap<String, Integer>();

    BlockModelLowPolyExporter(DynmapWorld world, DynmapCore core, HDShader shader) {
        super(world, core, shader);
        this.patchFactory = HDBlockModels.getPatchDefinitionFactory();
        this.exportTexturePack =
                (shader instanceof TexturePackHDShader) ? ((TexturePackHDShader) shader).getTexturePackForExport() : null;
    }

    @Override
    protected void exportLoadedRegion(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ, boolean[] edgeBits) throws IOException {
        MapIterator geometryIterator = cache.getIterator(rangeMinX, getMaxY(), rangeMinZ);
        Map<String, LowPolyBlockInfo> infoCache = new HashMap<String, LowPolyBlockInfo>();
        ArrayList<LowPolyBlockInfo> visibleBlocks =
                collectLowPolyVisibleBlocks(geometryIterator, infoCache, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
        if (visibleBlocks.isEmpty()) {
            return;
        }
        MapIterator groupIterator = cache.getIterator(rangeMinX, getMaxY(), rangeMinZ);
        MapIterator lightingIterator = cache.getIterator(rangeMinX, getMinY(), rangeMinZ);
        Set<String> grouped = new HashSet<String>();
        for (LowPolyBlockInfo seed : visibleBlocks) {
            String seedKey = blockKey(seed.x, seed.y, seed.z);
            if (grouped.contains(seedKey)) {
                continue;
            }
            LowPolyGroup group = growLowPolyGroup(groupIterator, infoCache, grouped, seed, rangeMinX, rangeMaxX, rangeMinZ,
                    rangeMaxZ);
            emitLowPolyGroup(sink, lightingIterator, group);
        }
    }

    private ArrayList<LowPolyBlockInfo> collectLowPolyVisibleBlocks(MapIterator geometryIterator,
            Map<String, LowPolyBlockInfo> infoCache, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ)
            throws IOException {
        Set<String> visited = new HashSet<String>();
        Set<String> visible = new HashSet<String>();
        ArrayDeque<FloodFillNode> queue = new ArrayDeque<FloodFillNode>();
        ArrayList<LowPolyBlockInfo> visibleBlocks = new ArrayList<LowPolyBlockInfo>();

        seedFloodFillQueue(geometryIterator, visited, queue, rangeMinX - 1, getMaxY(), rangeMinZ - 1, rangeMinX, rangeMaxX,
                rangeMinZ, rangeMaxZ);

        for (int z = rangeMinZ; z <= rangeMaxZ; z++) {
            for (int x = rangeMinX; x <= rangeMaxX; x++) {
                String key = blockKey(x, getMaxY(), z);
                if (!visited.add(key)) {
                    continue;
                }
                processLowPolyFloodFillPosition(geometryIterator, infoCache, visible, visibleBlocks, queue, x, getMaxY(),
                        z, true);
            }
        }

        while (!queue.isEmpty()) {
            FloodFillNode current = queue.removeFirst();
            for (int[] direction : BlockModelFloodFillSupport.DIRECTIONS) {
                int neighborX = current.x + direction[0];
                int neighborY = current.y + direction[1];
                int neighborZ = current.z + direction[2];
                if (!isWithinFloodFillBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ)) {
                    continue;
                }
                String key = blockKey(neighborX, neighborY, neighborZ);
                if (!visited.add(key)) {
                    continue;
                }
                processLowPolyFloodFillPosition(geometryIterator, infoCache, visible, visibleBlocks, queue, neighborX,
                        neighborY, neighborZ,
                        isWithinRenderBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ));
            }
        }

        return visibleBlocks;
    }

    private void processLowPolyFloodFillPosition(MapIterator geometryIterator, Map<String, LowPolyBlockInfo> infoCache,
            Set<String> visible, ArrayList<LowPolyBlockInfo> visibleBlocks, ArrayDeque<FloodFillNode> queue, int x, int y,
            int z, boolean render) throws IOException {
        geometryIterator.initialize(x, y, z);
        int blockId = geometryIterator.getBlockTypeID();
        boolean skylightAllowed = hasSufficientSkyLight(geometryIterator);
        if (render) {
            LowPolyBlockInfo info = getLowPolyBlockInfo(geometryIterator, infoCache, x, y, z);
            if ((info != null) && visible.add(blockKey(x, y, z))) {
                visibleBlocks.add(info);
            }
        }
        if (skylightAllowed && canQueueLowPolyBlock(blockId)) {
            queue.addLast(new FloodFillNode(x, y, z));
        }
    }

    private boolean canQueueLowPolyBlock(int blockId) {
        return !isSolidTraceBlock(blockId);
    }

    private boolean hasSufficientSkyLight(MapIterator geometryIterator) {
        if (!getWorld().canGetSkyLightLevel()) {
            return true;
        }
        return geometryIterator.getBlockSkyLight() >= getSimplifiedMinSkyLight();
    }

    private LowPolyGroup growLowPolyGroup(MapIterator geometryIterator, Map<String, LowPolyBlockInfo> infoCache,
            Set<String> grouped, LowPolyBlockInfo seed, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ)
            throws IOException {
        LowPolyGroup group = new LowPolyGroup(seed);
        ArrayDeque<FloodFillNode> queue = new ArrayDeque<FloodFillNode>();
        Set<String> queued = new HashSet<String>();
        queue.addLast(new FloodFillNode(seed.x, seed.y, seed.z));
        queued.add(blockKey(seed.x, seed.y, seed.z));

        while (!queue.isEmpty()) {
            FloodFillNode current = queue.removeFirst();
            String currentKey = blockKey(current.x, current.y, current.z);
            if (grouped.contains(currentKey)) {
                continue;
            }
            LowPolyBlockInfo info = getLowPolyBlockInfo(geometryIterator, infoCache, current.x, current.y, current.z);
            if ((info == null) || !seed.groupKey.equals(info.groupKey) || !canExpandLowPolyGroup(group, info.x, info.y, info.z)) {
                continue;
            }

            grouped.add(currentKey);
            group.members.add(info);
            updateLowPolyGroupBounds(group, info.x, info.y, info.z);

            for (int[] direction : BlockModelFloodFillSupport.DIRECTIONS) {
                int neighborX = current.x + direction[0];
                int neighborY = current.y + direction[1];
                int neighborZ = current.z + direction[2];
                if (!isWithinRenderBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ)) {
                    continue;
                }
                String neighborKey = blockKey(neighborX, neighborY, neighborZ);
                if (!grouped.contains(neighborKey) && queued.add(neighborKey)) {
                    queue.addLast(new FloodFillNode(neighborX, neighborY, neighborZ));
                }
            }
        }

        return group;
    }

    private boolean canExpandLowPolyGroup(LowPolyGroup group, int x, int y, int z) {
        int minX = Math.min(group.minX, x);
        int maxX = Math.max(group.maxX, x);
        int minY = Math.min(group.minY, y);
        int maxY = Math.max(group.maxY, y);
        int minZ = Math.min(group.minZ, z);
        int maxZ = Math.max(group.maxZ, z);
        return ((maxX - minX) < MAX_LOW_POLY_GROUP_SPAN) && ((maxY - minY) < MAX_LOW_POLY_GROUP_SPAN)
                && ((maxZ - minZ) < MAX_LOW_POLY_GROUP_SPAN);
    }

    private void updateLowPolyGroupBounds(LowPolyGroup group, int x, int y, int z) {
        group.minX = Math.min(group.minX, x);
        group.maxX = Math.max(group.maxX, x);
        group.minY = Math.min(group.minY, y);
        group.maxY = Math.max(group.maxY, y);
        group.minZ = Math.min(group.minZ, z);
        group.maxZ = Math.max(group.maxZ, z);
    }

    private List<HullPlane> buildLowPolyHullPlanes(LowPolyGroup group) {
        ArrayList<HullPlane> planes = new ArrayList<HullPlane>();
        int sizeX = (group.maxX - group.minX) + 1;
        int sizeY = (group.maxY - group.minY) + 1;
        int sizeZ = (group.maxZ - group.minZ) + 1;

        planes.add(new HullPlane(1.0, 0.0, 0.0, 0.0, group.seed.westMaterial, true));
        planes.add(new HullPlane(-1.0, 0.0, 0.0, -sizeX, group.seed.eastMaterial, true));
        planes.add(new HullPlane(0.0, 1.0, 0.0, 0.0, null, false));
        planes.add(new HullPlane(0.0, -1.0, 0.0, -sizeY, group.seed.topMaterial, true));
        planes.add(new HullPlane(0.0, 0.0, 1.0, 0.0, group.seed.northMaterial, true));
        planes.add(new HullPlane(0.0, 0.0, -1.0, -sizeZ, group.seed.southMaterial, true));

        for (int sx = 0; sx < 2; sx++) {
            for (int sy = 0; sy < 2; sy++) {
                int threshold = getLowPolyEdgeThreshold(group, sizeX, sizeY, sizeZ, 0, sx == 0, 1, sy == 0);
                if (threshold > 0) {
                    planes.add(buildHullPlane(sizeX, sizeY, sizeZ, true, sx == 0, true, sy == 0, false, true, threshold,
                            chooseLowPolyCutMaterial(group, true, sx == 0, true, sy == 0, false, true)));
                }
            }
        }
        for (int sx = 0; sx < 2; sx++) {
            for (int sz = 0; sz < 2; sz++) {
                int threshold = getLowPolyEdgeThreshold(group, sizeX, sizeY, sizeZ, 0, sx == 0, 2, sz == 0);
                if (threshold > 0) {
                    planes.add(buildHullPlane(sizeX, sizeY, sizeZ, true, sx == 0, false, true, true, sz == 0, threshold,
                            chooseLowPolyCutMaterial(group, true, sx == 0, false, true, true, sz == 0)));
                }
            }
        }
        for (int sy = 0; sy < 2; sy++) {
            for (int sz = 0; sz < 2; sz++) {
                int threshold = getLowPolyEdgeThreshold(group, sizeX, sizeY, sizeZ, 1, sy == 0, 2, sz == 0);
                if (threshold > 0) {
                    planes.add(buildHullPlane(sizeX, sizeY, sizeZ, false, true, true, sy == 0, true, sz == 0, threshold,
                            chooseLowPolyCutMaterial(group, false, true, true, sy == 0, true, sz == 0)));
                }
            }
        }
        for (int sx = 0; sx < 2; sx++) {
            for (int sy = 0; sy < 2; sy++) {
                for (int sz = 0; sz < 2; sz++) {
                    int threshold = getLowPolyCornerThreshold(group, sizeX, sizeY, sizeZ, sx == 0, sy == 0, sz == 0);
                    if (threshold > 0) {
                        planes.add(buildHullPlane(sizeX, sizeY, sizeZ, true, sx == 0, true, sy == 0, true, sz == 0,
                                threshold, chooseLowPolyCutMaterial(group, true, sx == 0, true, sy == 0, true, sz == 0)));
                    }
                }
            }
        }
        return planes;
    }

    private HullPlane buildHullPlane(int sizeX, int sizeY, int sizeZ, boolean useX, boolean minX, boolean useY, boolean minY,
            boolean useZ, boolean minZ, int threshold, ExportMaterial material) {
        double nx = 0.0;
        double ny = 0.0;
        double nz = 0.0;
        double constant = 0.0;
        if (useX) {
            nx = minX ? 1.0 : -1.0;
            constant += minX ? 0.0 : sizeX;
        }
        if (useY) {
            ny = minY ? 1.0 : -1.0;
            constant += minY ? 0.0 : sizeY;
        }
        if (useZ) {
            nz = minZ ? 1.0 : -1.0;
            constant += minZ ? 0.0 : sizeZ;
        }
        return new HullPlane(nx, ny, nz, threshold - constant, material, true);
    }

    private int getLowPolyEdgeThreshold(LowPolyGroup group, int sizeX, int sizeY, int sizeZ, int axisA, boolean minA,
            int axisB, boolean minB) {
        int threshold = Integer.MAX_VALUE;
        for (LowPolyBlockInfo member : group.members) {
            threshold = Math.min(threshold,
                    getLowPolyAxisDistance(group, member, sizeX, sizeY, sizeZ, axisA, minA)
                            + getLowPolyAxisDistance(group, member, sizeX, sizeY, sizeZ, axisB, minB));
        }
        return (threshold == Integer.MAX_VALUE) ? 0 : threshold;
    }

    private int getLowPolyCornerThreshold(LowPolyGroup group, int sizeX, int sizeY, int sizeZ, boolean minX,
            boolean minY, boolean minZ) {
        int threshold = Integer.MAX_VALUE;
        for (LowPolyBlockInfo member : group.members) {
            threshold = Math.min(threshold,
                    getLowPolyAxisDistance(group, member, sizeX, sizeY, sizeZ, 0, minX)
                            + getLowPolyAxisDistance(group, member, sizeX, sizeY, sizeZ, 1, minY)
                            + getLowPolyAxisDistance(group, member, sizeX, sizeY, sizeZ, 2, minZ));
        }
        return (threshold == Integer.MAX_VALUE) ? 0 : threshold;
    }

    private int getLowPolyAxisDistance(LowPolyGroup group, LowPolyBlockInfo member, int sizeX, int sizeY, int sizeZ,
            int axis, boolean minSide) {
        int local = (axis == 0) ? (member.x - group.minX) : ((axis == 1) ? (member.y - group.minY) : (member.z - group.minZ));
        int size = (axis == 0) ? sizeX : ((axis == 1) ? sizeY : sizeZ);
        return minSide ? local : (size - (local + 1));
    }

    private ExportMaterial chooseLowPolyCutMaterial(LowPolyGroup group, boolean useX, boolean minX, boolean useY,
            boolean minY, boolean useZ, boolean minZ) {
        if (group.seed.topMaterial != null) {
            return group.seed.topMaterial;
        }
        if (useX) {
            return minX ? group.seed.westMaterial : group.seed.eastMaterial;
        }
        if (useZ) {
            return minZ ? group.seed.northMaterial : group.seed.southMaterial;
        }
        return group.seed.topMaterial;
    }

    private List<double[]> computeHullVertices(List<HullPlane> planes) {
        ArrayList<double[]> vertices = new ArrayList<double[]>();
        for (int i = 0; i < planes.size(); i++) {
            for (int j = i + 1; j < planes.size(); j++) {
                for (int k = j + 1; k < planes.size(); k++) {
                    double[] point = intersectPlanes(planes.get(i), planes.get(j), planes.get(k));
                    if ((point == null) || !isInsideHull(point, planes) || containsVertex(vertices, point)) {
                        continue;
                    }
                    vertices.add(point);
                }
            }
        }
        return vertices;
    }

    private double[] intersectPlanes(HullPlane a, HullPlane b, HullPlane c) {
        double det = determinant3(a.nx, a.ny, a.nz, b.nx, b.ny, b.nz, c.nx, c.ny, c.nz);
        if (Math.abs(det) < HULL_EPSILON) {
            return null;
        }
        double x = determinant3(a.d, a.ny, a.nz, b.d, b.ny, b.nz, c.d, c.ny, c.nz) / det;
        double y = determinant3(a.nx, a.d, a.nz, b.nx, b.d, b.nz, c.nx, c.d, c.nz) / det;
        double z = determinant3(a.nx, a.ny, a.d, b.nx, b.ny, b.d, c.nx, c.ny, c.d) / det;
        return new double[] { x, y, z };
    }

    private double determinant3(double a00, double a01, double a02, double a10, double a11, double a12, double a20,
            double a21, double a22) {
        return (a00 * ((a11 * a22) - (a12 * a21))) - (a01 * ((a10 * a22) - (a12 * a20)))
                + (a02 * ((a10 * a21) - (a11 * a20)));
    }

    private boolean isInsideHull(double[] point, List<HullPlane> planes) {
        for (HullPlane plane : planes) {
            double value = (plane.nx * point[0]) + (plane.ny * point[1]) + (plane.nz * point[2]);
            if (value < (plane.d - HULL_EPSILON)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsVertex(List<double[]> vertices, double[] point) {
        for (double[] existing : vertices) {
            if ((Math.abs(existing[0] - point[0]) < HULL_EPSILON) && (Math.abs(existing[1] - point[1]) < HULL_EPSILON)
                    && (Math.abs(existing[2] - point[2]) < HULL_EPSILON)) {
                return true;
            }
        }
        return false;
    }

    private List<double[]> getPlaneFace(List<double[]> vertices, final HullPlane plane) {
        ArrayList<double[]> face = new ArrayList<double[]>();
        for (double[] point : vertices) {
            double value = (plane.nx * point[0]) + (plane.ny * point[1]) + (plane.nz * point[2]);
            if (Math.abs(value - plane.d) < HULL_EPSILON) {
                face.add(point);
            }
        }
        if (face.size() < 3) {
            return face;
        }
        final double[] center = computeCenter(face);
        final double[] outward = normalize(new double[] { -plane.nx, -plane.ny, -plane.nz });
        double[] ref = (Math.abs(outward[2]) < 0.9) ? new double[] { 0.0, 0.0, 1.0 } : new double[] { 0.0, 1.0, 0.0 };
        final double[] tangent = normalize(cross(ref, outward));
        final double[] bitangent = cross(outward, tangent);
        Collections.sort(face, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                double angleA = Math.atan2(dot(bitangent, subtract(a, center)), dot(tangent, subtract(a, center)));
                double angleB = Math.atan2(dot(bitangent, subtract(b, center)), dot(tangent, subtract(b, center)));
                return Double.compare(angleA, angleB);
            }
        });
        return face;
    }

    private double[] computeCenter(List<double[]> points) {
        double[] center = new double[3];
        for (double[] point : points) {
            center[0] += point[0];
            center[1] += point[1];
            center[2] += point[2];
        }
        center[0] /= points.size();
        center[1] /= points.size();
        center[2] /= points.size();
        return center;
    }

    private double[] subtract(double[] a, double[] b) {
        return new double[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }

    private double[] cross(double[] a, double[] b) {
        return new double[] { (a[1] * b[2]) - (a[2] * b[1]), (a[2] * b[0]) - (a[0] * b[2]),
                (a[0] * b[1]) - (a[1] * b[0]) };
    }

    private double dot(double[] a, double[] b) {
        return (a[0] * b[0]) + (a[1] * b[1]) + (a[2] * b[2]);
    }

    private double[] normalize(double[] value) {
        double len = Math.sqrt(dot(value, value));
        if (len < HULL_EPSILON) {
            return new double[] { 1.0, 0.0, 0.0 };
        }
        return new double[] { value[0] / len, value[1] / len, value[2] / len };
    }

    private void emitLowPolyGroup(BlockModelExportSink sink, MapIterator lightingIterator, LowPolyGroup group) throws IOException {
        if (shouldTightenLowPolyGroup(group) && emitTightenedLowPolyGroup(sink, group)) {
            return;
        }
        SurfaceCell cell = new SurfaceCell();
        cell.minX = group.minX;
        cell.minZ = group.minZ;
        cell.lightX = group.seed.x;
        cell.lightZ = group.seed.z;
        cell.lightBlockY = group.seed.y;
        cell.lightBlockId = group.seed.blockId;

        double minX = group.minX;
        double minZ = group.minZ;
        double width = (group.maxX - group.minX) + 1.0;
        double depth = (group.maxZ - group.minZ) + 1.0;
        double bottomY = group.minY;
        double topY = group.maxY + 1.0;

        lightingIterator.initialize(group.seed.x, group.seed.y, group.seed.z);
        sink.setChunk(group.minX >> 4, group.minZ >> 4);
        emitTopArea(sink, lightingIterator, cell, minX, minZ, width, depth, topY, group.seed.topMaterial);
        emitLowPolySideQuad(sink, new double[] { minX, bottomY, minZ, minX, bottomY, minZ + depth, minX, topY,
                minZ + depth, minX, topY, minZ }, group.seed.westMaterial);
        emitLowPolySideQuad(sink, new double[] { minX + width, bottomY, minZ + depth, minX + width, bottomY, minZ,
                minX + width, topY, minZ, minX + width, topY, minZ + depth }, group.seed.eastMaterial);
        emitLowPolySideQuad(sink, new double[] { minX + width, bottomY, minZ, minX, bottomY, minZ, minX, topY, minZ,
                minX + width, topY, minZ }, group.seed.northMaterial);
        emitLowPolySideQuad(sink, new double[] { minX, bottomY, minZ + depth, minX + width, bottomY, minZ + depth,
                minX + width, topY, minZ + depth, minX, topY, minZ + depth }, group.seed.southMaterial);
    }

    private boolean shouldTightenLowPolyGroup(LowPolyGroup group) {
        double boxVolume = ((group.maxX - group.minX) + 1.0) * ((group.maxY - group.minY) + 1.0)
                * ((group.maxZ - group.minZ) + 1.0);
        return boxVolume > (group.members.size() * LOW_POLY_TIGHTEN_OVERFILL_RATIO);
    }

    private boolean emitTightenedLowPolyGroup(BlockModelExportSink sink, LowPolyGroup group) throws IOException {
        List<HullPlane> planes = buildLowPolyHullPlanes(group);
        List<double[]> vertices = computeHullVertices(planes);
        if (vertices.isEmpty()) {
            return false;
        }
        Map<String, HullFaceCandidate> facesByKey = new HashMap<String, HullFaceCandidate>();
        boolean emitted = false;
        for (HullPlane plane : planes) {
            if (!plane.emit || (plane.material == null)) {
                continue;
            }
            List<double[]> face = getPlaneFace(vertices, plane);
            if (face.size() < 3) {
                continue;
            }
            String faceKey = getHullFaceKey(face);
            HullFaceCandidate existing = facesByKey.get(faceKey);
            if ((existing == null) || shouldPreferHullFaceCandidate(group, plane, existing.plane)) {
                facesByKey.put(faceKey, new HullFaceCandidate(plane, face));
            }
        }
        for (HullFaceCandidate candidate : facesByKey.values()) {
            HullPlane plane = candidate.plane;
            List<double[]> face = candidate.face;
            double[] xyz = new double[face.size() * 3];
            for (int i = 0; i < face.size(); i++) {
                double[] point = face.get(i);
                xyz[i * 3] = point[0] + group.minX;
                xyz[(i * 3) + 1] = point[1] + group.minY;
                xyz[(i * 3) + 2] = point[2] + group.minZ;
            }
            PatchVertexLighting lighting = buildLowPolyPolygonLighting(face.size(), plane.material);
            sink.setChunk(group.minX >> 4, group.minZ >> 4);
            sink.addPolygon(xyz, plane.material, lighting.vertexColors, lighting.nightVertexLights);
            emitted = true;
        }
        return emitted;
    }

    private boolean shouldPreferHullFaceCandidate(LowPolyGroup group, HullPlane candidate, HullPlane current) {
        if (current == null) {
            return true;
        }
        if (candidate.material == current.material) {
            return false;
        }
        boolean candidateTop = isTopHullMaterial(group, candidate) && isTopFacingHullPlane(candidate);
        boolean currentTop = isTopHullMaterial(group, current) && isTopFacingHullPlane(current);
        if (candidateTop != currentTop) {
            return candidateTop;
        }
        return false;
    }

    private boolean isTopHullMaterial(LowPolyGroup group, HullPlane plane) {
        return (group.seed.topMaterial != null) && (plane.material != null)
                && group.seed.topMaterial.getMaterialId().equals(plane.material.getMaterialId());
    }

    private boolean isTopFacingHullPlane(HullPlane plane) {
        return (-plane.ny) > HULL_EPSILON;
    }

    private String getHullFaceKey(List<double[]> face) {
        ArrayList<String> vertexKeys = new ArrayList<String>(face.size());
        for (double[] point : face) {
            vertexKeys.add(quantizeHullCoord(point[0]) + "," + quantizeHullCoord(point[1]) + "," + quantizeHullCoord(point[2]));
        }
        Collections.sort(vertexKeys);
        StringBuilder key = new StringBuilder();
        for (String vertexKey : vertexKeys) {
            key.append(vertexKey).append(';');
        }
        return key.toString();
    }

    private long quantizeHullCoord(double value) {
        return Math.round(value / HULL_EPSILON);
    }

    private void emitLowPolySideQuad(BlockModelExportSink sink, double[] xyz, ExportMaterial material) throws IOException {
        if ((material == null) || (xyz == null) || (xyz.length != 12)) {
            return;
        }
        if ((xyz[7] - xyz[1]) <= EPSILON) {
            return;
        }
        PatchVertexLighting lighting = buildLowPolyPatchLighting(4, material);
        sink.addQuad(xyz, material, lighting.vertexColors, lighting.nightVertexLights);
    }

    private void seedFloodFillQueue(MapIterator geometryIterator, Set<String> visited, ArrayDeque<FloodFillNode> queue,
            int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) throws IOException {
        if (!isWithinFloodFillBounds(x, y, z, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ)) {
            return;
        }
        String key = blockKey(x, y, z);
        if (!visited.add(key)) {
            return;
        }
        geometryIterator.initialize(x, y, z);
        int blockId = geometryIterator.getBlockTypeID();
        if (hasSufficientSkyLight(geometryIterator) && canQueueLowPolyBlock(blockId)) {
            queue.addLast(new FloodFillNode(x, y, z));
        }
    }

    private boolean isWithinRenderBounds(int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) {
        return BlockModelFloodFillSupport.isWithinRenderBounds(x, y, z, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ,
                getMinY(), getMaxY());
    }

    private boolean isWithinFloodFillBounds(int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ,
            int rangeMaxZ) {
        return BlockModelFloodFillSupport.isWithinFloodFillBounds(x, y, z, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ,
                getMinY(), getMaxY());
    }

    private LowPolyBlockInfo getLowPolyBlockInfo(MapIterator iterator, Map<String, LowPolyBlockInfo> infoCache, int x, int y,
            int z) throws IOException {
        String key = blockKey(x, y, z);
        if (infoCache.containsKey(key)) {
            return infoCache.get(key);
        }
        iterator.initialize(x, y, z);
        int blockId = iterator.getBlockTypeID();
        if (blockId <= 0) {
            infoCache.put(key, null);
            return null;
        }

        ResolvedBlockData block = resolveBlock(iterator, blockId);
        ExportMaterial topMaterial = getSurfaceMaterial(block, BlockStep.Y_PLUS);
        if (topMaterial == null) {
            topMaterial = getAnySurfaceMaterial(block);
        }
        if (topMaterial == null) {
            infoCache.put(key, null);
            return null;
        }
        ExportMaterial westMaterial = getSurfaceMaterial(block, BlockStep.X_MINUS);
        ExportMaterial eastMaterial = getSurfaceMaterial(block, BlockStep.X_PLUS);
        ExportMaterial northMaterial = getSurfaceMaterial(block, BlockStep.Z_MINUS);
        ExportMaterial southMaterial = getSurfaceMaterial(block, BlockStep.Z_PLUS);
        if (westMaterial == null) {
            westMaterial = topMaterial;
        }
        if (eastMaterial == null) {
            eastMaterial = topMaterial;
        }
        if (northMaterial == null) {
            northMaterial = topMaterial;
        }
        if (southMaterial == null) {
            southMaterial = topMaterial;
        }

        ExportMaterial solidTop = toSolidColorMaterial(topMaterial);
        ExportMaterial solidWest = toSolidColorMaterial(westMaterial);
        ExportMaterial solidEast = toSolidColorMaterial(eastMaterial);
        ExportMaterial solidNorth = toSolidColorMaterial(northMaterial);
        ExportMaterial solidSouth = toSolidColorMaterial(southMaterial);

        String groupKey = getSolidColorKey(solidTop) + ":" + getSolidColorKey(solidWest) + ":" + getSolidColorKey(solidEast)
                + ":" + getSolidColorKey(solidNorth) + ":" + getSolidColorKey(solidSouth);
        LowPolyBlockInfo info = new LowPolyBlockInfo(x, y, z, blockId, groupKey, solidTop, solidWest, solidEast, solidNorth,
                solidSouth);
        infoCache.put(key, info);
        return info;
    }

    private String getSolidColorKey(ExportMaterial material) {
        if (material == null) {
            return "null";
        }
        return getSolidMaterialKey(material.getSolidColorArgb(), material.isEmissive());
    }


    private void emitTopArea(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, double minX,
            double minZ, double width, double depth, double topY, ExportMaterial material) throws IOException {
        if ((material == null) || (width <= EPSILON) || (depth <= EPSILON)) {
            return;
        }
        for (double dx = 0.0; dx < width - EPSILON; dx += 2.0) {
            double segWidth = Math.min(2.0, width - dx);
            for (double dz = 0.0; dz < depth - EPSILON; dz += 2.0) {
                double segDepth = Math.min(2.0, depth - dz);
                int baseX = (int) Math.floor(minX + dx);
                int baseY = (int) Math.floor(topY);
                int baseZ = (int) Math.floor(minZ + dz);
                double localX = (minX + dx) - baseX;
                double localY = topY - baseY;
                double localZ = (minZ + dz) - baseZ;
                PatchDefinition patch = (PatchDefinition) patchFactory.getPatch(localX, localY, localZ, localX,
                        localY, localZ + segDepth, localX + segWidth, localY, localZ, 0, 1, 0, 1, 100, SideVisible.TOP, 0);
                emitPatchAt(sink, lightingIterator, cell, patch, baseX, baseY, baseZ, BlockStep.Y_PLUS, material);
            }
        }
    }

    private void emitPatchAt(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, PatchDefinition patch,
            double x, double y, double z, BlockStep step, ExportMaterial material) throws IOException {
        emitPatchAt(sink, lightingIterator, patch, x, y, z, step, material, cell.lightBlockId);
    }

    private void emitPatchAt(BlockModelExportSink sink, MapIterator lightingIterator, PatchDefinition patch, double x,
            double y, double z, BlockStep step, ExportMaterial material, int blockId) throws IOException {
        if ((patch == null) || (material == null)) {
            return;
        }
        PatchVertexLighting lighting = buildPatchVertexLighting(patch, x, y, z, step, lightingIterator, blockId, material);
        sink.addPatch(patch, x, y, z, material, lighting.vertexColors, lighting.nightVertexLights);
    }

    private boolean isSolidTraceBlock(int blockId) {
        return (blockId > 0) && (TexturePack.HDTextureMap.getTransparency(blockId) == BlockTransparency.OPAQUE);
    }

    private String blockKey(int x, int y, int z) {
        return BlockModelFloodFillSupport.blockKey(x, y, z);
    }

    private ExportMaterial toSolidColorMaterial(ExportMaterial material) throws IOException {
        if ((material == null) || material.isSolidColor()) {
            return material;
        }
        int argb = computeAverageColor(material);
        String solidKey = getSolidMaterialKey(argb, material.isEmissive());
        ExportMaterial cached = solidColorMaterialCache.get(solidKey);
        if (cached != null) {
            return cached;
        }
        ExportMaterial solid = ExportMaterial.solidColor("solid_" + solidKey, argb, material.getMaterialType(), material.isEmissive());
        solidColorMaterialCache.put(solidKey, solid);
        return solid;
    }

    private String getSolidMaterialKey(int argb, boolean emissive) {
        return Integer.toHexString(argb) + "|" + emissive;
    }

    private int computeAverageColor(ExportMaterial material) throws IOException {
        String cacheKey = getMaterialCacheKey(material);
        Integer cached = averageColorCache.get(cacheKey);
        if (cached != null) {
            return cached.intValue();
        }
        int blendColor = getBlendColorArgb(material);
        if (blendColor >= 0) {
            averageColorCache.put(cacheKey, Integer.valueOf(blendColor));
            return blendColor;
        }
        if (exportTexturePack == null) {
            return 0xFFFFFFFF;
        }
        ExportedTextureData texture = exportTexturePack.exportTexture(material);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(texture.imagePng));
        if (image == null) {
            return 0xFFFFFFFF;
        }
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        for (int pixel : pixels) {
            if (((pixel >> 24) & 0xFF) <= 127) {
                continue;
            }
            red += (pixel >> 16) & 0xFF;
            green += (pixel >> 8) & 0xFF;
            blue += pixel & 0xFF;
            count++;
        }
        if (count <= 0) {
            averageColorCache.put(cacheKey, Integer.valueOf(0xFFFFFFFF));
            return 0xFFFFFFFF;
        }
        int argb = 0xFF000000 | (((int) (red / count)) << 16) | (((int) (green / count)) << 8) | ((int) (blue / count));
        averageColorCache.put(cacheKey, Integer.valueOf(argb));
        return argb;
    }

    private String getMaterialCacheKey(ExportMaterial material) {
        if (material == null) {
            return "null";
        }
        StringBuilder key = new StringBuilder();
        appendMaterialCacheKey(key, material);
        return key.toString();
    }

    private void appendMaterialCacheKey(StringBuilder key, ExportMaterial material) {
        key.append(material.getMaterialId()).append('|').append(material.getTextureIndex()).append('|')
                .append(material.getColorMultiplier()).append('|').append(material.getRotation()).append('|')
                .append(material.getMaterialType()).append('|').append(material.isEmissive());
        ExportMaterial[] bakedLayers = material.getBakedLayers();
        if (bakedLayers != null) {
            key.append('[');
            for (ExportMaterial layer : bakedLayers) {
                appendMaterialCacheKey(key, layer);
                key.append(';');
            }
            key.append(']');
        }
    }

    private int getBlendColorArgb(ExportMaterial material) {
        if (material == null) {
            return -1;
        }
        int colorMultiplier = material.getColorMultiplier() & 0xFFFFFF;
        if (colorMultiplier != 0xFFFFFF) {
            return 0xFF000000 | colorMultiplier;
        }
        ExportMaterial[] bakedLayers = material.getBakedLayers();
        if (bakedLayers != null) {
            for (ExportMaterial layer : bakedLayers) {
                int layerColor = getBlendColorArgb(layer);
                if (layerColor >= 0) {
                    return layerColor;
                }
            }
        }
        return -1;
    }

    private ExportMaterial getSurfaceMaterial(ResolvedBlockData block, BlockStep step) throws IOException {
        if ((block == null) || (block.materials == null)) {
            return null;
        }
        if (block.patches != null) {
            for (int i = 0; i < block.patches.length; i++) {
                if ((block.steps[i] == step) && (block.materials.length > i) && (block.materials[i] != null)) {
                    ExportMaterial material = getFirstSolidMaterial(block.materials[i]);
                    if (material != null) {
                        return material;
                    }
                }
            }
            for (int i = 0; i < block.patches.length; i++) {
                if ((block.steps[i] == step.opposite()) && (block.materials.length > i) && (block.materials[i] != null)) {
                    ExportMaterial material = getFirstSolidMaterial(block.materials[i]);
                    if (material != null) {
                        return material;
                    }
                }
            }
            return null;
        }
        int face = getSurfaceMaterialFaceIndex(step);
        if ((face >= 0) && (face < block.materials.length) && (block.materials[face] != null) && (block.steps.length > face && block.steps[face] == step.opposite())) {
            return getFirstSolidMaterial(block.materials[face]);
        }

        int opposite = step.opposite().ordinal();
        if ((opposite < block.materials.length) && (block.materials[opposite] != null) && (block.steps.length > opposite && block.steps[opposite] == step.opposite())) {
            return getFirstSolidMaterial(block.materials[opposite]);
        }

        return null;
    }

    private int getSurfaceMaterialFaceIndex(BlockStep step) {
        if (step == null) {
            return -1;
        }
        return step.opposite().getFaceEntered();
    }

    private double getMaxSurfaceLocalY(ResolvedBlockData block) throws IOException {
        if (block == null) {
            return 0.0;
        }
        if (block.patches != null) {
            double maxLocalY = 0.0;
            for (int i = 0; i < block.patches.length; i++) {
                if ((block.materials.length <= i) || (getFirstSolidMaterial(block.materials[i]) == null)) {
                    continue;
                }
                ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(block.patches[i], 0, 0, 0, 0);
                for (int off = 1; off < geometry.xyz.length; off += 3) {
                    maxLocalY = Math.max(maxLocalY, geometry.xyz[off]);
                }
            }
            return maxLocalY;
        }
        return (getAnySurfaceMaterial(block) != null) ? block.maxLocalY : 0.0;
    }

    private double[] getSideLocalSpan(ResolvedBlockData block, BlockStep side) throws IOException {
        if (block == null) {
            return null;
        }
        if (block.patches != null) {
            double minLocalY = Double.POSITIVE_INFINITY;
            double maxLocalY = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < block.patches.length; i++) {
                if ((block.steps[i] != side) || (block.materials.length <= i) || (getFirstSolidMaterial(block.materials[i]) == null)) {
                    continue;
                }
                ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(block.patches[i], 0, 0, 0, 0);
                for (int off = 1; off < geometry.xyz.length; off += 3) {
                    minLocalY = Math.min(minLocalY, geometry.xyz[off]);
                    maxLocalY = Math.max(maxLocalY, geometry.xyz[off]);
                }
            }
            if (!Double.isFinite(minLocalY) || !Double.isFinite(maxLocalY)) {
                return null;
            }
            return new double[] { minLocalY, maxLocalY };
        }
        return (getSurfaceMaterial(block, side) != null) ? new double[] { 0.0, block.maxLocalY } : null;
    }

    @Override
    protected PatchVertexLighting buildPatchVertexLighting(PatchDefinition patch, double x, double y, double z,
            BlockStep faceStep, MapIterator map, int blockId, ExportMaterial material) {
        int vertexCount = (patch.uplusvmax <= 1.0000001) ? 3 : 4;
        if ((material != null) && material.isSolidColor()) {
            return buildLowPolyPatchLighting(vertexCount, material);
        }
        return super.buildPatchVertexLighting(patch, x, y, z, faceStep, map, blockId, material);
    }

    private PatchVertexLighting buildLowPolyPatchLighting(int vertexCount, ExportMaterial material) {
        float[] colors = new float[vertexCount * 3];
        GLBExport.LightingMode lightingMode = getLightingMode();
        float[] nightLights = (lightingMode == GLBExport.LightingMode.BOTH) ? new float[vertexCount] : null;
        float dayLight = 1.0F;
        float nightLight = material.isEmissive() ? 1.0F : 0.0F;
        float primaryLight = dayLight;
        if (lightingMode == GLBExport.LightingMode.NIGHT) {
            primaryLight = nightLight;
        }
        for (int i = 0; i < vertexCount; i++) {
            int off = i * 3;
            colors[off] = primaryLight;
            colors[off + 1] = primaryLight;
            colors[off + 2] = primaryLight;
            if (nightLights != null) {
                nightLights[i] = nightLight;
            }
        }
        return new PatchVertexLighting(colors, nightLights);
    }

    private PatchVertexLighting buildLowPolyPolygonLighting(int vertexCount, ExportMaterial material) {
        return buildLowPolyPatchLighting(vertexCount, material);
    }
}
