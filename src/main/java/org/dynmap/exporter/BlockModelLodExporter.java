package org.dynmap.exporter;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.dynmap.DynmapWorld;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDBlockModels.CustomBlockModel;
import org.dynmap.hdmap.HDBlockModels.HDScaledBlockModels;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
import org.dynmap.hdmap.TexturePack.ExportedTextureData;
import org.dynmap.hdmap.TexturePackHDShader;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

final class BlockModelLodExporter {
    private static final int MODELSCALE = 16;
    private static final double BLKSIZE = 1.0 / (double) MODELSCALE;
    private static final ExportMaterial[][] EMPTY_MATERIALS = new ExportMaterial[0][];
    private static final double EPSILON = 1.0E-6;
    private static final BlockStep[] SEMI_STEPS =
            { BlockStep.Y_PLUS, BlockStep.X_MINUS, BlockStep.X_PLUS, BlockStep.Z_MINUS, BlockStep.Z_PLUS };
    private static final int WEST = 0;
    private static final int EAST = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final BlockStep[] SIDE_STEPS =
            { BlockStep.X_MINUS, BlockStep.X_PLUS, BlockStep.Z_MINUS, BlockStep.Z_PLUS };
    private static final int[][] FLOOD_FILL_DIRECTIONS =
            { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    private static final int MAX_ZOOMOUT_GROUP_SPAN = 8;
    private static final double ZOOMOUT_TIGHTEN_OVERFILL_RATIO = 1.25;
    private static final double HULL_EPSILON = 1.0E-5;
    private static final double[][] BOX_PATCH_POINTS = {
            { 0, 0, 0, 1, 0, 0, 0, 0, 1 },
            { 0, 1, 1, 1, 1, 1, 0, 1, 0 },
            { 1, 0, 0, 0, 0, 0, 1, 1, 0 },
            { 0, 0, 1, 1, 0, 1, 0, 1, 1 },
            { 0, 0, 0, 0, 0, 1, 0, 1, 0 },
            { 1, 0, 1, 1, 0, 0, 1, 1, 1 }
    };

    private static final class PatchVertexLighting {
        final float[] vertexColors;
        final float[] nightVertexLights;

        PatchVertexLighting(float[] vertexColors, float[] nightVertexLights) {
            this.vertexColors = vertexColors;
            this.nightVertexLights = nightVertexLights;
        }
    }

    private static final class ResolvedBlockData {
        final int blockId;
        final PatchDefinition[] patches;
        final BlockStep[] steps;
        final ExportMaterial[][] materials;
        final double maxLocalY;

        ResolvedBlockData(int blockId, PatchDefinition[] patches, BlockStep[] steps, ExportMaterial[][] materials,
                double maxLocalY) {
            this.blockId = blockId;
            this.patches = patches;
            this.steps = steps;
            this.materials = materials;
            this.maxLocalY = maxLocalY;
        }
    }

    private static final class SurfaceColumn {
        int x;
        int z;
        int blockY;
        int blockId;
        double topY;
        ExportMaterial topMaterial;
        final ExportMaterial[] sideMaterials = new ExportMaterial[4];
        boolean valid;
    }

    private static final class MaterialCounter {
        final Map<String, Integer> counts = new HashMap<String, Integer>();
        final Map<String, ExportMaterial> materials = new HashMap<String, ExportMaterial>();

        void add(ExportMaterial material) {
            if (material == null) {
                return;
            }
            String key = material.getMaterialId();
            Integer count = counts.get(key);
            counts.put(key, Integer.valueOf((count == null) ? 1 : (count.intValue() + 1)));
            materials.put(key, material);
        }

        ExportMaterial getDominant(ExportMaterial fallback) {
            ExportMaterial best = fallback;
            int bestCount = -1;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                int count = entry.getValue().intValue();
                if (count > bestCount) {
                    bestCount = count;
                    best = materials.get(entry.getKey());
                }
            }
            return best;
        }
    }

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

    private static final class WallTraceBlock {
        int x;
        int y;
        int z;
        int inset;
        int blockId;
        double topY;
        double bottomY;
        ExportMaterial sideMaterial;
        ResolvedBlockData block;
    }

    private static final class WallTraceResult {
        final ArrayList<WallTraceBlock> blocks = new ArrayList<WallTraceBlock>();
        WallTraceBlock anchor;
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

    private static final class ZoomoutBlockInfo {
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

