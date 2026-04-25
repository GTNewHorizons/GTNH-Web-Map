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
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;

public class BlockModelExporter {
    private static final int MODELSCALE = 16;
    private static final double BLKSIZE = 1.0 / (double) MODELSCALE;
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
                        if (((chunkX + i) <= maxChunkX) && ((chunkZ + j) <= maxChunkZ) && ((chunkX + i) >= minChunkX)
                                && ((chunkZ + j) >= minChunkZ)) {
                            requiredChunks.add(new DynmapChunk(chunkX + i, chunkZ + j));
                        }
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
        BlockStep[] steps = BlockStep.values();
        int[] textureIndexes = null;
        int blockData = map.getBlockData();
        int renderData = HDBlockModels.getBlockRenderData(blockId, map);
        CustomRendererData customRenderData = null;

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
            steps = new BlockStep[patches.length];
            textureIndexes = new int[patches.length];
            for (int i = 0; i < textureIndexes.length; i++) {
                textureIndexes[i] = ((PatchDefinition) patches[i]).getTextureIndex();
                steps[i] = ((PatchDefinition) patches[i]).step;
            }
        } else {
            short[] scaledModel = models.getScaledModel(blockId, blockData, renderData);
            if (scaledModel != null) {
                patches = getScaledModelAsPatches(scaledModel);
                steps = new BlockStep[patches.length];
                textureIndexes = new int[patches.length];
                for (int i = 0; i < patches.length; i++) {
                    PatchDefinition patch = (PatchDefinition) patches[i];
                    steps[i] = patch.step;
                    textureIndexes[i] = patch.getTextureIndex();
                }
            }
        }

        sink.setBlock(blockId, blockData);
        ExportMaterial[][] materials =
                resolveMaterials(blockId, blockData, renderData, map, textureIndexes, steps, customRenderData);

        if (patches != null) {
            for (int i = 0; i < patches.length; i++) {
                ExportMaterial[] patchMaterials = ((materials.length > i) && (materials[i] != null)) ? materials[i] : null;
                if (patchMaterials == null) {
                    continue;
                }
                for (ExportMaterial material : patchMaterials) {
                    sink.addPatch((PatchDefinition) patches[i], map.getX(), map.getY(), map.getZ(), material);
                }
            }
        } else {
            boolean opaque = TexturePack.HDTextureMap.getTransparency(blockId) == BlockTransparency.OPAQUE;
            for (int face = 0; face < 6; face++) {
                int adjacentBlock = map.getBlockTypeIDAt(BlockStep.oppositeValues[face]);
                if ((!opaque) || (adjacentBlock == 0) || edgeBits[face]
                        || (TexturePack.HDTextureMap.getTransparency(adjacentBlock) != BlockTransparency.OPAQUE)) {
                    ExportMaterial[] faceMaterials =
                            ((materials.length > face) && (materials[face] != null)) ? materials[face] : null;
                    if (faceMaterials == null) {
                        continue;
                    }
                    for (ExportMaterial material : faceMaterials) {
                        sink.addPatch(defaultPatches[face], map.getX(), map.getY(), map.getZ(), material);
                    }
                }
            }
        }
    }

    private ExportMaterial[][] resolveMaterials(int blockId, int blockData, int renderData, MapIterator map,
            int[] textureIndexes, BlockStep[] steps, CustomRendererData customRenderData) {
        if (shader instanceof TexturePackHDShader) {
            return ((TexturePackHDShader) shader).getCurrentBlockExportMaterials(blockId, blockData, renderData, map,
                    textureIndexes, steps, customRenderData);
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
