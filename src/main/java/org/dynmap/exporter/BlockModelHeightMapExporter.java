package org.dynmap.exporter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dynmap.DynmapChunk;
import org.dynmap.Color;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.HDShader;
import org.dynmap.hdmap.IsoHDPerspective;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.TexturePack.ExportedTextureData;
import org.dynmap.hdmap.TexturePackHDShader;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.ImageIOManager;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.Vector3D;

final class BlockModelHeightMapExporter extends AbstractBlockModelExporter {
    private static final int HEIGHT_MAP_QUARTER_COUNT = 4;
    private static final int[] DOUBLE_SIDED_QUAD_INDICES = { 0, 1, 2, 0, 2, 3, 3, 2, 1, 3, 1, 0 };

    private static final class ColumnData {
        final boolean hasSurface;
        final double topY;
        final ExportMaterial topMaterial;
        final float dayLight;
        final float nightLight;

        ColumnData(boolean hasSurface, double topY, ExportMaterial topMaterial, float dayLight, float nightLight) {
            this.hasSurface = hasSurface;
            this.topY = topY;
            this.topMaterial = topMaterial;
            this.dayLight = dayLight;
            this.nightLight = nightLight;
        }
    }

    private static final class VertexData {
        final double y;
        final boolean valid;
        final float dayLight;
        final float nightLight;

        VertexData(double y, boolean valid, float dayLight, float nightLight) {
            this.y = y;
            this.valid = valid;
            this.dayLight = dayLight;
            this.nightLight = nightLight;
        }
    }

    private final DynmapCore core;
    private final HDShader shader;
    private final TexturePack exportTexturePack;
    private final ExportMaterialColorResolver colorResolver;
    private String heightMapTextureMap;
    private int heightMapTextureDetail = 1;

    BlockModelHeightMapExporter(DynmapWorld world, DynmapCore core, HDShader shader) {
        super(world, core, shader);
        this.core = core;
        this.shader = shader;
        this.exportTexturePack =
                (shader instanceof TexturePackHDShader) ? ((TexturePackHDShader) shader).getTexturePackForExport() : null;
        this.colorResolver = new ExportMaterialColorResolver(exportTexturePack);
    }

    void setHeightMapTextureMap(String heightMapTextureMap) {
        if ((heightMapTextureMap != null) && (heightMapTextureMap.trim().length() == 0)) {
            this.heightMapTextureMap = null;
        } else {
            this.heightMapTextureMap = heightMapTextureMap;
        }
    }

    void setHeightMapTextureDetail(int heightMapTextureDetail) {
        this.heightMapTextureDetail = Math.max(1, heightMapTextureDetail);
    }

    @Override
    public void export(BlockModelExportSink sink) throws IOException {
        MapChunkCache cache = createFullRegionCache();
        if (cache == null) {
            throw new IOException("Error loading chunk cache");
        }
        export(cache, sink);
    }

    @Override
    public void export(MapChunkCache cache, BlockModelExportSink sink) throws IOException {
        if (cache == null) {
            throw new IOException("Error loading chunk cache");
        }
        exportHeightMap(cache, sink, getMinX(), getMaxX(), getMinZ(), getMaxZ());
    }