        ZoomoutBlockInfo(int x, int y, int z, int blockId, String groupKey, ExportMaterial topMaterial,
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

    private static final class ZoomoutGroup {
        final ZoomoutBlockInfo seed;
        final ArrayList<ZoomoutBlockInfo> members = new ArrayList<ZoomoutBlockInfo>();
        int minX;
        int maxX;
        int minY;
        int maxY;
        int minZ;
        int maxZ;

        ZoomoutGroup(ZoomoutBlockInfo seed) {
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

    private final BlockModelExporter fullExporter;
    private final DynmapWorld world;
    private final HDShader shader;
    private final HDScaledBlockModels models;
    private final PatchDefinitionFactory patchFactory;
    private final TexturePack exportTexturePack;
    private final int[] brightnessTable;
    private final int minY;
    private final int maxY;
    private final boolean cullExportRegionEdges;
    private final GLBExport.LightingMode lightingMode;
    private final int simplifiedMinSkyLight;
    private final PatchDefinition[] defaultPatches = new PatchDefinition[6];
    private final Map<String, ExportMaterial> solidColorMaterialCache = new HashMap<String, ExportMaterial>();
    private final Map<String, Integer> averageColorCache = new HashMap<String, Integer>();

    BlockModelLodExporter(BlockModelExporter fullExporter, DynmapWorld world, HDShader shader, int minY, int maxY,
            boolean cullExportRegionEdges, GLBExport.LightingMode lightingMode, int simplifiedMinSkyLight) {
        this.fullExporter = fullExporter;
        this.world = world;
        this.shader = shader;
        this.models = HDBlockModels.getModelsForScale(MODELSCALE);
        this.patchFactory = HDBlockModels.getPatchDefinitionFactory();
        this.exportTexturePack =
                (shader instanceof TexturePackHDShader) ? ((TexturePackHDShader) shader).getTexturePackForExport() : null;
        this.brightnessTable = world.getBrightnessTable();
        this.minY = Math.max(0, minY);
        this.maxY = Math.min(world.worldheight - 1, maxY);
        this.cullExportRegionEdges = cullExportRegionEdges;
        this.lightingMode = (lightingMode == null) ? GLBExport.LightingMode.DAY : lightingMode;
        this.simplifiedMinSkyLight = Math.max(0, Math.min(15, simplifiedMinSkyLight));
        for (BlockStep step : BlockStep.values()) {
            double[] p = BOX_PATCH_POINTS[step.getFaceEntered()];
            int ordinal = step.ordinal();
            defaultPatches[ordinal] = patchFactory.getPatch(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], 0, 1,
                    0, 1, 100, SideVisible.TOP, ordinal);
        }
    }

    void exportSimplified(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX, int rangeMinZ,
            int rangeMaxZ) throws IOException {
        emitSimplifiedFloodFill(cache, sink, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
    }

    void exportZoomout(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX, int rangeMinZ,
            int rangeMaxZ, int lodZoomLevel) throws IOException {
        MapIterator geometryIterator = cache.getIterator(rangeMinX, maxY, rangeMinZ);
        Map<String, ZoomoutBlockInfo> infoCache = new HashMap<String, ZoomoutBlockInfo>();
        ArrayList<ZoomoutBlockInfo> visibleBlocks =
                collectZoomoutVisibleBlocks(geometryIterator, infoCache, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
        if (visibleBlocks.isEmpty()) {
            return;
        }
        MapIterator groupIterator = cache.getIterator(rangeMinX, maxY, rangeMinZ);
        MapIterator lightingIterator = cache.getIterator(rangeMinX, minY, rangeMinZ);
        Set<String> grouped = new HashSet<String>();
        for (ZoomoutBlockInfo seed : visibleBlocks) {
            String seedKey = blockKey(seed.x, seed.y, seed.z);
            if (grouped.contains(seedKey)) {
                continue;
            }
            ZoomoutGroup group = growZoomoutGroup(groupIterator, infoCache, grouped, seed, rangeMinX, rangeMaxX, rangeMinZ,
                    rangeMaxZ);
            emitZoomoutGroup(sink, lightingIterator, group);
        }
    }

    private ArrayList<ZoomoutBlockInfo> collectZoomoutVisibleBlocks(MapIterator geometryIterator,
            Map<String, ZoomoutBlockInfo> infoCache, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ)
            throws IOException {
        Set<String> visited = new HashSet<String>();
        Set<String> visible = new HashSet<String>();
        ArrayDeque<FloodFillNode> queue = new ArrayDeque<FloodFillNode>();
        ArrayList<ZoomoutBlockInfo> visibleBlocks = new ArrayList<ZoomoutBlockInfo>();

        seedFloodFillQueue(geometryIterator, visited, queue, rangeMinX - 1, maxY, rangeMinZ - 1, rangeMinX, rangeMaxX,
                rangeMinZ, rangeMaxZ);

        for (int z = rangeMinZ; z <= rangeMaxZ; z++) {
            for (int x = rangeMinX; x <= rangeMaxX; x++) {
                String key = blockKey(x, maxY, z);
                if (!visited.add(key)) {
                    continue;
                }
                processZoomoutFloodFillPosition(geometryIterator, infoCache, visible, visibleBlocks, queue, x, maxY, z, true);
            }
        }

        while (!queue.isEmpty()) {
            FloodFillNode current = queue.removeFirst();
            for (int[] direction : FLOOD_FILL_DIRECTIONS) {
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
                processZoomoutFloodFillPosition(geometryIterator, infoCache, visible, visibleBlocks, queue, neighborX,
                        neighborY, neighborZ,
                        isWithinRenderBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ));
            }
        }

        return visibleBlocks;
    }

    private void processZoomoutFloodFillPosition(MapIterator geometryIterator, Map<String, ZoomoutBlockInfo> infoCache,
            Set<String> visible, ArrayList<ZoomoutBlockInfo> visibleBlocks, ArrayDeque<FloodFillNode> queue, int x, int y,
            int z, boolean render) throws IOException {
        geometryIterator.initialize(x, y, z);
        int blockId = geometryIterator.getBlockTypeID();
        boolean skylightAllowed = hasSufficientSkyLight(geometryIterator);
        if (render) {
            ZoomoutBlockInfo info = getZoomoutBlockInfo(geometryIterator, infoCache, x, y, z);
            if ((info != null) && visible.add(blockKey(x, y, z))) {
                visibleBlocks.add(info);
            }
        }
        if (skylightAllowed && canQueueZoomoutBlock(blockId)) {
            queue.addLast(new FloodFillNode(x, y, z));
        }
    }

    private boolean canQueueZoomoutBlock(int blockId) {
        return !isSolidTraceBlock(blockId);
    }

    private boolean hasSufficientSkyLight(MapIterator geometryIterator) {
        if (!world.canGetSkyLightLevel()) {
            return true;
        }
        return geometryIterator.getBlockSkyLight() >= simplifiedMinSkyLight;
    }

    private ZoomoutGroup growZoomoutGroup(MapIterator geometryIterator, Map<String, ZoomoutBlockInfo> infoCache,
            Set<String> grouped, ZoomoutBlockInfo seed, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ)
            throws IOException {
        ZoomoutGroup group = new ZoomoutGroup(seed);
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
            ZoomoutBlockInfo info = getZoomoutBlockInfo(geometryIterator, infoCache, current.x, current.y, current.z);
            if ((info == null) || !seed.groupKey.equals(info.groupKey) || !canExpandZoomoutGroup(group, info.x, info.y, info.z)) {
                continue;
            }

            grouped.add(currentKey);
            group.members.add(info);
            updateZoomoutGroupBounds(group, info.x, info.y, info.z);

            for (int[] direction : FLOOD_FILL_DIRECTIONS) {
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

    private boolean canExpandZoomoutGroup(ZoomoutGroup group, int x, int y, int z) {
        int minX = Math.min(group.minX, x);
        int maxX = Math.max(group.maxX, x);
        int minY = Math.min(group.minY, y);
        int maxY = Math.max(group.maxY, y);
        int minZ = Math.min(group.minZ, z);
        int maxZ = Math.max(group.maxZ, z);
        return ((maxX - minX) < MAX_ZOOMOUT_GROUP_SPAN) && ((maxY - minY) < MAX_ZOOMOUT_GROUP_SPAN)
                && ((maxZ - minZ) < MAX_ZOOMOUT_GROUP_SPAN);
    }

    private void updateZoomoutGroupBounds(ZoomoutGroup group, int x, int y, int z) {
        group.minX = Math.min(group.minX, x);
        group.maxX = Math.max(group.maxX, x);
        group.minY = Math.min(group.minY, y);
        group.maxY = Math.max(group.maxY, y);
        group.minZ = Math.min(group.minZ, z);
        group.maxZ = Math.max(group.maxZ, z);
    }

    private List<HullPlane> buildZoomoutHullPlanes(ZoomoutGroup group) {
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
                int threshold = getZoomoutEdgeThreshold(group, sizeX, sizeY, sizeZ, 0, sx == 0, 1, sy == 0);
                if (threshold > 0) {
                    planes.add(buildHullPlane(sizeX, sizeY, sizeZ, true, sx == 0, true, sy == 0, false, true, threshold,
                            chooseZoomoutCutMaterial(group, true, sx == 0, true, sy == 0, false, true)));
                }
            }
        }
        for (int sx = 0; sx < 2; sx++) {
            for (int sz = 0; sz < 2; sz++) {
                int threshold = getZoomoutEdgeThreshold(group, sizeX, sizeY, sizeZ, 0, sx == 0, 2, sz == 0);
                if (threshold > 0) {
                    planes.add(buildHullPlane(sizeX, sizeY, sizeZ, true, sx == 0, false, true, true, sz == 0, threshold,
                            chooseZoomoutCutMaterial(group, true, sx == 0, false, true, true, sz == 0)));
                }
            }
        }
        for (int sy = 0; sy < 2; sy++) {
            for (int sz = 0; sz < 2; sz++) {
                int threshold = getZoomoutEdgeThreshold(group, sizeX, sizeY, sizeZ, 1, sy == 0, 2, sz == 0);
                if (threshold > 0) {
                    planes.add(buildHullPlane(sizeX, sizeY, sizeZ, false, true, true, sy == 0, true, sz == 0, threshold,
                            chooseZoomoutCutMaterial(group, false, true, true, sy == 0, true, sz == 0)));
                }
            }
        }
        for (int sx = 0; sx < 2; sx++) {
            for (int sy = 0; sy < 2; sy++) {
                for (int sz = 0; sz < 2; sz++) {
                    int threshold = getZoomoutCornerThreshold(group, sizeX, sizeY, sizeZ, sx == 0, sy == 0, sz == 0);
                    if (threshold > 0) {
                        planes.add(buildHullPlane(sizeX, sizeY, sizeZ, true, sx == 0, true, sy == 0, true, sz == 0,
                                threshold, chooseZoomoutCutMaterial(group, true, sx == 0, true, sy == 0, true, sz == 0)));
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

    private int getZoomoutEdgeThreshold(ZoomoutGroup group, int sizeX, int sizeY, int sizeZ, int axisA, boolean minA,
            int axisB, boolean minB) {
        int threshold = Integer.MAX_VALUE;
        for (ZoomoutBlockInfo member : group.members) {
            threshold = Math.min(threshold,
                    getZoomoutAxisDistance(group, member, sizeX, sizeY, sizeZ, axisA, minA)
                            + getZoomoutAxisDistance(group, member, sizeX, sizeY, sizeZ, axisB, minB));
        }
        return (threshold == Integer.MAX_VALUE) ? 0 : threshold;
    }

    private int getZoomoutCornerThreshold(ZoomoutGroup group, int sizeX, int sizeY, int sizeZ, boolean minX,
            boolean minY, boolean minZ) {
        int threshold = Integer.MAX_VALUE;
        for (ZoomoutBlockInfo member : group.members) {
            threshold = Math.min(threshold,
                    getZoomoutAxisDistance(group, member, sizeX, sizeY, sizeZ, 0, minX)
                            + getZoomoutAxisDistance(group, member, sizeX, sizeY, sizeZ, 1, minY)
                            + getZoomoutAxisDistance(group, member, sizeX, sizeY, sizeZ, 2, minZ));
        }
        return (threshold == Integer.MAX_VALUE) ? 0 : threshold;
    }

    private int getZoomoutAxisDistance(ZoomoutGroup group, ZoomoutBlockInfo member, int sizeX, int sizeY, int sizeZ,
            int axis, boolean minSide) {
        int local = (axis == 0) ? (member.x - group.minX) : ((axis == 1) ? (member.y - group.minY) : (member.z - group.minZ));
        int size = (axis == 0) ? sizeX : ((axis == 1) ? sizeY : sizeZ);
        return minSide ? local : (size - (local + 1));
    }

    private ExportMaterial chooseZoomoutCutMaterial(ZoomoutGroup group, boolean useX, boolean minX, boolean useY,
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

    private void emitZoomoutGroup(BlockModelExportSink sink, MapIterator lightingIterator, ZoomoutGroup group) throws IOException {
        if (shouldTightenZoomoutGroup(group) && emitTightenedZoomoutGroup(sink, group)) {
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
        emitZoomoutSideQuad(sink, new double[] { minX, bottomY, minZ, minX, bottomY, minZ + depth, minX, topY,
                minZ + depth, minX, topY, minZ }, group.seed.westMaterial);
        emitZoomoutSideQuad(sink, new double[] { minX + width, bottomY, minZ + depth, minX + width, bottomY, minZ,
                minX + width, topY, minZ, minX + width, topY, minZ + depth }, group.seed.eastMaterial);
        emitZoomoutSideQuad(sink, new double[] { minX + width, bottomY, minZ, minX, bottomY, minZ, minX, topY, minZ,
                minX + width, topY, minZ }, group.seed.northMaterial);
        emitZoomoutSideQuad(sink, new double[] { minX, bottomY, minZ + depth, minX + width, bottomY, minZ + depth,
                minX + width, topY, minZ + depth, minX, topY, minZ + depth }, group.seed.southMaterial);
    }

    private boolean shouldTightenZoomoutGroup(ZoomoutGroup group) {
        double boxVolume = ((group.maxX - group.minX) + 1.0) * ((group.maxY - group.minY) + 1.0)
                * ((group.maxZ - group.minZ) + 1.0);
        return boxVolume > (group.members.size() * ZOOMOUT_TIGHTEN_OVERFILL_RATIO);
    }

    private boolean emitTightenedZoomoutGroup(BlockModelExportSink sink, ZoomoutGroup group) throws IOException {
        List<HullPlane> planes = buildZoomoutHullPlanes(group);
        List<double[]> vertices = computeHullVertices(planes);
        if (vertices.isEmpty()) {
            return false;
        }
        boolean emitted = false;
        for (HullPlane plane : planes) {
            if (!plane.emit || (plane.material == null)) {
                continue;
            }
            List<double[]> face = getPlaneFace(vertices, plane);
            if (face.size() < 3) {
                continue;
            }
            double[] xyz = new double[face.size() * 3];
            for (int i = 0; i < face.size(); i++) {
                double[] point = face.get(i);
                xyz[i * 3] = point[0] + group.minX;
                xyz[(i * 3) + 1] = point[1] + group.minY;
                xyz[(i * 3) + 2] = point[2] + group.minZ;
            }
            PatchVertexLighting lighting = buildZoomoutPolygonLighting(face.size(), plane.material);
            sink.setChunk(group.minX >> 4, group.minZ >> 4);
            sink.addPolygon(xyz, plane.material, lighting.vertexColors, lighting.nightVertexLights);
            emitted = true;
        }
        return emitted;
    }

    private void emitZoomoutSideQuad(BlockModelExportSink sink, double[] xyz, ExportMaterial material) throws IOException {
        if ((material == null) || (xyz == null) || (xyz.length != 12)) {
            return;
        }
        if ((xyz[7] - xyz[1]) <= EPSILON) {
            return;
        }
        PatchVertexLighting lighting = buildZoomoutPatchLighting(4, material);
        sink.addQuad(xyz, material, lighting.vertexColors, lighting.nightVertexLights);
    }

    private SurfaceCell[][] buildSurfaceCells(MapChunkCache cache, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ,
            int cellSize, boolean solidColors) throws IOException {
        int cellsX = ((rangeMaxX - rangeMinX + 1) + cellSize - 1) / cellSize;
        int cellsZ = ((rangeMaxZ - rangeMinZ + 1) + cellSize - 1) / cellSize;
        SurfaceCell[][] cells = new SurfaceCell[cellsZ][cellsX];
        MapIterator scanIterator = cache.getIterator(rangeMinX, maxY, rangeMinZ);
        for (int cellZ = 0; cellZ < cellsZ; cellZ++) {
            int cellMinZ = rangeMinZ + (cellZ * cellSize);
            int cellMaxZ = Math.min(rangeMaxZ, cellMinZ + cellSize - 1);
            for (int cellX = 0; cellX < cellsX; cellX++) {
                int cellMinX = rangeMinX + (cellX * cellSize);
                int cellMaxX = Math.min(rangeMaxX, cellMinX + cellSize - 1);
                cells[cellZ][cellX] = buildSurfaceCell(scanIterator, cellMinX, cellMaxX, cellMinZ, cellMaxZ, solidColors);
            }
        }
        return cells;
    }

    private void emitSimplifiedFloodFill(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ) throws IOException {
        MapIterator geometryIterator = cache.getIterator(rangeMinX, maxY, rangeMinZ);
        Set<String> emittedBlocks = new HashSet<String>();
        Set<String> visited = new HashSet<String>();
        ArrayDeque<FloodFillNode> queue = new ArrayDeque<FloodFillNode>();

        seedFloodFillQueue(geometryIterator, visited, queue, rangeMinX - 1, maxY, rangeMinZ - 1, rangeMinX, rangeMaxX,
                rangeMinZ, rangeMaxZ);

        for (int z = rangeMinZ; z <= rangeMaxZ; z++) {
            for (int x = rangeMinX; x <= rangeMaxX; x++) {
                if (!visited.add(blockKey(x, maxY, z))) {
                    continue;
                }
                processSimplifiedPosition(geometryIterator, sink, emittedBlocks, queue, x, maxY, z, true);
            }
        }

        while (!queue.isEmpty()) {
            FloodFillNode current = queue.removeFirst();
            for (int[] direction : FLOOD_FILL_DIRECTIONS) {
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
                processSimplifiedPosition(geometryIterator, sink, emittedBlocks, queue, neighborX, neighborY, neighborZ,
                        isWithinRenderBounds(neighborX, neighborY, neighborZ, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ));
            }
        }
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
        if (canQueueSimplifiedBlock(geometryIterator, blockId)) {
            queue.addLast(new FloodFillNode(x, y, z));
        }
    }

    private void processSimplifiedPosition(MapIterator geometryIterator, BlockModelExportSink sink, Set<String> emittedBlocks,
            ArrayDeque<FloodFillNode> queue, int x, int y, int z, boolean render) throws IOException {
        geometryIterator.initialize(x, y, z);
        int blockId = geometryIterator.getBlockTypeID();
        if (render && hasRenderableGeometry(geometryIterator, blockId)) {
            emitSelectedBlock(sink, geometryIterator, x, y, z, emittedBlocks);
        }
        if (canQueueSimplifiedBlock(geometryIterator, blockId)) {
            queue.addLast(new FloodFillNode(x, y, z));
        }
    }

    private boolean isWithinRenderBounds(int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) {
        return (x >= rangeMinX) && (x <= rangeMaxX) && (y >= minY) && (y <= maxY) && (z >= rangeMinZ) && (z <= rangeMaxZ);
    }

    private boolean isWithinFloodFillBounds(int x, int y, int z, int rangeMinX, int rangeMaxX, int rangeMinZ,
            int rangeMaxZ) {
        return (x >= (rangeMinX - 1)) && (x <= (rangeMaxX + 1)) && (y >= minY) && (y <= maxY)
                && (z >= (rangeMinZ - 1)) && (z <= (rangeMaxZ + 1));
    }

    private boolean hasRenderableGeometry(MapIterator geometryIterator, int blockId) throws IOException {
        if (blockId <= 0) {
            return false;
        }
        return getAnySurfaceMaterial(resolveBlock(geometryIterator, blockId)) != null;
    }

    private boolean canQueueSimplifiedBlock(MapIterator geometryIterator, int blockId) {
        if (isSolidTraceBlock(blockId)) {
            return false;
        }
        if (!world.canGetSkyLightLevel()) {
            return true;
        }
        return geometryIterator.getBlockSkyLight() >= simplifiedMinSkyLight;
    }

    private SurfaceCell buildSurfaceCell(MapIterator iterator, int cellMinX, int cellMaxX, int cellMinZ, int cellMaxZ,
            boolean solidColors) throws IOException {
        SurfaceCell cell = new SurfaceCell();
        cell.minX = cellMinX;
        cell.minZ = cellMinZ;
        cell.width = cellMaxX - cellMinX + 1;
        cell.depth = cellMaxZ - cellMinZ + 1;

        MaterialCounter topCounter = new MaterialCounter();
        MaterialCounter westCounter = new MaterialCounter();
        MaterialCounter eastCounter = new MaterialCounter();
        MaterialCounter northCounter = new MaterialCounter();
        MaterialCounter southCounter = new MaterialCounter();
        SurfaceColumn representative = null;

        for (int z = cellMinZ; z <= cellMaxZ; z++) {
            for (int x = cellMinX; x <= cellMaxX; x++) {
                SurfaceColumn column = resolveSurfaceColumn(iterator, x, z);
                if (!column.valid) {
                    continue;
                }
                cell.valid = true;
                if ((representative == null) || (column.topY > representative.topY)) {
                    representative = column;
                    cell.topY = column.topY;
                }
                topCounter.add(column.topMaterial);
                if (x == cellMinX) {
                    westCounter.add(column.sideMaterials[WEST]);
                }
                if (x == cellMaxX) {
                    eastCounter.add(column.sideMaterials[EAST]);
                }
                if (z == cellMinZ) {
                    northCounter.add(column.sideMaterials[NORTH]);
                }
                if (z == cellMaxZ) {
                    southCounter.add(column.sideMaterials[SOUTH]);
                }
            }
        }

        if (!cell.valid || (representative == null)) {
            return cell;
        }

        cell.lightX = representative.x;
        cell.lightZ = representative.z;
        cell.lightBlockY = representative.blockY;
        cell.lightBlockId = representative.blockId;
        cell.topMaterial = finalizeMaterial(topCounter.getDominant(representative.topMaterial), solidColors);
        cell.sideMaterials[WEST] = finalizeMaterial(westCounter.getDominant(representative.sideMaterials[WEST]), solidColors);
        cell.sideMaterials[EAST] = finalizeMaterial(eastCounter.getDominant(representative.sideMaterials[EAST]), solidColors);
        cell.sideMaterials[NORTH] =
                finalizeMaterial(northCounter.getDominant(representative.sideMaterials[NORTH]), solidColors);
        cell.sideMaterials[SOUTH] =
                finalizeMaterial(southCounter.getDominant(representative.sideMaterials[SOUTH]), solidColors);
        for (int i = 0; i < cell.sideMaterials.length; i++) {
            if (cell.sideMaterials[i] == null) {
                cell.sideMaterials[i] = cell.topMaterial;
            }
        }
        return cell;
    }

    private ExportMaterial finalizeMaterial(ExportMaterial material, boolean solidColors) throws IOException {
        if ((material == null) || !solidColors) {
            return material;
        }
        return toSolidColorMaterial(material);
    }

    private ZoomoutBlockInfo getZoomoutBlockInfo(MapIterator iterator, Map<String, ZoomoutBlockInfo> infoCache, int x, int y,
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

        String groupKey = blockId + ":" + iterator.getBlockData() + ":" + getSolidColorKey(solidTop) + ":"
                + getSolidColorKey(solidWest) + ":" + getSolidColorKey(solidEast) + ":" + getSolidColorKey(solidNorth) + ":"
                + getSolidColorKey(solidSouth);
        ZoomoutBlockInfo info = new ZoomoutBlockInfo(x, y, z, blockId, groupKey, solidTop, solidWest, solidEast, solidNorth,
                solidSouth);
        infoCache.put(key, info);
        return info;
    }

    private String getSolidColorKey(ExportMaterial material) {
        if (material == null) {
            return "null";
        }
        return Integer.toHexString(material.getSolidColorArgb()) + ":" + material.getMaterialType() + ":" + material.isEmissive();
    }

    private SurfaceColumn resolveSurfaceColumn(MapIterator iterator, int x, int z) throws IOException {
        SurfaceColumn column = new SurfaceColumn();
        column.x = x;
        column.z = z;
        iterator.initialize(x, maxY, z);
        for (int y = maxY; y >= minY; y--) {
            iterator.setY(y);
            int blockId = iterator.getBlockTypeID();
            if (blockId <= 0) {
                continue;
            }
            ResolvedBlockData block = resolveBlock(iterator, blockId);
            ExportMaterial topMaterial = getSurfaceMaterial(block, BlockStep.Y_PLUS);
            if (topMaterial == null) {
                topMaterial = getAnySurfaceMaterial(block);
            }
            if (topMaterial == null) {
                continue;
            }
            double topLocalY = getMaxSurfaceLocalY(block);
            if (topLocalY <= EPSILON) {
                continue;
            }
            column.valid = true;
            column.blockY = y;
            column.blockId = blockId;
            column.topY = y + topLocalY;
            column.topMaterial = topMaterial;
            column.sideMaterials[WEST] = getSurfaceMaterial(block, BlockStep.X_MINUS);
            column.sideMaterials[EAST] = getSurfaceMaterial(block, BlockStep.X_PLUS);
            column.sideMaterials[NORTH] = getSurfaceMaterial(block, BlockStep.Z_MINUS);
            column.sideMaterials[SOUTH] = getSurfaceMaterial(block, BlockStep.Z_PLUS);
            for (int i = 0; i < column.sideMaterials.length; i++) {
                if (column.sideMaterials[i] == null) {
                    column.sideMaterials[i] = topMaterial;
                }
            }
            return column;
        }
        return column;
    }

    private void emitTexturedSurface(MapChunkCache cache, SurfaceCell[][] cells, BlockModelExportSink sink,
            MapIterator lightingIterator)
            throws IOException {
        MapIterator geometryIterator = cache.getIterator(cells[0][0].minX, maxY, cells[0][0].minZ);
        Set<String> emittedBlocks = new HashSet<String>();
        for (int z = 0; z < cells.length; z++) {
            for (int x = 0; x < cells[z].length; x++) {
                SurfaceCell cell = cells[z][x];
                if (!cell.valid || (cell.topMaterial == null)) {
                    continue;
                }
                lightingIterator.initialize(cell.lightX, cell.lightBlockY, cell.lightZ);
                sink.setChunk(cell.minX >> 4, cell.minZ >> 4);
                emitTopStack(sink, geometryIterator, cell, emittedBlocks);
                traceWall(sink, geometryIterator, cell, BlockStep.X_MINUS, emittedBlocks);
                traceWall(sink, geometryIterator, cell, BlockStep.X_PLUS, emittedBlocks);
                traceWall(sink, geometryIterator, cell, BlockStep.Z_MINUS, emittedBlocks);
                traceWall(sink, geometryIterator, cell, BlockStep.Z_PLUS, emittedBlocks);
            }
        }
    }

    private void emitMergedSurface(SurfaceCell[][] cells, BlockModelExportSink sink, MapIterator lightingIterator)
            throws IOException {
        emitMergedTopPatches(cells, sink, lightingIterator);
        emitMergedWestEastPatches(cells, sink, lightingIterator, true);
        emitMergedWestEastPatches(cells, sink, lightingIterator, false);
        emitMergedNorthSouthPatches(cells, sink, lightingIterator, true);
        emitMergedNorthSouthPatches(cells, sink, lightingIterator, false);
    }

    private void emitMergedTopPatches(SurfaceCell[][] cells, BlockModelExportSink sink, MapIterator lightingIterator)
            throws IOException {
        boolean[][] used = new boolean[cells.length][cells[0].length];
        for (int z = 0; z < cells.length; z++) {
            for (int x = 0; x < cells[z].length; x++) {
                SurfaceCell cell = cells[z][x];
                if (used[z][x] || !cell.valid || (cell.topMaterial == null)) {
                    continue;
                }
                int widthCount = 1;
                while ((x + widthCount) < cells[z].length && matchesTop(cell, cells[z][x + widthCount])
                        && !used[z][x + widthCount]) {
                    widthCount++;
                }
                int depthCount = 1;
                boolean expandable = true;
                while (expandable && ((z + depthCount) < cells.length)) {
                    for (int xx = 0; xx < widthCount; xx++) {
                        if (used[z + depthCount][x + xx] || !matchesTop(cell, cells[z + depthCount][x + xx])) {
                            expandable = false;
                            break;
                        }
                    }
                    if (expandable) {
                        depthCount++;
                    }
                }
                for (int zz = 0; zz < depthCount; zz++) {
                    for (int xx = 0; xx < widthCount; xx++) {
                        used[z + zz][x + xx] = true;
                    }
                }
                lightingIterator.initialize(cell.lightX, cell.lightBlockY, cell.lightZ);
                sink.setChunk(cell.minX >> 4, cell.minZ >> 4);
                emitTopArea(sink, lightingIterator, cell, cell.minX, cell.minZ, cell.width * widthCount,
                        cell.depth * depthCount, cell.topY, cell.topMaterial);
            }
        }
    }

    private void emitMergedWestEastPatches(SurfaceCell[][] cells, BlockModelExportSink sink, MapIterator lightingIterator,
            boolean west) throws IOException {
        boolean[][] used = new boolean[cells.length][cells[0].length];
        int direction = west ? WEST : EAST;
        BlockStep step = west ? BlockStep.X_MINUS : BlockStep.X_PLUS;
        for (int x = 0; x < cells[0].length; x++) {
            for (int z = 0; z < cells.length; z++) {
                SurfaceCell cell = cells[z][x];
                if (used[z][x] || !cell.valid) {
                    continue;
                }
                double bottom = getNeighborTop(cells, west ? (x - 1) : (x + 1), z);
                if ((cell.topY - bottom) <= EPSILON || (cell.sideMaterials[direction] == null)) {
                    continue;
                }
                int depthCount = 1;
                while ((z + depthCount) < cells.length && !used[z + depthCount][x]
                        && matchesSide(cell, cells[z + depthCount][x], direction,
                                getNeighborTop(cells, west ? (x - 1) : (x + 1), z + depthCount), bottom)) {
                    depthCount++;
                }
                for (int zz = 0; zz < depthCount; zz++) {
                    used[z + zz][x] = true;
                }
                lightingIterator.initialize(cell.lightX, cell.lightBlockY, cell.lightZ);
                sink.setChunk(cell.minX >> 4, cell.minZ >> 4);
                if (west) {
                    emitWestArea(sink, lightingIterator, cell, cell.minX, cell.minZ, cell.depth * depthCount, bottom,
                            cell.topY, cell.sideMaterials[direction]);
                } else {
                    emitEastArea(sink, lightingIterator, cell, cell.minX + cell.width, cell.minZ,
                            cell.depth * depthCount, bottom, cell.topY, cell.sideMaterials[direction]);
                }
            }
        }
    }

    private void emitMergedNorthSouthPatches(SurfaceCell[][] cells, BlockModelExportSink sink,
            MapIterator lightingIterator, boolean north) throws IOException {
        boolean[][] used = new boolean[cells.length][cells[0].length];
        int direction = north ? NORTH : SOUTH;
        BlockStep step = north ? BlockStep.Z_MINUS : BlockStep.Z_PLUS;
        for (int z = 0; z < cells.length; z++) {
            for (int x = 0; x < cells[z].length; x++) {
                SurfaceCell cell = cells[z][x];
                if (used[z][x] || !cell.valid) {
                    continue;
                }
                double bottom = getNeighborTop(cells, x, north ? (z - 1) : (z + 1));
                if ((cell.topY - bottom) <= EPSILON || (cell.sideMaterials[direction] == null)) {
                    continue;
                }
                int widthCount = 1;
                while ((x + widthCount) < cells[z].length && !used[z][x + widthCount]
                        && matchesSide(cell, cells[z][x + widthCount], direction,
                                getNeighborTop(cells, x + widthCount, north ? (z - 1) : (z + 1)), bottom)) {
                    widthCount++;
                }
                for (int xx = 0; xx < widthCount; xx++) {
                    used[z][x + xx] = true;
                }
                lightingIterator.initialize(cell.lightX, cell.lightBlockY, cell.lightZ);
                sink.setChunk(cell.minX >> 4, cell.minZ >> 4);
                if (north) {
                    emitNorthArea(sink, lightingIterator, cell, cell.minX, cell.minZ, cell.width * widthCount, bottom,
                            cell.topY, cell.sideMaterials[direction]);
                } else {
                    emitSouthArea(sink, lightingIterator, cell, cell.minX, cell.minZ + cell.depth, cell.width * widthCount,
                            bottom, cell.topY, cell.sideMaterials[direction]);
                }
            }
        }
    }

    private boolean matchesTop(SurfaceCell base, SurfaceCell other) {
        return other.valid && (other.topMaterial != null) && sameMaterial(base.topMaterial, other.topMaterial)
                && (Math.abs(base.topY - other.topY) <= EPSILON) && (base.width == other.width)
                && (base.depth == other.depth);
    }

    private boolean matchesSide(SurfaceCell base, SurfaceCell other, int direction, double otherBottom, double baseBottom) {
        return other.valid && sameMaterial(base.sideMaterials[direction], other.sideMaterials[direction])
                && (Math.abs(base.topY - other.topY) <= EPSILON) && (Math.abs(baseBottom - otherBottom) <= EPSILON)
                && (base.width == other.width) && (base.depth == other.depth);
    }

    private boolean sameMaterial(ExportMaterial a, ExportMaterial b) {
        if (a == null) {
            return b == null;
        }
        return (b != null) && a.getMaterialId().equals(b.getMaterialId());
    }

    private double getNeighborTop(SurfaceCell[][] cells, int x, int z) {
        if ((z < 0) || (z >= cells.length) || (x < 0) || (x >= cells[z].length)) {
            return cullExportRegionEdges ? Double.POSITIVE_INFINITY : minY;
        }
        SurfaceCell neighbor = cells[z][x];
        return neighbor.valid ? neighbor.topY : minY;
    }

    private void emitPatch(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, PatchDefinition patch,
            BlockStep step, ExportMaterial material) throws IOException {
        if ((patch == null) || (material == null)) {
            return;
        }
        PatchVertexLighting lighting =
                buildPatchVertexLighting(patch, cell.minX, 0, cell.minZ, step, lightingIterator, cell.lightBlockId, material);
        sink.addPatch(patch, cell.minX, 0, cell.minZ, material, lighting.vertexColors, lighting.nightVertexLights);
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

    private void emitWestArea(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, double planeX,
            double minZ, double depth, double bottomY, double topY, ExportMaterial material) throws IOException {
        emitVerticalArea(sink, lightingIterator, cell, planeX, minZ, depth, bottomY, topY, material, BlockStep.X_MINUS, true);
    }

    private void emitEastArea(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, double planeX,
            double minZ, double depth, double bottomY, double topY, ExportMaterial material) throws IOException {
        emitVerticalArea(sink, lightingIterator, cell, planeX, minZ, depth, bottomY, topY, material, BlockStep.X_PLUS, true);
    }

    private void emitNorthArea(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, double minX,
            double planeZ, double width, double bottomY, double topY, ExportMaterial material) throws IOException {
        emitVerticalArea(sink, lightingIterator, cell, planeZ, minX, width, bottomY, topY, material, BlockStep.Z_MINUS, false);
    }

    private void emitSouthArea(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, double minX,
            double planeZ, double width, double bottomY, double topY, ExportMaterial material) throws IOException {
        emitVerticalArea(sink, lightingIterator, cell, planeZ, minX, width, bottomY, topY, material, BlockStep.Z_PLUS, false);
    }

    private void emitVerticalArea(BlockModelExportSink sink, MapIterator lightingIterator, SurfaceCell cell, double fixedCoord,
            double alongStart, double alongLength, double bottomY, double topY, ExportMaterial material, BlockStep step,
            boolean alongZ) throws IOException {
        if ((material == null) || (topY - bottomY) <= EPSILON || (alongLength <= EPSILON)
                || Double.isInfinite(bottomY) || Double.isInfinite(topY)) {
            return;
        }
        for (double alongOffset = 0.0; alongOffset < alongLength - EPSILON; alongOffset += 2.0) {
            double segAlong = Math.min(2.0, alongLength - alongOffset);
            double currentY = bottomY;
            while (currentY < topY - EPSILON) {
                int baseY = (int) Math.floor(currentY);
                double localBottom = currentY - baseY;
                double segHeight = Math.min(topY - currentY, 2.0 - localBottom);
                if (segHeight <= EPSILON) {
                    break;
                }
                int baseX;
                int baseZ;
                double localX;
                double localZ;
                double localAlong = (alongStart + alongOffset) - Math.floor(alongStart + alongOffset);
                if (alongZ) {
                    baseX = (int) Math.floor(fixedCoord);
                    baseZ = (int) Math.floor(alongStart + alongOffset);
                    localX = fixedCoord - baseX;
                    localZ = (alongStart + alongOffset) - baseZ;
                } else {
                    baseX = (int) Math.floor(alongStart + alongOffset);
                    baseZ = (int) Math.floor(fixedCoord);
                    localX = (alongStart + alongOffset) - baseX;
                    localZ = fixedCoord - baseZ;
                }
                PatchDefinition patch = createVerticalPatch(localX, localBottom, localZ, segAlong, segHeight, step, alongZ);
                emitPatchAt(sink, lightingIterator, cell, patch, baseX, baseY, baseZ, step, material);
                currentY += segHeight;
            }
        }
    }

    private PatchDefinition createVerticalPatch(double localX, double localBottomY, double localZ, double alongLength,
            double height, BlockStep step, boolean alongZ) {
        if (alongZ) {
            if (step == BlockStep.X_MINUS) {
                return (PatchDefinition) patchFactory.getPatch(localX, localBottomY, localZ, localX, localBottomY,
                        localZ + alongLength, localX, localBottomY + height, localZ, 0, 1, 0, 1, 100, SideVisible.TOP, 0);
            }
            return (PatchDefinition) patchFactory.getPatch(localX, localBottomY, localZ, localX, localBottomY + height,
                    localZ, localX, localBottomY, localZ + alongLength, 0, 1, 0, 1, 100, SideVisible.TOP, 0);
        }
        if (step == BlockStep.Z_MINUS) {
            return (PatchDefinition) patchFactory.getPatch(localX, localBottomY, localZ, localX, localBottomY + height,
                    localZ, localX + alongLength, localBottomY, localZ, 0, 1, 0, 1, 100, SideVisible.TOP, 0);
        }
        return (PatchDefinition) patchFactory.getPatch(localX, localBottomY, localZ, localX + alongLength, localBottomY,
                localZ, localX, localBottomY + height, localZ, 0, 1, 0, 1, 100, SideVisible.TOP, 0);
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

    private void traceWall(BlockModelExportSink sink, MapIterator geometryIterator, SurfaceCell cell, BlockStep side,
            Set<String> emittedBlocks) throws IOException {
        WallTraceResult current = findWallTraceBlocks(geometryIterator, cell, side, cell.lightBlockY, 0);
        if (current == null) {
            return;
        }

        emitWallTraceBlocks(sink, geometryIterator, current, emittedBlocks);

        int startInset = current.anchor.inset;
        int searchY = current.anchor.y - 1;
        while (searchY >= minY) {
            WallTraceResult next = findWallTraceBlocks(geometryIterator, cell, side, searchY, startInset);
            if (next == null) {
                break;
            }
            emitWallTraceBlocks(sink, geometryIterator, next, emittedBlocks);
            current = next;
            startInset = next.anchor.inset;
            searchY = next.anchor.y - 1;
        }
    }

    private void emitTopStack(BlockModelExportSink sink, MapIterator geometryIterator, SurfaceCell cell, Set<String> emittedBlocks)
            throws IOException {
        emitColumnStack(sink, geometryIterator, cell.minX, cell.minZ, maxY, emittedBlocks);
    }

    private WallTraceResult findWallTraceBlocks(MapIterator geometryIterator, SurfaceCell cell, BlockStep side, int startY,
            int startInset) throws IOException {
        for (int y = startY; y >= minY; y--) {
            WallTraceResult result = null;
            for (int inset = startInset; inset <= (startInset + 3); inset++) {
                int x = cell.minX;
                int z = cell.minZ;
                switch (side) {
                    case X_MINUS:
                        x = cell.minX + inset;
                        break;
                    case X_PLUS:
                        x = cell.minX - inset;
                        break;
                    case Z_MINUS:
                        z = cell.minZ + inset;
                        break;
                    case Z_PLUS:
                        z = cell.minZ - inset;
                        break;
                    default:
                        break;
                }
                geometryIterator.initialize(x, y, z);
                int blockId = geometryIterator.getBlockTypeID();
                if (blockId <= 0) {
                    continue;
                }
                if (!isWallSideExposed(blockId, geometryIterator, side)) {
                    continue;
                }
                ResolvedBlockData block = resolveBlock(geometryIterator, blockId);
                ExportMaterial sideMaterial = getSurfaceMaterial(block, side);
                if (sideMaterial == null) {
                    continue;
                }
                double[] span = getSideLocalSpan(block, side);
                if ((span == null) || ((span[1] - span[0]) <= EPSILON)) {
                    continue;
                }
                WallTraceBlock traced = new WallTraceBlock();
                traced.x = x;
                traced.y = y;
                traced.z = z;
                traced.inset = inset;
                traced.blockId = blockId;
                traced.topY = y + span[1];
                traced.bottomY = y + span[0];
                traced.sideMaterial = sideMaterial;
                traced.block = block;
                if (result == null) {
                    result = new WallTraceResult();
                }
                result.anchor = traced;
                result.blocks.add(traced);
                if (isSolidTraceBlock(blockId)) {
                    result.anchor = traced;
                    return result;
                }
            }
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean isWallSideExposed(int blockId, MapIterator geometryIterator, BlockStep side) {
        TexturePack.BlockTransparency transparency = TexturePack.HDTextureMap.getTransparency(blockId);
        if (transparency != BlockTransparency.OPAQUE) {
            return true;
        }
        int adjacentBlock = geometryIterator.getBlockTypeIDAt(side);
        return (adjacentBlock <= 0)
                || (TexturePack.HDTextureMap.getTransparency(adjacentBlock) != BlockTransparency.OPAQUE);
    }

    private void emitSelectedBlock(BlockModelExportSink sink, MapIterator geometryIterator, int x, int y, int z,
            Set<String> emittedBlocks) throws IOException {
        String key = blockKey(x, y, z);
        if (!emittedBlocks.add(key)) {
            return;
        }
        geometryIterator.initialize(x, y, z);
        sink.setChunk(x >> 4, z >> 4);
        fullExporter.emitCurrentBlock(geometryIterator, sink);
    }

    private void emitWallTraceBlocks(BlockModelExportSink sink, MapIterator geometryIterator, WallTraceResult result,
            Set<String> emittedBlocks) throws IOException {
        for (WallTraceBlock block : result.blocks) {
            emitColumnStack(sink, geometryIterator, block.x, block.z, block.y, emittedBlocks);
        }
    }

    private void emitColumnStack(BlockModelExportSink sink, MapIterator geometryIterator, int x, int z, int startY,
            Set<String> emittedBlocks) throws IOException {
        geometryIterator.initialize(x, Math.min(maxY, startY), z);
        for (int y = Math.min(maxY, startY); y >= minY; y--) {
            geometryIterator.setY(y);
            int blockId = geometryIterator.getBlockTypeID();
            if (blockId <= 0) {
                continue;
            }
            ResolvedBlockData block = resolveBlock(geometryIterator, blockId);
            if (getAnySurfaceMaterial(block) == null) {
                continue;
            }
            emitSelectedBlock(sink, geometryIterator, x, y, z, emittedBlocks);
            if (isSolidTraceBlock(blockId)) {
                break;
            }
        }
    }

    private boolean isSolidTraceBlock(int blockId) {
        return (blockId > 0) && (TexturePack.HDTextureMap.getTransparency(blockId) == BlockTransparency.OPAQUE);
    }

    private String blockKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private ExportMaterial toSolidColorMaterial(ExportMaterial material) throws IOException {
        if ((material == null) || material.isSolidColor()) {
            return material;
        }
        String cacheKey = getMaterialCacheKey(material);
        ExportMaterial cached = solidColorMaterialCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        int argb = computeAverageColor(material);
        ExportMaterial solid = ExportMaterial.solidColor("solid_" + cacheKey, argb, material.getMaterialType(),
                material.isEmissive());
        solidColorMaterialCache.put(cacheKey, solid);
        return solid;
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

    private ResolvedBlockData resolveBlock(MapIterator map, int blockId) {
        BlockStep[] steps = BlockStep.values();
        int[] textureIndexes = null;
        int blockData = map.getBlockData();
        int renderData = HDBlockModels.getBlockRenderData(blockId, map);
        CustomRendererData customRenderData = null;
        boolean patchModelUsesPatchTextureCoords = false;

        RenderPatch[] patches = models.getPatchModel(blockId, blockData, renderData);
        if (patches == null) {
            CustomBlockModel customBlockModel = models.getCustomBlockModel(blockId, blockData);
            if (customBlockModel != null) {
                customRenderData = customBlockModel.getMeshForBlock(map);
                if (customRenderData != null) {
                    patches = customRenderData.getCustomMesh();
                }
            }
        }
        if (patches != null) {
            patchModelUsesPatchTextureCoords = true;
            steps = new BlockStep[patches.length];
            textureIndexes = new int[patches.length];
            for (int i = 0; i < textureIndexes.length; i++) {
                PatchDefinition patch = (PatchDefinition) patches[i];
                textureIndexes[i] = patch.getTextureIndex();
                steps[i] = getVisiblePatchStep(patch);
            }
        } else {
            short[] scaledModel = models.getScaledModel(blockId, blockData, renderData);
            if (scaledModel != null) {
                patches = getScaledModelAsPatches(scaledModel);
                steps = new BlockStep[patches.length];
                textureIndexes = new int[patches.length];
                for (int i = 0; i < patches.length; i++) {
                    PatchDefinition patch = (PatchDefinition) patches[i];
                    steps[i] = getVisiblePatchStep(patch);
                    textureIndexes[i] = patch.getTextureIndex();
                }
            }
        }

        ExportMaterial[][] materials =
                resolveMaterials(blockId, blockData, renderData, map, textureIndexes, steps, customRenderData,
                        !patchModelUsesPatchTextureCoords);
        collapseLayeredMaterials(materials);
        PatchDefinition[] resolvedPatches = (patches != null) ? toPatchDefinitions(patches) : null;
        double maxLocalY = computeMaxLocalY(resolvedPatches);
        return new ResolvedBlockData(blockId, resolvedPatches, steps, materials, maxLocalY);
    }

    private PatchDefinition[] toPatchDefinitions(RenderPatch[] patches) {
        PatchDefinition[] definitions = new PatchDefinition[patches.length];
        for (int i = 0; i < patches.length; i++) {
            definitions[i] = (PatchDefinition) patches[i];
        }
        return definitions;
    }

    private double computeMaxLocalY(PatchDefinition[] patches) {
        if ((patches == null) || (patches.length == 0)) {
            return 1.0;
        }
        double maxLocalY = 0.0;
        for (PatchDefinition patch : patches) {
            ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(patch, 0, 0, 0, 0);
            for (int i = 1; i < geometry.xyz.length; i += 3) {
                maxLocalY = Math.max(maxLocalY, geometry.xyz[i]);
            }
        }
        return Math.max(EPSILON, maxLocalY);
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

    private ExportMaterial getAnySurfaceMaterial(ResolvedBlockData block) throws IOException {
        if ((block == null) || (block.materials == null)) {
            return null;
        }
        for (ExportMaterial[] materials : block.materials) {
            ExportMaterial material = getFirstSolidMaterial(materials);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    private ExportMaterial getFirstSolidMaterial(ExportMaterial[] materials) throws IOException {
        if (materials == null) {
            return null;
        }
        for (ExportMaterial material : materials) {
            if (material != null) {
                return material;
            }
        }
        return null;
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

    private PatchVertexLighting buildPatchVertexLighting(PatchDefinition patch, double x, double y, double z,
            BlockStep faceStep, MapIterator map, int blockId, ExportMaterial material) {
        int vertexCount = (patch.uplusvmax <= 1.0000001) ? 3 : 4;
        if ((material != null) && material.isSolidColor()) {
            return buildZoomoutPatchLighting(vertexCount, material);
        }
        ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(patch, x, y, z, 0);
        float dayLightScale = computeLightScale(blockId, faceStep, map, material, false);
        float nightLightScale = computeLightScale(blockId, faceStep, map, material, true);
        float[] colors = new float[vertexCount * 3];
        float[] nightLights = (lightingMode == GLBExport.LightingMode.BOTH) ? new float[vertexCount] : null;
        for (int i = 0; i < vertexCount; i++) {
            BlockStep[] neighborSteps = getVertexNeighborSteps(faceStep, x, y, z, geometry.xyz, i);
            float dayNeighbor1 = sampleNeighborLightScale(neighborSteps[0], faceStep, map, dayLightScale, false);
            float dayNeighbor2 = sampleNeighborLightScale(neighborSteps[1], faceStep, map, dayLightScale, false);
            float dayDiagonal =
                    sampleNeighborLightScale(neighborSteps[0], neighborSteps[1], faceStep, map, dayLightScale, false);
            float smoothedDay = (dayLightScale + dayNeighbor1 + dayNeighbor2 + dayDiagonal) * 0.25F;
            float primaryLight = smoothedDay;
            float smoothedNight = 0.0F;
            if (lightingMode != GLBExport.LightingMode.DAY) {
                float nightNeighbor1 = sampleNeighborLightScale(neighborSteps[0], faceStep, map, nightLightScale, true);
                float nightNeighbor2 = sampleNeighborLightScale(neighborSteps[1], faceStep, map, nightLightScale, true);
                float nightDiagonal = sampleNeighborLightScale(neighborSteps[0], neighborSteps[1], faceStep, map,
                        nightLightScale, true);
                smoothedNight = (nightLightScale + nightNeighbor1 + nightNeighbor2 + nightDiagonal) * 0.25F;
                if (lightingMode == GLBExport.LightingMode.NIGHT) {
                    primaryLight = smoothedNight;
                } else {
                    nightLights[i] = smoothedNight;
                }
            }
            int off = i * 3;
            colors[off] = primaryLight;
            colors[off + 1] = primaryLight;
            colors[off + 2] = primaryLight;
        }
        return new PatchVertexLighting(colors, nightLights);
    }

    private PatchVertexLighting buildZoomoutPatchLighting(int vertexCount, ExportMaterial material) {
        float[] colors = new float[vertexCount * 3];
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

    private PatchVertexLighting buildZoomoutPolygonLighting(int vertexCount, ExportMaterial material) {
        return buildZoomoutPatchLighting(vertexCount, material);
    }

    private BlockStep[] getVertexNeighborSteps(BlockStep faceStep, double blockX, double blockY, double blockZ,
            double[] xyz, int vertexIndex) {
        int off = vertexIndex * 3;
        double localX = xyz[off] - blockX;
        double localY = xyz[off + 1] - blockY;
        double localZ = xyz[off + 2] - blockZ;
        switch (faceStep) {
            case X_PLUS:
            case X_MINUS:
                return new BlockStep[] { (localY >= 0.5) ? BlockStep.Y_PLUS : BlockStep.Y_MINUS,
                        (localZ >= 0.5) ? BlockStep.Z_PLUS : BlockStep.Z_MINUS };
            case Y_PLUS:
            case Y_MINUS:
                return new BlockStep[] { (localX >= 0.5) ? BlockStep.X_PLUS : BlockStep.X_MINUS,
                        (localZ >= 0.5) ? BlockStep.Z_PLUS : BlockStep.Z_MINUS };
            case Z_PLUS:
            case Z_MINUS:
            default:
                return new BlockStep[] { (localX >= 0.5) ? BlockStep.X_PLUS : BlockStep.X_MINUS,
                        (localY >= 0.5) ? BlockStep.Y_PLUS : BlockStep.Y_MINUS };
        }
    }

    private float sampleNeighborLightScale(BlockStep neighborStep, BlockStep faceStep, MapIterator map, float fallback,
            boolean emittedOnly) {
        if (!isStepYInBounds(map, neighborStep)) {
            return fallback;
        }
        map.stepPosition(neighborStep);
        try {
            int neighborBlockId = map.getBlockTypeID();
            return computeLightScale(neighborBlockId, faceStep, map, null, emittedOnly);
        } finally {
            map.unstepPosition(neighborStep);
        }
    }

    private float sampleNeighborLightScale(BlockStep neighborStep0, BlockStep neighborStep1, BlockStep faceStep,
            MapIterator map, float fallback, boolean emittedOnly) {
        if (!isStepYInBounds(map, neighborStep0)) {
            return fallback;
        }
        map.stepPosition(neighborStep0);
        map.stepPosition(neighborStep1);
        try {
            if (!isCurrentYInBounds(map)) {
                return fallback;
            }
            int neighborBlockId = map.getBlockTypeID();
            return computeLightScale(neighborBlockId, faceStep, map, null, emittedOnly);
        } finally {
            map.unstepPosition(neighborStep1);
            map.unstepPosition(neighborStep0);
        }
    }

    private boolean isStepYInBounds(MapIterator map, BlockStep step) {
        int y = map.getY();
        if (step == BlockStep.Y_PLUS) {
            y++;
        } else if (step == BlockStep.Y_MINUS) {
            y--;
        }
        return (y >= 0) && (y < map.getWorldHeight());
    }

    private boolean isCurrentYInBounds(MapIterator map) {
        int y = map.getY();
        return (y >= 0) && (y < map.getWorldHeight());
    }

    private float computeLightScale(int blockId, BlockStep faceStep, MapIterator map, ExportMaterial material,
            boolean emittedOnly) {
        if ((material != null) && material.isEmissive()) {
            return 1.0F;
        }
        LightLevels ll = new LightLevels();
        sampleFaceLightLevels(blockId, faceStep, map, ll);
        int lightLevel = emittedOnly ? ll.emitted : Math.max(ll.sky, ll.emitted);
        return toLightScale(lightLevel);
    }

    private float toLightScale(int lightLevel) {
        if ((brightnessTable != null) && (lightLevel >= 0) && (lightLevel < brightnessTable.length)) {
            return Math.max(0.0F, Math.min(1.0F, brightnessTable[lightLevel] / 256.0F));
        }
        return Math.max(0.0F, Math.min(1.0F, lightLevel / 15.0F));
    }

    private void sampleFaceLightLevels(int blockId, BlockStep faceStep, MapIterator map, LightLevels ll) {
        TexturePack.BlockTransparency transparency = TexturePack.HDTextureMap.getTransparency(blockId);
        if ((faceStep == null) || (transparency != BlockTransparency.OPAQUE)
                && (transparency != BlockTransparency.SEMITRANSPARENT)) {
            ll.sky = map.getBlockSkyLight();
            ll.emitted = map.getBlockEmittedLight();
            return;
        }
        if (transparency == BlockTransparency.SEMITRANSPARENT) {
            sampleSemitransparentLightLevels(map, ll, false);
            return;
        }

        map.stepPosition(faceStep.opposite());
        try {
            int neighborType = map.getBlockTypeID();
            if (TexturePack.HDTextureMap.getTransparency(neighborType) == BlockTransparency.SEMITRANSPARENT) {
                sampleSemitransparentLightLevels(map, ll, true);
            } else {
                ll.sky = map.getBlockSkyLight();
                ll.emitted = map.getBlockEmittedLight();
            }
        } finally {
            map.unstepPosition(faceStep.opposite());
        }
    }

    private void sampleSemitransparentLightLevels(MapIterator map, LightLevels ll, boolean subtract) {
        int emitted = 0;
        int sky = 0;
        for (BlockStep step : SEMI_STEPS) {
            map.stepPosition(step);
            try {
                int emittedLight = map.getBlockEmittedLight();
                if (subtract && (emittedLight > 0)) {
                    emittedLight--;
                }
                if (emittedLight > emitted) {
                    emitted = emittedLight;
                }
                int skyLight = map.getBlockSkyLight();
                if (subtract && (step != BlockStep.Y_PLUS) && (skyLight > 0)) {
                    skyLight--;
                }
                if (skyLight > sky) {
                    sky = skyLight;
                }
            } finally {
                map.unstepPosition(step);
            }
        }
        ll.emitted = emitted;
        ll.sky = sky;
    }

    private BlockStep getVisiblePatchStep(PatchDefinition patch) {
        BlockStep step = patch.step;
        if (step == null) {
            return BlockStep.Y_MINUS;
        }
        switch (patch.sidevis) {
            case BOTTOM:
                return step;
            case TOP:
            case BOTH:
            case FLIP:
            default:
                return step.opposite();
        }
    }

    private ExportMaterial[][] resolveMaterials(int blockId, int blockData, int renderData, MapIterator map,
            int[] textureIndexes, BlockStep[] steps, CustomRendererData customRenderData,
            boolean allowLegacyTopBottomRotationCorrection) {
        if (shader instanceof TexturePackHDShader) {
            return ((TexturePackHDShader) shader).getCurrentBlockExportMaterials(blockId, blockData, renderData, map,
                    textureIndexes, steps, customRenderData, allowLegacyTopBottomRotationCorrection);
        }
        if (shader != null) {
            String[] materials = shader.getCurrentBlockMaterials(blockId, blockData, renderData, map, textureIndexes, steps);
            ExportMaterial[][] resolved = new ExportMaterial[materials.length][];
            for (int i = 0; i < materials.length; i++) {
                ExportMaterial material = ExportMaterial.fromLegacyString(materials[i]);
                if (material != null) {
                    resolved[i] = new ExportMaterial[] { material };
                }
            }
            return resolved;
        }
        return EMPTY_MATERIALS;
    }

    private void collapseLayeredMaterials(ExportMaterial[][] materials) {
        if (materials == null) {
            return;
        }
        for (int i = 0; i < materials.length; i++) {
            materials[i] = collapseLayeredMaterials(materials[i]);
        }
    }

    private ExportMaterial[] collapseLayeredMaterials(ExportMaterial[] materials) {
        if ((materials == null) || (materials.length <= 1)) {
            return materials;
        }
        ArrayList<ExportMaterial> filtered = new ArrayList<ExportMaterial>(materials.length);
        for (ExportMaterial material : materials) {
            if (material != null) {
                filtered.add(material);
            }
        }
        if (filtered.isEmpty()) {
            return null;
        }
        if (filtered.size() == 1) {
            return new ExportMaterial[] { filtered.get(0) };
        }

        ExportMaterial emissiveLayer = null;
        int bakeStart = 0;
        int bakeEnd = filtered.size();
        ExportMaterial topLayer = filtered.get(0);
        if (topLayer.isEmissive()) {
            emissiveLayer = topLayer;
            bakeStart = 1;
        }

        ArrayList<ExportMaterial> collapsed = new ArrayList<ExportMaterial>(2);
        if (bakeEnd > bakeStart) {
            int bakeCount = bakeEnd - bakeStart;
            collapsed.add(
                    ExportMaterial.bakeLayers(filtered.subList(bakeStart, bakeEnd).toArray(new ExportMaterial[bakeCount])));
        }
        if (emissiveLayer != null) {
            collapsed.add(emissiveLayer);
        }
        return collapsed.toArray(new ExportMaterial[collapsed.size()]);
    }

    private static boolean getSubblock(short[] mod, int x, int y, int z) {
        if ((x >= 0) && (x < MODELSCALE) && (y >= 0) && (y < MODELSCALE) && (z >= 0) && (z < MODELSCALE)) {
            return mod[MODELSCALE * MODELSCALE * y + MODELSCALE * z + x] != 0;
        }
        return false;
    }

    private int scanX(short[] model, int x, int y, int z) {
        int xLength = 0;
        while (getSubblock(model, x + xLength, y, z)) {
            xLength++;
        }
        return xLength;
    }

    private int scanZ(short[] model, int x, int y, int z, int xLength) {
        int zLength = 0;
        while (scanX(model, x, y, z + zLength) >= xLength) {
            zLength++;
        }
        return zLength;
    }

    private int scanY(short[] model, int x, int y, int z, int xLength, int zLength) {
        int yLength = 0;
        while (scanZ(model, x, y + yLength, z, xLength) >= zLength) {
            yLength++;
        }
        return yLength;
    }

    private void addSubblock(short[] model, int x, int y, int z, ArrayList<RenderPatch> list) {
        int xLength = scanX(model, x, y, z);
        int zLength = scanZ(model, x, y, z, xLength);
        int yLength = scanY(model, x, y, z, xLength, zLength);

        CustomRenderer.addBox(HDBlockModels.getPatchDefinitionFactory(), list, BLKSIZE * x, BLKSIZE * (x + xLength),
                BLKSIZE * y, BLKSIZE * (y + yLength), BLKSIZE * z, BLKSIZE * (z + zLength), HDBlockModels.boxPatchList);

        for (int xx = 0; xx < xLength; xx++) {
            for (int yy = 0; yy < yLength; yy++) {
                for (int zz = 0; zz < zLength; zz++) {
                    model[MODELSCALE * MODELSCALE * (y + yy) + MODELSCALE * (z + zz) + (x + xx)] = 0;
                }
            }
        }
    }

    private PatchDefinition[] getScaledModelAsPatches(short[] model) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        short[] copy = Arrays.copyOf(model, model.length);
        for (int y = 0; y < MODELSCALE; y++) {
            for (int z = 0; z < MODELSCALE; z++) {
                for (int x = 0; x < MODELSCALE; x++) {
                    if (getSubblock(copy, x, y, z)) {
                        addSubblock(copy, x, y, z, list);
                    }
                }
            }
        }

        PatchDefinition[] patches = new PatchDefinition[list.size()];
        for (int i = 0; i < patches.length; i++) {
            patches[i] = (PatchDefinition) list.get(i);
        }
        return patches;
    }
}
