package org.dynmap.exporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.HDBlockModels.CustomBlockModel;
import org.dynmap.hdmap.HDBlockModels.HDScaledBlockModels;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.TexturePack.BlockTransparency;
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

public class BlockModelExporter {
    protected static final class PatchVertexLighting {
        final float[] vertexColors;
        final float[] nightVertexLights;

        PatchVertexLighting(float[] vertexColors, float[] nightVertexLights) {
            this.vertexColors = vertexColors;
            this.nightVertexLights = nightVertexLights;
        }
    }

    protected static final class ResolvedBlockData {
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

    private static final int MODELSCALE = 16;
    private static final double BLKSIZE = 1.0 / (double) MODELSCALE;
    private static final BlockStep[] SEMI_STEPS =
            { BlockStep.Y_PLUS, BlockStep.X_MINUS, BlockStep.X_PLUS, BlockStep.Z_MINUS, BlockStep.Z_PLUS };
    private static final double[][] BOX_PATCH_POINTS = {
            { 0, 0, 0, 1, 0, 0, 0, 0, 1 },
            { 0, 1, 1, 1, 1, 1, 0, 1, 0 },
            { 1, 0, 0, 0, 0, 0, 1, 1, 0 },
            { 0, 0, 1, 1, 0, 1, 0, 1, 1 },
            { 0, 0, 0, 0, 0, 1, 0, 1, 0 },
            { 1, 0, 1, 1, 0, 0, 1, 1, 1 }
    };
    private static final ExportMaterial[][] EMPTY_MATERIALS = new ExportMaterial[0][];

    private final DynmapWorld world;
    private final DynmapCore core;
    private final HDShader shader;
    private final PatchDefinition[] defaultPatches;
    private final HDScaledBlockModels models;
    private final int[] brightnessTable;
    private boolean cullExportRegionEdges;
    private GLBExport.LightingMode lightingMode = GLBExport.LightingMode.DAY;
    private BlockModelExportMode exportMode = BlockModelExportMode.FULL;
    private int lodZoomLevel;
    private int simplifiedMinSkyLight = 7;

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BlockModelExporter(DynmapWorld world, DynmapCore core, HDShader shader) {
        this.world = world;
        this.core = core;
        this.shader = shader;
        this.defaultPatches = new PatchDefinition[6];

        PatchDefinitionFactory factory = HDBlockModels.getPatchDefinitionFactory();
        for (BlockStep step : BlockStep.values()) {
            double[] p = BOX_PATCH_POINTS[step.getFaceEntered()];
            int ordinal = step.ordinal();
            defaultPatches[ordinal] = factory.getPatch(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], 0, 1, 0,
                    1, 100, SideVisible.TOP, ordinal);
        }
        models = HDBlockModels.getModelsForScale(MODELSCALE);
        brightnessTable = world.getBrightnessTable();
    }

    public void setRenderBounds(int minx, int miny, int minz, int maxx, int maxy, int maxz) {
        if (minx < maxx) {
            minX = minx;
            maxX = maxx;
        } else {
            minX = maxx;
            maxX = minx;
        }
        if (miny < maxy) {
            minY = miny;
            maxY = maxy;
        } else {
            minY = maxy;
            maxY = miny;
        }
        if (minz < maxz) {
            minZ = minz;
            maxZ = maxz;
        } else {
            minZ = maxz;
            maxZ = minz;
        }
        if (minY < 0) {
            minY = 0;
        }
        if (maxY >= world.worldheight) {
            maxY = world.worldheight - 1;
        }
    }

    public void setCullExportRegionEdges(boolean cullExportRegionEdges) {
        this.cullExportRegionEdges = cullExportRegionEdges;
    }

    public void setLightingMode(GLBExport.LightingMode lightingMode) {
        this.lightingMode = (lightingMode == null) ? GLBExport.LightingMode.DAY : lightingMode;
    }

    public void setExportMode(BlockModelExportMode exportMode) {
        this.exportMode = (exportMode == null) ? BlockModelExportMode.FULL : exportMode;
    }

    public void setLodZoomLevel(int lodZoomLevel) {
        this.lodZoomLevel = Math.max(0, lodZoomLevel);
    }