    @Override
    protected void exportLoadedRegion(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ, boolean[] edgeBits) throws IOException {
        exportHeightMap(cache, sink, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
    }

    private MapChunkCache createFullRegionCache() {
        List<DynmapChunk> requiredChunks = new ArrayList<DynmapChunk>();
        int minChunkX = getMinX() >> 4;
        int maxChunkX = getMaxX() >> 4;
        int minChunkZ = getMinZ() >> 4;
        int maxChunkZ = getMaxZ() >> 4;
        boolean needBiome = (shader == null) || shader.isBiomeDataNeeded();
        boolean needRawBiome = (shader != null) && shader.isRawBiomeDataNeeded();
        for (int chunkX = minChunkX - 1; chunkX <= maxChunkX + 1; chunkX++) {
            for (int chunkZ = minChunkZ - 1; chunkZ <= maxChunkZ + 1; chunkZ++) {
                requiredChunks.add(new DynmapChunk(chunkX, chunkZ));
            }
        }
        return core.getServer().createMapChunkCache(getWorld(), requiredChunks, true, false, needBiome, needRawBiome);
    }

    private void exportHeightMap(MapChunkCache cache, BlockModelExportSink sink, int rangeMinX, int rangeMaxX, int rangeMinZ,
            int rangeMaxZ) throws IOException {
        int columnMinX = rangeMinX - 1;
        int columnMaxX = rangeMaxX + 1;
        int columnMinZ = rangeMinZ - 1;
        int columnMaxZ = rangeMaxZ + 1;
        ColumnData[] columns = collectColumnData(cache, columnMinX, columnMaxX, columnMinZ, columnMaxZ);
        BufferedImage textureImage = buildTextureImage(columns, columnMinX, columnMaxX, columnMinZ, columnMaxZ, rangeMinX,
                rangeMaxX, rangeMinZ, rangeMaxZ);
        ExportedTextureData textureData = encodeTextureImage(textureImage);
        ExportMaterial[] quarterMaterials =
                buildQuarterMaterials(buildHeightMapMaterialId(rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ), textureData);
        int[][] quarterBounds = buildQuarterBounds(rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
        int minChunkX = rangeMinX >> 4;
        int maxChunkX = rangeMaxX >> 4;
        int minChunkZ = rangeMinZ >> 4;
        int maxChunkZ = rangeMaxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int chunkMinX = Math.max(rangeMinX, chunkX << 4);
            int chunkMaxX = Math.min(rangeMaxX, (chunkX << 4) + 15);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int chunkMinZ = Math.max(rangeMinZ, chunkZ << 4);
                int chunkMaxZ = Math.min(rangeMaxZ, (chunkZ << 4) + 15);
                for (int quarter = 0; quarter < HEIGHT_MAP_QUARTER_COUNT; quarter++) {
                    int meshMinX = Math.max(chunkMinX, quarterBounds[quarter][0]);
                    int meshMaxX = Math.min(chunkMaxX, quarterBounds[quarter][1]);
                    int meshMinZ = Math.max(chunkMinZ, quarterBounds[quarter][2]);
                    int meshMaxZ = Math.min(chunkMaxZ, quarterBounds[quarter][3]);
                    if ((meshMinX > meshMaxX) || (meshMinZ > meshMaxZ)) {
                        continue;
                    }
                    emitChunkHeightMesh(sink, columns, quarterMaterials[quarter], meshMinX, meshMaxX, meshMinZ, meshMaxZ, columnMinX,
                            columnMaxX, columnMinZ, columnMaxZ, rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
                }
            }
        }
    }

    private ColumnData[] collectColumnData(MapChunkCache cache, int minX, int maxX, int minZ, int maxZ) throws IOException {
        int width = (maxX - minX) + 1;
        int depth = (maxZ - minZ) + 1;
        ColumnData[] columns = new ColumnData[width * depth];
        MapIterator iterator = cache.getIterator(minX, getMaxY(), minZ);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                columns[((z - minZ) * width) + (x - minX)] = resolveTopColumn(iterator, x, z);
            }
        }
        return columns;
    }

    private ColumnData resolveTopColumn(MapIterator iterator, int x, int z) throws IOException {
        iterator.initialize(x, getMaxY(), z);
        for (int y = getMaxY(); y >= getMinY(); y--) {
            iterator.setY(y);
            int blockId = iterator.getBlockTypeID();
            if (blockId <= 0) {
                continue;
            }
            ResolvedBlockData block = resolveBlock(iterator, blockId);
            ExportMaterial topMaterial = getSurfaceMaterial(block, BlockStep.Y_PLUS);
            if (topMaterial == null) {
                topMaterial = getAnyTexturedMaterial(block);
            }
            if (topMaterial != null) {
                float dayLight = computeFaceLightScale(blockId, BlockStep.Y_MINUS, iterator, topMaterial, false);
                float nightLight = computeFaceLightScale(blockId, BlockStep.Y_MINUS, iterator, topMaterial, true);
                return new ColumnData(true, y + block.maxLocalY, topMaterial, dayLight, nightLight);
            }
        }
        return new ColumnData(false, getMinY(), null, 0.0F, 0.0F);
    }

    private BufferedImage buildTextureImage(ColumnData[] columns, int columnMinX, int columnMaxX, int columnMinZ,
            int columnMaxZ, int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) throws IOException {
        SourceMapSampler sourceSampler = resolveSourceMapSampler();
        int textureDetail = (sourceSampler != null) ? sourceSampler.getTextureDetail() : 1;
        int width = ((rangeMaxX - rangeMinX) + 1) * textureDetail;
        int depth = ((rangeMaxZ - rangeMinZ) + 1) * textureDetail;
        BufferedImage image = new BufferedImage(width, depth, BufferedImage.TYPE_INT_ARGB);
        int columnStride = getColumnStride(columnMinX, columnMaxX);
        for (int pixelZ = 0; pixelZ < depth; pixelZ++) {
            int worldBlockZ = rangeMaxZ - (pixelZ / textureDetail);
            double sampleWorldZ = (rangeMaxZ + 1) - ((pixelZ + 0.5) / textureDetail);
            for (int pixelX = 0; pixelX < width; pixelX++) {
                int worldBlockX = rangeMinX + (pixelX / textureDetail);
                double sampleWorldX = rangeMinX + ((pixelX + 0.5) / textureDetail);
                ColumnData column = columns[getColumnIndex(worldBlockX, worldBlockZ, columnMinX, columnMinZ, columnStride)];
                int argb = 0x00000000;
                if ((sourceSampler != null) && column.hasSurface) {
                    int fallbackArgb = colorResolver.computeAverageColor(column.topMaterial);
                    argb = sourceSampler.sample(sampleWorldX, column.topY, sampleWorldZ, fallbackArgb);
                } else if (column.hasSurface) {
                    double localU = sampleWorldX - worldBlockX;
                    double localV = 1.0 - (sampleWorldZ - worldBlockZ);
                    argb = colorResolver.sampleColor(column.topMaterial, localU, localV);
                }
                image.setRGB(pixelX, pixelZ, argb);
            }
        }
        return image;
    }

    private void emitChunkHeightMesh(BlockModelExportSink sink, ColumnData[] columns, ExportMaterial material, int chunkMinX,
            int chunkMaxX, int chunkMinZ, int chunkMaxZ, int columnMinX, int columnMaxX, int columnMinZ, int columnMaxZ,
            int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) throws IOException {
        int widthColumns = (rangeMaxX - rangeMinX) + 1;
        int depthColumns = (rangeMaxZ - rangeMinZ) + 1;
        int columnStride = getColumnStride(columnMinX, columnMaxX);
        int cellWidth = (chunkMaxX - chunkMinX) + 1;
        int cellDepth = (chunkMaxZ - chunkMinZ) + 1;
        int vertexWidth = (chunkMaxX - chunkMinX) + 2;
        int vertexDepth = (chunkMaxZ - chunkMinZ) + 2;
        int cornerVertexCount = vertexWidth * vertexDepth;
        int centerVertexCount = cellWidth * cellDepth;
        int totalVertexCount = cornerVertexCount + centerVertexCount;
        double[] xyz = new double[totalVertexCount * 3];
        double[] uv = new double[totalVertexCount * 2];
        VertexData[] vertices = new VertexData[totalVertexCount];
        for (int vz = 0; vz < vertexDepth; vz++) {
            int worldZ = chunkMinZ + vz;
            for (int vx = 0; vx < vertexWidth; vx++) {
                int worldX = chunkMinX + vx;
                VertexData vertex = buildVertex(columns, worldX, worldZ, columnMinX, columnMaxX, columnMinZ, columnMaxZ,
                        columnStride);
                int index = (vz * vertexWidth) + vx;
                vertices[index] = vertex;
                int xyzOffset = index * 3;
                int uvOffset = index * 2;
                xyz[xyzOffset] = worldX;
                xyz[xyzOffset + 1] = vertex.y;
                xyz[xyzOffset + 2] = worldZ;
                uv[uvOffset] = (double) (worldX - rangeMinX) / (double) widthColumns;
                uv[uvOffset + 1] = (double) (worldZ - rangeMinZ) / (double) depthColumns;
            }
        }
        for (int cz = 0; cz < cellDepth; cz++) {
            int worldZ = chunkMinZ + cz;
            for (int cx = 0; cx < cellWidth; cx++) {
                int worldX = chunkMinX + cx;
                VertexData centerVertex = buildCenterVertex(columns, worldX, worldZ, columnMinX, columnMaxX, columnMinZ,
                        columnMaxZ, columnStride);
                int index = cornerVertexCount + (cz * cellWidth) + cx;
                vertices[index] = centerVertex;
                int xyzOffset = index * 3;
                int uvOffset = index * 2;
                xyz[xyzOffset] = worldX + 0.5;
                xyz[xyzOffset + 1] = centerVertex.y;
                xyz[xyzOffset + 2] = worldZ + 0.5;
                uv[uvOffset] = (double) ((worldX + 0.5) - rangeMinX) / (double) widthColumns;
                uv[uvOffset + 1] = (double) ((worldZ + 0.5) - rangeMinZ) / (double) depthColumns;
            }
        }

        ArrayList<Integer> indexList = new ArrayList<Integer>();
        for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
            for (int x = chunkMinX; x <= chunkMaxX; x++) {
                int localX = x - chunkMinX;
                int localZ = z - chunkMinZ;
                int v00 = (localZ * vertexWidth) + localX;
                int v10 = v00 + 1;
                int v01 = ((localZ + 1) * vertexWidth) + localX;
                int v11 = v01 + 1;
                int centerIndex = cornerVertexCount + (localZ * cellWidth) + localX;
                if (!vertices[centerIndex].valid) {
                    continue;
                }
                indexList.add(Integer.valueOf(centerIndex));
                indexList.add(Integer.valueOf(v00));
                indexList.add(Integer.valueOf(v01));
                indexList.add(Integer.valueOf(centerIndex));
                indexList.add(Integer.valueOf(v01));
                indexList.add(Integer.valueOf(v11));
                indexList.add(Integer.valueOf(centerIndex));
                indexList.add(Integer.valueOf(v11));
                indexList.add(Integer.valueOf(v10));
                indexList.add(Integer.valueOf(centerIndex));
                indexList.add(Integer.valueOf(v10));
                indexList.add(Integer.valueOf(v00));
            }
        }
        if (indexList.isEmpty()) {
            return;
        }

        int[] indices = new int[indexList.size()];
        for (int i = 0; i < indexList.size(); i++) {
            indices[i] = indexList.get(i).intValue();
        }
        float[] vertexColors = buildVertexColors(vertices);
        float[] nightLights = buildNightLights(vertices);
        sink.setChunk(chunkMinX >> 4, chunkMinZ >> 4);
        sink.addTriangleMesh(xyz, uv, indices, material, vertexColors, nightLights);
        emitEdgeCurtains(sink, material, xyz, uv, vertices, vertexWidth, vertexDepth, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ,
                rangeMinX, rangeMaxX, rangeMinZ, rangeMaxZ);
    }

    private VertexData buildVertex(ColumnData[] columns, int worldX, int worldZ, int columnMinX, int columnMaxX,
            int columnMinZ, int columnMaxZ, int columnStride) {
        double[] heights = new double[4];
        float[] dayLights = new float[4];
        float[] nightLights = new float[4];
        int count = 0;
        for (int dz = -1; dz <= 0; dz++) {
            for (int dx = -1; dx <= 0; dx++) {
                int sampleX = worldX + dx;
                int sampleZ = worldZ + dz;
                if ((sampleX < columnMinX) || (sampleX > columnMaxX) || (sampleZ < columnMinZ) || (sampleZ > columnMaxZ)) {
                    continue;
                }
                ColumnData column = columns[getColumnIndex(sampleX, sampleZ, columnMinX, columnMinZ, columnStride)];
                if (column.hasSurface) {
                    heights[count] = column.topY;
                    dayLights[count] = column.dayLight;
                    nightLights[count] = column.nightLight;
                    count++;
                }
            }
        }
        return buildLowerMedianVertexData(heights, dayLights, nightLights, count);
    }

    private VertexData buildCenterVertex(ColumnData[] columns, int worldX, int worldZ, int columnMinX, int columnMaxX,
            int columnMinZ, int columnMaxZ, int columnStride) {
        if ((worldX < columnMinX) || (worldX > columnMaxX) || (worldZ < columnMinZ) || (worldZ > columnMaxZ)) {
            return new VertexData(getMinY(), false, 0.0F, 0.0F);
        }
        ColumnData column = columns[getColumnIndex(worldX, worldZ, columnMinX, columnMinZ, columnStride)];
        if ((column == null) || !column.hasSurface) {
            return new VertexData(getMinY(), false, 0.0F, 0.0F);
        }
        return new VertexData(column.topY, true, column.dayLight, column.nightLight);
    }

    private VertexData buildLowerMedianVertexData(double[] heights, float[] dayLights, float[] nightLights, int count) {
        if (count <= 0) {
            return new VertexData(getMinY(), false, 0.0F, 0.0F);
        }
        sortSampleTriples(heights, dayLights, nightLights, count);
        int medianIndex = (count - 1) / 2;
        double selectedHeight = heights[medianIndex];
        float selectedDayLight = dayLights[medianIndex];
        float selectedNightLight = nightLights[medianIndex];
        for (int i = 0; i < count; i++) {
            if (Math.abs(heights[i] - selectedHeight) < 0.0000001) {
                selectedDayLight = Math.max(selectedDayLight, dayLights[i]);
                selectedNightLight = Math.max(selectedNightLight, nightLights[i]);
            }
        }
        return new VertexData(selectedHeight, true, selectedDayLight, selectedNightLight);
    }

    private void sortSampleTriples(double[] heights, float[] dayLights, float[] nightLights, int count) {
        for (int i = 1; i < count; i++) {
            double height = heights[i];
            float dayLight = dayLights[i];
            float nightLight = nightLights[i];
            int insert = i;
            while ((insert > 0) && (heights[insert - 1] > height)) {
                heights[insert] = heights[insert - 1];
                dayLights[insert] = dayLights[insert - 1];
                nightLights[insert] = nightLights[insert - 1];
                insert--;
            }
            heights[insert] = height;
            dayLights[insert] = dayLight;
            nightLights[insert] = nightLight;
        }
    }

    private float[] buildVertexColors(VertexData[] vertices) {
        float[] colors = new float[vertices.length * 3];
        for (int i = 0; i < vertices.length; i++) {
            float light = getPrimaryLight(vertices[i].dayLight, vertices[i].nightLight);
            int offset = i * 3;
            colors[offset] = light;
            colors[offset + 1] = light;
            colors[offset + 2] = light;
        }
        return colors;
    }

    private float[] buildNightLights(VertexData[] vertices) {
        if (getLightingMode() != GLBExport.LightingMode.BOTH) {
            return null;
        }
        float[] nightLights = new float[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            nightLights[i] = vertices[i].nightLight;
        }
        return nightLights;
    }

    private float[] buildCurtainVertexColors(VertexData[] vertices, int topIndex0, int topIndex1) {
        return new float[] {
                getPrimaryLight(vertices[topIndex0].dayLight, vertices[topIndex0].nightLight),
                getPrimaryLight(vertices[topIndex0].dayLight, vertices[topIndex0].nightLight),
                getPrimaryLight(vertices[topIndex0].dayLight, vertices[topIndex0].nightLight),
                getPrimaryLight(vertices[topIndex1].dayLight, vertices[topIndex1].nightLight),
                getPrimaryLight(vertices[topIndex1].dayLight, vertices[topIndex1].nightLight),
                getPrimaryLight(vertices[topIndex1].dayLight, vertices[topIndex1].nightLight),
                getPrimaryLight(vertices[topIndex1].dayLight, vertices[topIndex1].nightLight),
                getPrimaryLight(vertices[topIndex1].dayLight, vertices[topIndex1].nightLight),
                getPrimaryLight(vertices[topIndex1].dayLight, vertices[topIndex1].nightLight),
                getPrimaryLight(vertices[topIndex0].dayLight, vertices[topIndex0].nightLight),
                getPrimaryLight(vertices[topIndex0].dayLight, vertices[topIndex0].nightLight),
                getPrimaryLight(vertices[topIndex0].dayLight, vertices[topIndex0].nightLight) };
    }

    private float[] buildCurtainNightLights(VertexData[] vertices, int topIndex0, int topIndex1) {
        if (getLightingMode() != GLBExport.LightingMode.BOTH) {
            return null;
        }
        return new float[] { vertices[topIndex0].nightLight, vertices[topIndex1].nightLight, vertices[topIndex1].nightLight,
                vertices[topIndex0].nightLight };
    }

    private float getPrimaryLight(float dayLight, float nightLight) {
        return (getLightingMode() == GLBExport.LightingMode.NIGHT) ? nightLight : dayLight;
    }

    private static int getColumnStride(int columnMinX, int columnMaxX) {
        return (columnMaxX - columnMinX) + 1;
    }

    private static int getColumnIndex(int x, int z, int columnMinX, int columnMinZ, int columnStride) {
        return ((z - columnMinZ) * columnStride) + (x - columnMinX);
    }

    private ExportMaterial[] buildQuarterMaterials(String baseMaterialId, ExportedTextureData textureData) {
        ExportMaterial[] materials = new ExportMaterial[HEIGHT_MAP_QUARTER_COUNT];
        for (int quarter = 0; quarter < HEIGHT_MAP_QUARTER_COUNT; quarter++) {
            materials[quarter] = ExportMaterial.customTexture(baseMaterialId + "_q" + quarter, textureData, false);
        }
        return materials;
    }

    private int[][] buildQuarterBounds(int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) {
        int halfWidth = ((rangeMaxX - rangeMinX) + 1) / 2;
        int halfDepth = ((rangeMaxZ - rangeMinZ) + 1) / 2;
        return new int[][] {
                { rangeMinX, rangeMinX + halfWidth - 1, rangeMinZ, rangeMinZ + halfDepth - 1 },
                { rangeMinX + halfWidth, rangeMaxX, rangeMinZ, rangeMinZ + halfDepth - 1 },
                { rangeMinX, rangeMinX + halfWidth - 1, rangeMinZ + halfDepth, rangeMaxZ },
                { rangeMinX + halfWidth, rangeMaxX, rangeMinZ + halfDepth, rangeMaxZ } };
    }

    private void emitEdgeCurtains(BlockModelExportSink sink, ExportMaterial material, double[] xyz, double[] uv, VertexData[] vertices,
            int vertexWidth, int vertexDepth, int meshMinX, int meshMaxX, int meshMinZ, int meshMaxZ, int rangeMinX, int rangeMaxX,
            int rangeMinZ, int rangeMaxZ) throws IOException {
        if (meshMinX == rangeMinX) {
            for (int localZ = 0; localZ < vertexDepth - 1; localZ++) {
                emitCurtainSegment(sink, material, xyz, uv, vertices, localZ * vertexWidth, (localZ + 1) * vertexWidth);
            }
        }
        if (meshMaxX == rangeMaxX) {
            for (int localZ = 0; localZ < vertexDepth - 1; localZ++) {
                emitCurtainSegment(sink, material, xyz, uv, vertices, (localZ * vertexWidth) + (vertexWidth - 1),
                        ((localZ + 1) * vertexWidth) + (vertexWidth - 1));
            }
        }
        if (meshMinZ == rangeMinZ) {
            for (int localX = 0; localX < vertexWidth - 1; localX++) {
                emitCurtainSegment(sink, material, xyz, uv, vertices, localX, localX + 1);
            }
        }
        if (meshMaxZ == rangeMaxZ) {
            int rowStart = (vertexDepth - 1) * vertexWidth;
            for (int localX = 0; localX < vertexWidth - 1; localX++) {
                emitCurtainSegment(sink, material, xyz, uv, vertices, rowStart + localX, rowStart + localX + 1);
            }
        }
    }

    private void emitCurtainSegment(BlockModelExportSink sink, ExportMaterial material, double[] xyz, double[] uv, VertexData[] vertices,
            int topIndex0, int topIndex1) throws IOException {
        if (!(vertices[topIndex0].valid || vertices[topIndex1].valid)) {
            return;
        }
        double[] curtainXyz = new double[] { xyz[topIndex0 * 3], xyz[(topIndex0 * 3) + 1], xyz[(topIndex0 * 3) + 2],
                xyz[topIndex1 * 3], xyz[(topIndex1 * 3) + 1], xyz[(topIndex1 * 3) + 2], xyz[topIndex1 * 3], 0.0,
                xyz[(topIndex1 * 3) + 2], xyz[topIndex0 * 3], 0.0, xyz[(topIndex0 * 3) + 2] };
        double[] curtainUv = new double[] { uv[topIndex0 * 2], uv[(topIndex0 * 2) + 1], uv[topIndex1 * 2], uv[(topIndex1 * 2) + 1],
                uv[topIndex1 * 2], uv[(topIndex1 * 2) + 1], uv[topIndex0 * 2], uv[(topIndex0 * 2) + 1] };
        sink.addTriangleMesh(curtainXyz, curtainUv, DOUBLE_SIDED_QUAD_INDICES, material,
                buildCurtainVertexColors(vertices, topIndex0, topIndex1), buildCurtainNightLights(vertices, topIndex0, topIndex1));
    }

    private ExportedTextureData encodeTextureImage(BufferedImage image) throws IOException {
        BufferOutputStream imageStream = ImageIOManager.imageIOEncode(image, MapType.ImageFormat.FORMAT_PNG);
        if (imageStream == null) {
            throw new IOException("Failed to encode height map texture");
        }
        ExportedTextureData data = new ExportedTextureData();
        data.imagePng = java.util.Arrays.copyOf(imageStream.buf, imageStream.len);
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        boolean hasAlpha = false;
        boolean hasTranslucentAlpha = false;
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int pixel : pixels) {
            int alpha = (pixel >> 24) & 0xFF;
            if (alpha != 0xFF) {
                hasAlpha = true;
                if (alpha != 0) {
                    hasTranslucentAlpha = true;
                }
            }
            red += alpha * ((pixel >> 16) & 0xFF);
            green += alpha * ((pixel >> 8) & 0xFF);
            blue += alpha * (pixel & 0xFF);
            weight += alpha;
        }
        data.diffuseColor = (weight > 0) ? new Color((int) (red / weight), (int) (green / weight), (int) (blue / weight))
                : new Color();
        data.material = null;
        data.hasAlpha = hasAlpha;
        data.hasTranslucentAlpha = hasTranslucentAlpha;
        if (hasAlpha) {
            BufferedImage alphaImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int z = 0; z < image.getHeight(); z++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int alpha = (image.getRGB(x, z) >> 24) & 0xFF;
                    int argb = (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha;
                    alphaImage.setRGB(x, z, argb);
                }
            }
            BufferOutputStream alphaStream = ImageIOManager.imageIOEncode(alphaImage, MapType.ImageFormat.FORMAT_PNG);
            if (alphaStream != null) {
                data.alphaPng = java.util.Arrays.copyOf(alphaStream.buf, alphaStream.len);
            }
        }
        return data;
    }

    private String buildHeightMapMaterialId(int rangeMinX, int rangeMaxX, int rangeMinZ, int rangeMaxZ) {
        String source = (heightMapTextureMap != null) ? heightMapTextureMap : "generated";
        return "height_map_" + source + "_" + heightMapTextureDetail + "_" + rangeMinX + "_" + rangeMaxX + "_" + rangeMinZ
                + "_" + rangeMaxZ;
    }

    private SourceMapSampler resolveSourceMapSampler() {
        if (heightMapTextureMap == null) {
            return null;
        }
        for (org.dynmap.MapType mapType : getWorld().maps) {
            if (!(mapType instanceof HDMap) || !heightMapTextureMap.equals(mapType.getName())) {
                continue;
            }
            HDMap sourceMap = (HDMap) mapType;
            HDPerspective perspective = sourceMap.getPerspective();
            if (!(perspective instanceof IsoHDPerspective)) {
                Log.warning("Height map texture source map '" + heightMapTextureMap + "' does not use an isometric perspective in world '"
                        + getWorld().getName() + "' - falling back to generated texture");
                return null;
            }
            IsoHDPerspective isoPerspective = (IsoHDPerspective) perspective;
            if (!isTopDownPerspective(isoPerspective)) {
                Log.warning("Height map texture source map '" + heightMapTextureMap + "' must use azimuth 180 and inclination 90 in world '"
                        + getWorld().getName() + "' - falling back to generated texture");
                return null;
            }
            return new SourceMapSampler(getWorld(), sourceMap, heightMapTextureDetail);
        }
        Log.warning("Height map texture source map '" + heightMapTextureMap + "' was not found for world '" + getWorld().getName()
                + "' - falling back to generated texture");
        return null;
    }

    private static boolean isTopDownPerspective(IsoHDPerspective perspective) {
        double configuredAzimuth = normalizeConfiguredAzimuth(perspective.azimuth);
        return (Math.abs(configuredAzimuth - 180.0) < 0.0001) && (Math.abs(perspective.inclination - 90.0) < 0.0001);
    }

    private static double normalizeConfiguredAzimuth(double internalAzimuth) {
        double configuredAzimuth = internalAzimuth - 90.0;
        while (configuredAzimuth < 0.0) {
            configuredAzimuth += 360.0;
        }
        while (configuredAzimuth >= 360.0) {
            configuredAzimuth -= 360.0;
        }
        return configuredAzimuth;
    }

    private ExportMaterial getSurfaceMaterial(ResolvedBlockData block, BlockStep step) throws IOException {
        if ((block == null) || (block.materials == null)) {
            return null;
        }
        if (block.patches != null) {
            for (int i = 0; i < block.patches.length; i++) {
                if ((block.steps[i] == step) && (block.materials.length > i) && (block.materials[i] != null)) {
                    ExportMaterial material = getFirstTexturedMaterial(block.materials[i]);
                    if (material != null) {
                        return material;
                    }
                }
            }
            for (int i = 0; i < block.patches.length; i++) {
                if ((block.steps[i] == step.opposite()) && (block.materials.length > i) && (block.materials[i] != null)) {
                    ExportMaterial material = getFirstTexturedMaterial(block.materials[i]);
                    if (material != null) {
                        return material;
                    }
                }
            }
            return null;
        }
        int face = step.opposite().getFaceEntered();
        if ((face >= 0) && (face < block.materials.length) && (block.materials[face] != null) && (block.steps.length > face && block.steps[face] == step.opposite())) {
            return getFirstSolidMaterial(block.materials[face]);
        }

        int opposite = step.opposite().ordinal();
        if ((opposite < block.materials.length) && (block.materials[opposite] != null) && (block.steps.length > opposite && block.steps[opposite] == step.opposite())) {
            return getFirstSolidMaterial(block.materials[opposite]);
        }
//        int face = step.opposite().getFaceEntered();
//        if ((face >= 0) && (face < block.materials.length)) {
//            return getFirstTexturedMaterial(block.materials[face]);
//        }
//        int opposite = step.getFaceEntered();
//        if ((opposite >= 0) && (opposite < block.materials.length)) {
//            return getFirstTexturedMaterial(block.materials[opposite]);
//        }
        return null;
    }

    private ExportMaterial getAnyTexturedMaterial(ResolvedBlockData block) throws IOException {
        if ((block == null) || (block.materials == null)) {
            return null;
        }
        for (ExportMaterial[] materials : block.materials) {
            ExportMaterial material = getFirstTexturedMaterial(materials);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    private ExportMaterial getFirstTexturedMaterial(ExportMaterial[] materials) throws IOException {
        if (materials == null) {
            return null;
        }
        for (ExportMaterial material : materials) {
            if (hasDefinedTexture(material)) {
                return material;
            }
        }
        return null;
    }

    private boolean hasDefinedTexture(ExportMaterial material) {
        if (material == null) {
            return false;
        }
        if ((material.getTextureIndex() >= 0) || material.hasCustomTexture()) {
            return true;
        }
        ExportMaterial[] bakedLayers = material.getBakedLayers();
        if (bakedLayers != null) {
            for (ExportMaterial layer : bakedLayers) {
                if (hasDefinedTexture(layer)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class SourceMapSampler {
        private final DynmapWorld world;
        private final HDMap sourceMap;
        private final MapStorage storage;
        private final int tileSize;
        private final int textureDetail;
        private final Map<String, BufferedImage> tileImages = new HashMap<String, BufferedImage>();
        private final Vector3D input = new Vector3D();
        private final Vector3D output = new Vector3D();

        SourceMapSampler(DynmapWorld world, HDMap sourceMap, int requestedTextureDetail) {
            this.world = world;
            this.sourceMap = sourceMap;
            this.storage = world.getMapStorage();
            this.tileSize = sourceMap.getTileSize();
            int maxTextureDetail = Math.max(1, sourceMap.getPerspective().getModelScale());
            this.textureDetail = Math.max(1, Math.min(requestedTextureDetail, maxTextureDetail));
            if (requestedTextureDetail > maxTextureDetail) {
                Log.warning("Height map texture detail " + requestedTextureDetail + " exceeds source map scale " + maxTextureDetail
                        + " for map '" + sourceMap.getName() + "' - clamping to " + maxTextureDetail);
            }
        }

        int getTextureDetail() {
            return textureDetail;
        }

        int sample(double worldX, double worldY, double worldZ, int fallbackArgb) throws IOException {
            input.x = worldX;
            input.y = worldY;
            input.z = worldZ;
            sourceMap.getPerspective().transformWorldToMapCoord(input, output);
            double pixelX = output.x;
            double pixelY = output.y;
            int tileX = (int) Math.floor(pixelX / tileSize);
            int tileY = (int) Math.floor(pixelY / tileSize);
            BufferedImage image = getTileImage(tileX, tileY);
            if (image == null) {
                return fallbackArgb;
            }
            double localX = pixelX - (tileX * tileSize);
            double localY = pixelY - (tileY * tileSize);
            if ((localX < 0.0) || (localY < 0.0) || (localX >= tileSize) || (localY >= tileSize)) {
                return fallbackArgb;
            }
            int sampleX = Math.min(image.getWidth() - 1, Math.max(0, (int) Math.floor((localX / tileSize) * image.getWidth())));
            int sampleY = Math.min(image.getHeight() - 1,
                    Math.max(0, (int) Math.floor((1.0 - (localY / tileSize)) * image.getHeight())));
            return image.getRGB(sampleX, sampleY);
        }

        private BufferedImage getTileImage(int tileX, int tileY) throws IOException {
            String key = tileX + ":" + tileY;
            if (tileImages.containsKey(key)) {
                return tileImages.get(key);
            }
            MapStorageTile tile = storage.getTile(world, sourceMap, tileX, tileY, 0, MapType.ImageVariant.STANDARD);
            BufferedImage image = null;
            if ((tile != null) && tile.getReadLock(5000)) {
                try {
                    TileRead read = tile.read();
                    if (read != null) {
                        image = ImageIOManager.imageIODecode(read);
                    }
                } finally {
                    tile.releaseReadLock();
                }
            }
            tileImages.put(key, image);
            return image;
        }
    }
}