    public void setSimplifiedMinSkyLight(int simplifiedMinSkyLight) {
        this.simplifiedMinSkyLight = Math.max(0, Math.min(15, simplifiedMinSkyLight));
    }

    protected final DynmapWorld getWorld() {
        return world;
    }

    protected final DynmapCore getCore() {
        return core;
    }

    protected final HDShader getShader() {
        return shader;
    }

    protected final int getMinY() {
        return minY;
    }

    protected final int getMaxY() {
        return maxY;
    }

    protected final int getMinX() {
        return minX;
    }

    protected final int getMaxX() {
        return maxX;
    }

    protected final int getMinZ() {
        return minZ;
    }

    protected final int getMaxZ() {
        return maxZ;
    }

    protected final int getSimplifiedMinSkyLight() {
        return simplifiedMinSkyLight;
    }

    protected final boolean isCullExportRegionEdges() {
        return cullExportRegionEdges;
    }

    protected final GLBExport.LightingMode getLightingMode() {
        return lightingMode;
    }

    private void copyConfigurationTo(BlockModelExporter exporter) {
        exporter.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
        exporter.setCullExportRegionEdges(cullExportRegionEdges);
        exporter.setLightingMode(lightingMode);
        exporter.setExportMode(exportMode);
        exporter.setLodZoomLevel(lodZoomLevel);
        exporter.setSimplifiedMinSkyLight(simplifiedMinSkyLight);
    }

    public void export(BlockModelExportSink sink) throws IOException {
        List<DynmapChunk> requiredChunks = new ArrayList<DynmapChunk>();
        int minChunkX = minX >> 4;
        int maxChunkX = (maxX + 15) >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = (maxZ + 15) >> 4;
        boolean[] edgeBits = new boolean[6];

        boolean needBiome = (shader == null) || shader.isBiomeDataNeeded();
        boolean needRawBiome = (shader != null) && shader.isRawBiomeDataNeeded();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX += 4) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ += 4) {
                requiredChunks.clear();
                for (int i = -1; i < 5; i++) {
                    for (int j = -1; j < 5; j++) {
                        requiredChunks.add(new DynmapChunk(chunkX + i, chunkZ + j));
                    }
                }

                MapChunkCache cache =
                        core.getServer().createMapChunkCache(world, requiredChunks, true, false, needBiome, needRawBiome);
                if (cache == null) {
                    throw new IOException("Error loading chunk cache");
                }
                exportLoadedRegion(cache, sink, Math.max(minX, chunkX * 16), Math.min(maxX, (chunkX * 16) + 63),
                        Math.max(minZ, chunkZ * 16), Math.min(maxZ, (chunkZ * 16) + 63), edgeBits);
            }
        }
    }

    public void export(MapChunkCache cache, BlockModelExportSink sink) throws IOException {
        if (cache == null) {
            throw new IOException("Error loading chunk cache");
        }
        exportLoadedRegion(cache, sink, minX, maxX, minZ, maxZ, new boolean[6]);
    }

    private void exportLoadedRegion(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ, boolean[] edgeBits) throws IOException {
        switch (exportMode) {
            case SIMPLIFIED:
                BlockModelSimplifiedExporter simplifiedExporter = new BlockModelSimplifiedExporter(world, core, shader);
                copyConfigurationTo(simplifiedExporter);
                simplifiedExporter.exportSimplified(cache, sink, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
                return;
            case ZOOMOUT:
                new BlockModelLodExporter(this, world, shader, minY, maxY, cullExportRegionEdges, lightingMode,
                        simplifiedMinSkyLight)
                        .exportZoomout(cache, sink, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ, lodZoomLevel);
                return;
            case FULL:
            default:
                break;
        }
        MapIterator iterator = cache.getIterator(rangeMinX, minY, rangeMinZ);
        for (int x = rangeMinX; x <= rangeMaxX; x++) {
            edgeBits[BlockStep.X_PLUS.ordinal()] = (x == minX);
            edgeBits[BlockStep.X_MINUS.ordinal()] = (x == maxX);

            for (int z = rangeMinZ; z <= rangeMaxZ; z++) {
                edgeBits[BlockStep.Z_PLUS.ordinal()] = (z == minZ);
                edgeBits[BlockStep.Z_MINUS.ordinal()] = (z == maxZ);

                iterator.initialize(x, minY, z);
                sink.setChunk(x >> 4, z >> 4);

                edgeBits[BlockStep.Y_MINUS.ordinal()] = true;
                edgeBits[BlockStep.Y_PLUS.ordinal()] = false;
                int blockId = iterator.getBlockTypeID();
                if (blockId > 0) {
                    handleBlock(blockId, iterator, edgeBits, sink);
                }

                edgeBits[BlockStep.Y_MINUS.ordinal()] = false;
                for (int y = minY + 1; y < maxY; y++) {
                    iterator.setY(y);
                    blockId = iterator.getBlockTypeID();
                    if (blockId > 0) {
                        handleBlock(blockId, iterator, edgeBits, sink);
                    }
                }

                edgeBits[BlockStep.Y_PLUS.ordinal()] = true;
                iterator.setY(maxY);
                blockId = iterator.getBlockTypeID();
                if (blockId > 0) {
                    handleBlock(blockId, iterator, edgeBits, sink);
                }
            }
        }
    }

    private void handleBlock(int blockId, MapIterator map, boolean[] edgeBits, BlockModelExportSink sink)
            throws IOException {
        ResolvedBlockData block = resolveBlock(map, blockId);
        sink.setBlock(blockId, map.getBlockData());
        if (block.patches != null) {
            for (int i = 0; i < block.patches.length; i++) {
                PatchDefinition patch = block.patches[i];
                ExportMaterial[] patchMaterials =
                        ((block.materials.length > i) && (block.materials[i] != null)) ? block.materials[i] : null;
                if ((patchMaterials == null) || shouldCullPatchAgainstOpaqueNeighbor(patch, block.steps[i], map, edgeBits)) {
                    continue;
                }
                for (ExportMaterial material : patchMaterials) {
                    PatchVertexLighting lighting =
                            buildPatchVertexLighting(patch, map.getX(), map.getY(), map.getZ(), block.steps[i], map, blockId,
                                    material);
                    sink.addPatch(patch, map.getX(), map.getY(), map.getZ(), material,
                            lighting.vertexColors, lighting.nightVertexLights);
                }
            }
        } else {
            boolean opaque = TexturePack.HDTextureMap.getTransparency(blockId) == BlockTransparency.OPAQUE;
            for (int face = 0; face < 6; face++) {
                int adjacentBlock = map.getBlockTypeIDAt(BlockStep.oppositeValues[face]);
                if ((!opaque) || (adjacentBlock == 0) || edgeBits[face]
                        || (TexturePack.HDTextureMap.getTransparency(adjacentBlock) != BlockTransparency.OPAQUE)) {
                    ExportMaterial[] faceMaterials =
                            ((block.materials.length > face) && (block.materials[face] != null)) ? block.materials[face] : null;
                    if (faceMaterials == null) {
                        continue;
                    }
                    for (ExportMaterial material : faceMaterials) {
                        PatchVertexLighting lighting = buildPatchVertexLighting(defaultPatches[face], map.getX(), map.getY(),
                                map.getZ(), block.steps[face], map, blockId, material);
                        sink.addPatch(defaultPatches[face], map.getX(), map.getY(), map.getZ(), material,
                                lighting.vertexColors, lighting.nightVertexLights);
                    }
                }
            }
        }
    }

    protected boolean hasRenderableGeometry(MapIterator map, int blockId) throws IOException {
        return (blockId > 0) && (getAnySurfaceMaterial(resolveBlock(map, blockId)) != null);
    }

    void emitCurrentBlock(MapIterator map, BlockModelExportSink sink) throws IOException {
        int blockId = map.getBlockTypeID();
        if (blockId <= 0) {
            return;
        }
        boolean[] edgeBits = new boolean[6];
        fillEdgeBits(map.getX(), map.getY(), map.getZ(), edgeBits);
        handleBlock(blockId, map, edgeBits, sink);
    }

    private void fillEdgeBits(int x, int y, int z, boolean[] edgeBits) {
        edgeBits[BlockStep.X_PLUS.ordinal()] = (x == minX);
        edgeBits[BlockStep.X_MINUS.ordinal()] = (x == maxX);
        edgeBits[BlockStep.Z_PLUS.ordinal()] = (z == minZ);
        edgeBits[BlockStep.Z_MINUS.ordinal()] = (z == maxZ);
        edgeBits[BlockStep.Y_MINUS.ordinal()] = (y == minY);
        edgeBits[BlockStep.Y_PLUS.ordinal()] = (y == maxY);
    }

    protected PatchVertexLighting buildPatchVertexLighting(PatchDefinition patch, double x, double y, double z,
            BlockStep faceStep,
            MapIterator map, int blockId, ExportMaterial material) {
        int vertexCount = (patch.uplusvmax <= 1.0000001) ? 3 : 4;
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

    protected BlockStep getVisiblePatchStep(PatchDefinition patch) {
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

    protected boolean shouldCullPatchAgainstOpaqueNeighbor(PatchDefinition patch, BlockStep faceStep, MapIterator map,
            boolean[] edgeBits) {
        BlockStep outwardStep = getVisibleBoundaryStep(patch, faceStep);
        if (outwardStep == null) {
            return false;
        }
        if (edgeBits[faceStep.ordinal()] && !cullExportRegionEdges) {
            return false;
        }
        if (!isStepYInBounds(map, outwardStep)) {
            return false;
        }
        int adjacentBlock = map.getBlockTypeIDAt(outwardStep);

        if(adjacentBlock <= 0)
            return false;

        return TexturePack.HDTextureMap.getTransparency(adjacentBlock) == BlockTransparency.OPAQUE;
    }

    private BlockStep getVisibleBoundaryStep(PatchDefinition patch, BlockStep faceStep) {
        if (faceStep == null) {
            return null;
        }
        switch (patch.sidevis) {
            case TOP:
            case BOTTOM:
                break;
            case BOTH:
            case FLIP:
            default:
                return null;
        }
        BlockStep outwardStep = faceStep.opposite();
        return isPatchOnBoundary(patch, outwardStep) ? outwardStep : null;
    }

    private boolean isPatchOnBoundary(PatchDefinition patch, BlockStep step) {
        final double epsilon = 1.0E-9;
        switch (step) {
            case X_MINUS:
                return areAllEqual(patch.x0, patch.xu, patch.xv, 0.0, epsilon);
            case X_PLUS:
                return areAllEqual(patch.x0, patch.xu, patch.xv, 1.0, epsilon);
            case Y_MINUS:
                return areAllEqual(patch.y0, patch.yu, patch.yv, 0.0, epsilon);
            case Y_PLUS:
                return areAllEqual(patch.y0, patch.yu, patch.yv, 1.0, epsilon);
            case Z_MINUS:
                return areAllEqual(patch.z0, patch.zu, patch.zv, 0.0, epsilon);
            case Z_PLUS:
                return areAllEqual(patch.z0, patch.zu, patch.zv, 1.0, epsilon);
            default:
                return false;
        }
    }

    private boolean areAllEqual(double v0, double v1, double v2, double expected, double epsilon) {
        return (Math.abs(v0 - expected) <= epsilon) && (Math.abs(v1 - expected) <= epsilon)
                && (Math.abs(v2 - expected) <= epsilon);
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

    protected ExportMaterial[][] resolveMaterials(int blockId, int blockData, int renderData, MapIterator map,
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

    protected void collapseLayeredMaterials(ExportMaterial[][] materials) {
        if (materials == null) {
            return;
        }
        for (int i = 0; i < materials.length; i++) {
            materials[i] = collapseLayeredMaterials(materials[i]);
        }
    }

    protected ExportMaterial[] collapseLayeredMaterials(ExportMaterial[] materials) {
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

    private void addSubblock(short[] model, int x, int y, int z, List<RenderPatch> list) {
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

    protected PatchDefinition[] getScaledModelAsPatches(short[] model) {
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

    protected ResolvedBlockData resolveBlock(MapIterator map, int blockId) {
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
        return new ResolvedBlockData(blockId, resolvedPatches, steps, materials, computeMaxLocalY(resolvedPatches));
    }

    protected ExportMaterial getAnySurfaceMaterial(ResolvedBlockData block) throws IOException {
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

    protected ExportMaterial getFirstSolidMaterial(ExportMaterial[] materials) throws IOException {
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
        return maxLocalY;
    }
}
