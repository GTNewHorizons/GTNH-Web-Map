package org.dynmap.exporter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.imageio.ImageIO;

import org.dynmap.Color;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.MapType;
import org.dynmap.hdmap.TexturePack.ExportedTextureData;
import org.dynmap.modelmap.ModelMap;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTile.TileRead;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.ImageIOManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public final class BlockModelHeightMapZoomOutExporter {
    private static final int[] ZOOMOUT_STEP_SEQUENCE = { 3, 1, 2, 0 };
    private static final int HEIGHT_MAP_QUARTER_COUNT = 4;
    private static final int[] DOUBLE_SIDED_QUAD_INDICES = { 0, 1, 2, 0, 2, 3, 3, 2, 1, 3, 1, 0 };

    public enum ResultStatus {
        COMBINED,
        NO_DATA,
        INCOMPATIBLE
    }

    public static final class Result {
        public final ResultStatus status;
        public final BufferOutputStream glb;

        private Result(ResultStatus status, BufferOutputStream glb) {
            this.status = status;
            this.glb = glb;
        }

        static Result combined(BufferOutputStream glb) {
            return new Result(ResultStatus.COMBINED, glb);
        }

        static Result noData() {
            return new Result(ResultStatus.NO_DATA, null);
        }

        static Result incompatible() {
            return new Result(ResultStatus.INCOMPATIBLE, null);
        }
    }

    private static final class VertexSample {
        final double y;
        final float dayLight;
        final float nightLight;

        VertexSample(double y, float dayLight, float nightLight) {
            this.y = y;
            this.dayLight = dayLight;
            this.nightLight = nightLight;
        }
    }

    private static final class ParsedHeightMapTile {
        final Map<Long, VertexSample> vertices = new HashMap<Long, VertexSample>();
        final BufferedImage textureImage;

        ParsedHeightMapTile(BufferedImage textureImage) {
            this.textureImage = textureImage;
        }
    }

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION = 2;
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A;
    private static final int BIN_CHUNK_TYPE = 0x004E4942;
    private static final int COMPONENT_TYPE_BYTE = 5120;
    private static final int COMPONENT_TYPE_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_TYPE_SHORT = 5122;
    private static final int COMPONENT_TYPE_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final ModelMap map;
    private final DynmapWorld world;
    private final DynmapCore core;

    public BlockModelHeightMapZoomOutExporter(ModelMap map, DynmapWorld world) {
        this.map = map;
        this.world = world;
        this.core = map.getCore();
    }

    public Result export(MapStorageTile zoomTile, String basename) throws IOException {
        if ((zoomTile == null) || (zoomTile.zoom <= 0)) {
            return Result.incompatible();
        }

        int childZoom = zoomTile.zoom - 1;
        int childStep = 1 << childZoom;
        Map<Long, VertexSample> sourceVertices = new HashMap<Long, VertexSample>();
        BufferedImage outputTexture = null;
        int childTextureWidth = -1;
        int childTextureHeight = -1;
        int foundChildCount = 0;
        MapStorage storage = world.getMapStorage();
        int childTileXBase = zoomTile.x;
        int childTileZBase = zoomTile.y;

        for (int i = 0; i < 4; i++) {
            int childTileX = childTileXBase + childStep * (ZOOMOUT_STEP_SEQUENCE[i] & 1);
            int childTileZ = childTileZBase + childStep * (ZOOMOUT_STEP_SEQUENCE[i] >> 1);
            MapStorageTile childTile = storage.getTile(world, map, childTileX, childTileZ, childZoom, zoomTile.var);
            ParsedHeightMapTile childData;
            try {
                childData = loadHeightMapTile(childTile, childTileX, childTileZ, childZoom);
            } catch (IncompatibleHeightMapTileException ignored) {
                return Result.incompatible();
            }
            if (childData == null) {
                continue;
            }
            if (outputTexture == null) {
                childTextureWidth = childData.textureImage.getWidth();
                childTextureHeight = childData.textureImage.getHeight();
                outputTexture = new BufferedImage(childTextureWidth, childTextureHeight, BufferedImage.TYPE_INT_ARGB);
            } else if ((childData.textureImage.getWidth() != childTextureWidth)
                    || (childData.textureImage.getHeight() != childTextureHeight)) {
                return Result.incompatible();
            }
            blitZoomOutChildTexture(outputTexture, childData.textureImage, i);
            mergeVertices(sourceVertices, childData.vertices);
            foundChildCount++;
        }

        if (foundChildCount < 4) {
            return Result.incompatible();
        }

        int minBlockX = getTileMinBlockX(zoomTile.x);
        int minBlockZ = getTileMinBlockZ(zoomTile.y);
        int widthBlocks = getTileWidthBlocks(zoomTile.zoom);
        int depthBlocks = widthBlocks;
        int sourceVertexStep = 1 << childZoom;
        int outputVertexStep = sourceVertexStep << 1;
        int vertexWidth = (widthBlocks / outputVertexStep) + 1;
        int vertexDepth = (depthBlocks / outputVertexStep) + 1;
        double[] xyz = new double[vertexWidth * vertexDepth * 3];
        double[] uv = new double[vertexWidth * vertexDepth * 2];
        boolean[] validVertices = new boolean[vertexWidth * vertexDepth];
        float[] vertexColors = new float[vertexWidth * vertexDepth * 3];
        float[] nightLights = (map.getLightingMode().toExportLightingMode() == GLBExport.LightingMode.BOTH)
                ? new float[vertexWidth * vertexDepth] : null;

        for (int vz = 0; vz < vertexDepth; vz++) {
            int worldZ = minBlockZ + (vz * outputVertexStep);
            for (int vx = 0; vx < vertexWidth; vx++) {
                int worldX = minBlockX + (vx * outputVertexStep);
                int index = (vz * vertexWidth) + vx;
                VertexSample sample = buildOutputVertex(sourceVertices, worldX, worldZ, sourceVertexStep);
                validVertices[index] = sample != null;
                int xyzOffset = index * 3;
                int uvOffset = index * 2;
                xyz[xyzOffset] = worldX;
                xyz[xyzOffset + 1] = (sample != null) ? sample.y : world.minY;
                xyz[xyzOffset + 2] = worldZ;
                uv[uvOffset] = (double) (worldX - minBlockX) / (double) widthBlocks;
                uv[uvOffset + 1] = (double) (worldZ - minBlockZ) / (double) depthBlocks;
                float primaryLight = (sample != null) ? getPrimaryLight(sample.dayLight, sample.nightLight,
                        map.getLightingMode().toExportLightingMode()) : 0.0F;
                vertexColors[xyzOffset] = primaryLight;
                vertexColors[xyzOffset + 1] = primaryLight;
                vertexColors[xyzOffset + 2] = primaryLight;
                if (nightLights != null) {
                    nightLights[index] = (sample != null) ? sample.nightLight : 0.0F;
                }
            }
        }

        GLBExport export = new GLBExport(null, map.getShader(), world, core, basename);
        export.setRenderBounds(minBlockX, world.minY, minBlockZ, minBlockX + widthBlocks - 1, world.worldheight - 1,
                minBlockZ + depthBlocks - 1);
        export.setLightingMode(map.getLightingMode().toExportLightingMode());
        export.setChunk(zoomTile.x, zoomTile.y);
        ExportedTextureData textureData = encodeTextureImage(outputTexture);
        ExportMaterial[] quarterMaterials = buildQuarterMaterials(buildMaterialId(zoomTile), textureData);
        int[][] quarterBounds = buildQuarterBounds(minBlockX, widthBlocks, minBlockZ, depthBlocks, outputVertexStep);
        boolean emittedGeometry = false;
        for (int quarter = 0; quarter < HEIGHT_MAP_QUARTER_COUNT; quarter++) {
            int[] indices = buildTriangleIndices(validVertices, vertexWidth, vertexDepth, minBlockX, minBlockZ, outputVertexStep,
                    quarterBounds[quarter][0], quarterBounds[quarter][1], quarterBounds[quarter][2], quarterBounds[quarter][3]);
            if (indices.length > 0) {
                export.addTriangleMesh(xyz, uv, indices, quarterMaterials[quarter], vertexColors, nightLights);
                emittedGeometry = true;
            }
            emittedGeometry |= emitEdgeCurtains(export, quarterMaterials[quarter], xyz, uv, vertexColors, nightLights,
                    validVertices, vertexWidth, vertexDepth, minBlockX, minBlockZ, outputVertexStep, quarterBounds[quarter][0],
                    quarterBounds[quarter][1], quarterBounds[quarter][2], quarterBounds[quarter][3], minBlockX,
                    minBlockX + widthBlocks - outputVertexStep, minBlockZ, minBlockZ + depthBlocks - outputVertexStep,
                    map.getLightingMode().toExportLightingMode());
        }
        if (!emittedGeometry) {
            return Result.noData();
        }
        return Result.combined(export.finishToBuffer());
    }

    private ParsedHeightMapTile loadHeightMapTile(MapStorageTile tile, int tileX, int tileZ, int zoom)
            throws IOException, IncompatibleHeightMapTileException {
        if (tile == null) {
            return null;
        }
        if (!tile.getReadLock(5000)) {
            throw new IOException("Failed to lock child height map tile for read");
        }
        try {
            TileRead read = tile.read();
            if ((read == null) || (read.image == null)) {
                return null;
            }
            BufferInputStream input = read.image;
            if (isGzipCompressed(input)) {
                input = gunzip(input);
            }
            byte[] glbBytes = new byte[input.length()];
            System.arraycopy(input.buffer(), 0, glbBytes, 0, input.length());
            return parseHeightMapTile(glbBytes, tileX, tileZ, zoom);
        } finally {
            tile.releaseReadLock();
        }
    }

    private ParsedHeightMapTile parseHeightMapTile(byte[] glbBytes, int tileX, int tileZ, int zoom)
            throws IOException, IncompatibleHeightMapTileException {
        ByteBuffer glb = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN);
        if (glb.remaining() < 20) {
            throw new IncompatibleHeightMapTileException();
        }
        if (glb.getInt() != GLB_MAGIC) {
            throw new IncompatibleHeightMapTileException();
        }
        if (glb.getInt() != GLB_VERSION) {
            throw new IncompatibleHeightMapTileException();
        }
        int totalLength = glb.getInt();
        if ((totalLength <= 0) || (totalLength > glbBytes.length)) {
            throw new IncompatibleHeightMapTileException();
        }

        byte[] jsonBytes = null;
        byte[] binBytes = null;
        while (glb.remaining() >= 8) {
            int chunkLength = glb.getInt();
            int chunkType = glb.getInt();
            if ((chunkLength < 0) || (chunkLength > glb.remaining())) {
                throw new IncompatibleHeightMapTileException();
            }
            byte[] chunkBytes = new byte[chunkLength];
            glb.get(chunkBytes);
            if (chunkType == JSON_CHUNK_TYPE) {
                jsonBytes = chunkBytes;
            } else if (chunkType == BIN_CHUNK_TYPE) {
                binBytes = chunkBytes;
            }
        }
        if ((jsonBytes == null) || (binBytes == null)) {
            throw new IncompatibleHeightMapTileException();
        }

        JSONObject root = parseJson(jsonBytes);
        JSONArray meshes = getArray(root, "meshes");
        if (meshes.size() != 1) {
            throw new IncompatibleHeightMapTileException();
        }
        JSONObject mesh = asObject(meshes.get(0));
        JSONArray primitives = getArray(mesh, "primitives");
        if (primitives.isEmpty()) {
            throw new IncompatibleHeightMapTileException();
        }
        JSONArray materials = getArray(root, "materials");
        JSONArray textures = getArray(root, "textures");
        JSONArray images = getArray(root, "images");
        JSONArray accessors = getArray(root, "accessors");
        JSONArray bufferViews = getArray(root, "bufferViews");
        ParsedHeightMapTile parsed = null;
        int minBlockX = getTileMinBlockX(tileX);
        int minBlockZ = getTileMinBlockZ(tileZ);
        int widthBlocks = getTileWidthBlocks(zoom);
        double originX = minBlockX + ((widthBlocks - 1) / 2.0);
        double originZ = minBlockZ + ((widthBlocks - 1) / 2.0);
        for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
            JSONObject primitive = asObject(primitives.get(primitiveIndex));
            Number materialIndex = asNumber(primitive.get("material"));
            if (materialIndex == null) {
                throw new IncompatibleHeightMapTileException();
            }
            JSONObject material = asObject(materials.get(materialIndex.intValue()));
            String materialName = asString(material.get("name"));
            if ((materialName == null) || !materialName.startsWith("height_map_")) {
                throw new IncompatibleHeightMapTileException();
            }
            if (parsed == null) {
                BufferedImage textureImage = resolveMaterialTexture(material, textures, images, bufferViews, binBytes);
                if (textureImage == null) {
                    throw new IncompatibleHeightMapTileException();
                }
                parsed = new ParsedHeightMapTile(textureImage);
            }
            mergePrimitiveVertices(parsed, primitive, accessors, bufferViews, binBytes, originX, originZ, minBlockX, minBlockZ,
                    widthBlocks, zoom);
        }
        if (parsed == null) {
            throw new IncompatibleHeightMapTileException();
        }
        return parsed;
    }

    private JSONObject parseJson(byte[] jsonBytes) throws IOException, IncompatibleHeightMapTileException {
        String json = new String(jsonBytes, UTF8);
        int end = json.length();
        while ((end > 0) && (json.charAt(end - 1) <= ' ')) {
            end--;
        }
        try {
            return (JSONObject) new JSONParser().parse(json.substring(0, end));
        } catch (ParseException | ClassCastException ex) {
            throw new IncompatibleHeightMapTileException();
        }
    }

    private static JSONArray getArray(JSONObject object, String key) throws IncompatibleHeightMapTileException {
        Object value = object.get(key);
        if (!(value instanceof JSONArray)) {
            throw new IncompatibleHeightMapTileException();
        }
        return (JSONArray) value;
    }

    private static JSONObject asObject(Object value) throws IncompatibleHeightMapTileException {
        if (!(value instanceof JSONObject)) {
            throw new IncompatibleHeightMapTileException();
        }
        return (JSONObject) value;
    }

    private static Number asNumber(Object value) {
        return (value instanceof Number) ? (Number) value : null;
    }

    private static String asString(Object value) {
        return (value instanceof String) ? (String) value : null;
    }

    private static int getInt(Object value, int defaultValue) {
        return (value instanceof Number) ? ((Number) value).intValue() : defaultValue;
    }

    private static byte[] getBufferViewBytes(JSONArray bufferViews, int bufferViewIndex, byte[] binBytes, int accessorOffset,
            int requestedLength) throws IncompatibleHeightMapTileException {
        JSONObject bufferView = asObject(bufferViews.get(bufferViewIndex));
        int byteOffset = getInt(bufferView.get("byteOffset"), 0) + accessorOffset;
        int byteLength = getInt(bufferView.get("byteLength"), 0);
        if ((accessorOffset < 0) || (byteOffset < 0) || (byteLength < 0) || (byteOffset > binBytes.length)) {
            throw new IncompatibleHeightMapTileException();
        }
        int availableLength = Math.min(byteLength - accessorOffset, binBytes.length - byteOffset);
        if (availableLength < 0) {
            throw new IncompatibleHeightMapTileException();
        }
        int resultLength = (requestedLength > 0) ? requestedLength : availableLength;
        if (availableLength < resultLength) {
            throw new IncompatibleHeightMapTileException();
        }
        byte[] out = new byte[resultLength];
        System.arraycopy(binBytes, byteOffset, out, 0, resultLength);
        return out;
    }

    private BufferedImage resolveMaterialTexture(JSONObject material, JSONArray textures, JSONArray images, JSONArray bufferViews,
            byte[] binBytes) throws IOException, IncompatibleHeightMapTileException {
        JSONObject pbr = asObject(material.get("pbrMetallicRoughness"));
        JSONObject baseColorTexture = asObject(pbr.get("baseColorTexture"));
        Number textureIndex = asNumber(baseColorTexture.get("index"));
        if (textureIndex == null) {
            throw new IncompatibleHeightMapTileException();
        }
        JSONObject texture = asObject(textures.get(textureIndex.intValue()));
        Number imageIndex = asNumber(texture.get("source"));
        if (imageIndex == null) {
            throw new IncompatibleHeightMapTileException();
        }
        JSONObject image = asObject(images.get(imageIndex.intValue()));
        Number imageViewIndex = asNumber(image.get("bufferView"));
        if (imageViewIndex == null) {
            throw new IncompatibleHeightMapTileException();
        }
        return decodePng(getBufferViewBytes(bufferViews, imageViewIndex.intValue(), binBytes, 0, 0));
    }

    private void mergePrimitiveVertices(ParsedHeightMapTile tile, JSONObject primitive, JSONArray accessors, JSONArray bufferViews,
            byte[] binBytes, double originX, double originZ, int minBlockX, int minBlockZ, int widthBlocks, int zoom)
            throws IncompatibleHeightMapTileException {
        JSONObject attributes = asObject(primitive.get("attributes"));
        Number positionAccessorIndex = asNumber(attributes.get("POSITION"));
        Number colorAccessorIndex = asNumber(attributes.get("COLOR_0"));
        Number nightLightAccessorIndex = asNumber(attributes.get("_NIGHTLIGHT"));
        if (positionAccessorIndex == null) {
            throw new IncompatibleHeightMapTileException();
        }
        JSONObject positionAccessor = asObject(accessors.get(positionAccessorIndex.intValue()));
        Number count = asNumber(positionAccessor.get("count"));
        Number componentType = asNumber(positionAccessor.get("componentType"));
        String accessorType = asString(positionAccessor.get("type"));
        Number viewIndex = asNumber(positionAccessor.get("bufferView"));
        int accessorOffset = getInt(positionAccessor.get("byteOffset"), 0);
        if ((count == null) || (componentType == null) || (viewIndex == null) || (componentType.intValue() != COMPONENT_TYPE_FLOAT)
                || !"VEC3".equals(accessorType)) {
            throw new IncompatibleHeightMapTileException();
        }
        int vertexCount = count.intValue();
        byte[] positionBytes = getBufferViewBytes(bufferViews, viewIndex.intValue(), binBytes, accessorOffset, vertexCount * 12);
        ByteBuffer positions = ByteBuffer.wrap(positionBytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] colors = readAccessorFloats(accessors, bufferViews, binBytes, colorAccessorIndex, vertexCount, 3);
        float[] primitiveNightLights = readAccessorFloats(accessors, bufferViews, binBytes, nightLightAccessorIndex, vertexCount, 1);
        for (int i = 0; i < vertexCount; i++) {
            double worldX = positions.getFloat() + originX;
            double y = positions.getFloat();
            double worldZ = positions.getFloat() + originZ;
            int gridX = roundCoordinate(worldX);
            int gridZ = roundCoordinate(worldZ);
            if ((Math.abs(worldX - gridX) > 0.01) || (Math.abs(worldZ - gridZ) > 0.01)) {
                throw new IncompatibleHeightMapTileException();
            }
            if ((gridX < minBlockX) || (gridX > (minBlockX + widthBlocks)) || (gridZ < minBlockZ)
                    || (gridZ > (minBlockZ + widthBlocks))) {
                throw new IncompatibleHeightMapTileException();
            }
            float dayLight = (colors != null) ? colors[i * 3] : 1.0F;
            float nightLight = (primitiveNightLights != null) ? primitiveNightLights[i] : 0.0F;
            putVertex(tile.vertices, gridX, gridZ, y, dayLight, nightLight);
        }
    }

    private static BufferedImage decodePng(byte[] bytes) throws IOException {
        ImageIO.setUseCache(false);
        return ImageIO.read(new BufferInputStream(bytes));
    }

    private static boolean isGzipCompressed(BufferInputStream input) {
        return (input != null) && (input.length() >= 2) && ((input.buffer()[0] & 0xFF) == 0x1F)
                && ((input.buffer()[1] & 0xFF) == 0x8B);
    }

    private static BufferInputStream gunzip(BufferInputStream compressed) throws IOException {
        GZIPInputStream gzip = new GZIPInputStream(compressed);
        BufferOutputStream out = new BufferOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int len;
            while ((len = gzip.read(buffer)) >= 0) {
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } finally {
            gzip.close();
        }
        return new BufferInputStream(out.buf, out.len);
    }

    private void blitZoomOutChildTexture(BufferedImage outputTexture, BufferedImage childTexture, int index) {
        int width = childTexture.getWidth();
        int height = childTexture.getHeight();
        int[] argb = childTexture.getRGB(0, 0, width, height, null, 0, width);
        int[] scaled = downsampleZoomOutChild(argb, width, height);
        outputTexture.setRGB(((index >> 1) != 0) ? 0 : width / 2, (index & 1) * (height / 2), width / 2, height / 2, scaled, 0,
                width / 2);
    }

    private int[] downsampleZoomOutChild(int[] argb, int width, int height) {
        int[] scaled = new int[(width / 2) * (height / 2)];
        for (int y = 0; y < height; y += 2) {
            int sourceOffset = y * width;
            for (int x = 0; x < width; x += 2, sourceOffset += 2) {
                int p0 = argb[sourceOffset];
                int p1 = argb[sourceOffset + 1];
                int p2 = argb[sourceOffset + width];
                int p3 = argb[sourceOffset + width + 1];
                int a0 = (p0 >> 24) & 0xFF;
                int a1 = (p1 >> 24) & 0xFF;
                int a2 = (p2 >> 24) & 0xFF;
                int a3 = (p3 >> 24) & 0xFF;
                int alpha = a0 + a1 + a2 + a3;
                int outAlpha = (alpha + 2) >> 2;
                int red = 0;
                int green = 0;
                int blue = 0;
                if (alpha > 0) {
                    red = ((((p0 >> 16) & 0xFF) * a0) + (((p1 >> 16) & 0xFF) * a1) + (((p2 >> 16) & 0xFF) * a2)
                            + (((p3 >> 16) & 0xFF) * a3) + (alpha / 2)) / alpha;
                    green = ((((p0 >> 8) & 0xFF) * a0) + (((p1 >> 8) & 0xFF) * a1) + (((p2 >> 8) & 0xFF) * a2)
                            + (((p3 >> 8) & 0xFF) * a3) + (alpha / 2)) / alpha;
                    blue = (((p0 & 0xFF) * a0) + ((p1 & 0xFF) * a1) + ((p2 & 0xFF) * a2) + ((p3 & 0xFF) * a3)
                            + (alpha / 2)) / alpha;
                }
                scaled[(y / 2) * (width / 2) + (x / 2)] = (outAlpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        return scaled;
    }

    private float[] readAccessorFloats(JSONArray accessors, JSONArray bufferViews, byte[] binBytes, Number accessorIndex,
            int expectedCount, int componentCount) throws IncompatibleHeightMapTileException {
        if (accessorIndex == null) {
            return null;
        }
        JSONObject accessor = asObject(accessors.get(accessorIndex.intValue()));
        Number count = asNumber(accessor.get("count"));
        Number componentType = asNumber(accessor.get("componentType"));
        String accessorType = asString(accessor.get("type"));
        Number viewIndex = asNumber(accessor.get("bufferView"));
        int accessorOffset = getInt(accessor.get("byteOffset"), 0);
        boolean normalized = Boolean.TRUE.equals(accessor.get("normalized"));
        if ((count == null) || (componentType == null) || (viewIndex == null) || (count.intValue() != expectedCount)
                || !matchesAccessorType(accessorType, componentCount)) {
            throw new IncompatibleHeightMapTileException();
        }
        int componentSize = getComponentSize(componentType.intValue());
        int requestedLength = expectedCount * componentCount * componentSize;
        byte[] valueBytes = getBufferViewBytes(bufferViews, viewIndex.intValue(), binBytes, accessorOffset, requestedLength);
        ByteBuffer values = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[expectedCount * componentCount];
        for (int i = 0; i < out.length; i++) {
            out[i] = readComponentValue(values, componentType.intValue(), normalized);
        }
        return out;
    }

    private static boolean matchesAccessorType(String accessorType, int componentCount) {
        if (componentCount == 1) {
            return "SCALAR".equals(accessorType);
        }
        if (componentCount == 2) {
            return "VEC2".equals(accessorType);
        }
        if (componentCount == 3) {
            return "VEC3".equals(accessorType);
        }
        if (componentCount == 4) {
            return "VEC4".equals(accessorType);
        }
        return false;
    }

    private static int getComponentSize(int componentType) throws IncompatibleHeightMapTileException {
        switch (componentType) {
            case COMPONENT_TYPE_BYTE:
            case COMPONENT_TYPE_UNSIGNED_BYTE:
                return 1;
            case COMPONENT_TYPE_SHORT:
            case COMPONENT_TYPE_UNSIGNED_SHORT:
                return 2;
            case COMPONENT_TYPE_FLOAT:
                return 4;
            default:
                throw new IncompatibleHeightMapTileException();
        }
    }

    private static float readComponentValue(ByteBuffer values, int componentType, boolean normalized)
            throws IncompatibleHeightMapTileException {
        switch (componentType) {
            case COMPONENT_TYPE_BYTE: {
                int value = values.get();
                if (!normalized) {
                    return value;
                }
                return Math.max(-1.0F, value / 127.0F);
            }
            case COMPONENT_TYPE_UNSIGNED_BYTE: {
                int value = values.get() & 0xFF;
                return normalized ? (value / 255.0F) : value;
            }
            case COMPONENT_TYPE_SHORT: {
                int value = values.getShort();
                if (!normalized) {
                    return value;
                }
                return Math.max(-1.0F, value / 32767.0F);
            }
            case COMPONENT_TYPE_UNSIGNED_SHORT: {
                int value = values.getShort() & 0xFFFF;
                return normalized ? (value / 65535.0F) : value;
            }
            case COMPONENT_TYPE_FLOAT:
                return values.getFloat();
            default:
                throw new IncompatibleHeightMapTileException();
        }
    }

    private static void mergeVertices(Map<Long, VertexSample> target, Map<Long, VertexSample> incoming) {
        for (Map.Entry<Long, VertexSample> entry : incoming.entrySet()) {
            VertexSample existing = target.get(entry.getKey());
            VertexSample incomingSample = entry.getValue();
            target.put(entry.getKey(), mergeSamples(existing, incomingSample));
        }
    }

    private static void putVertex(Map<Long, VertexSample> vertices, int x, int z, double y, float dayLight, float nightLight) {
        long key = vertexKey(x, z);
        VertexSample existing = vertices.get(key);
        vertices.put(key, mergeSamples(existing, new VertexSample(y, dayLight, nightLight)));
    }

    private static VertexSample mergeSamples(VertexSample existing, VertexSample incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        if (incoming.y > existing.y) {
            return incoming;
        }
        if (incoming.y < existing.y) {
            return existing;
        }
        return new VertexSample(existing.y, Math.max(existing.dayLight, incoming.dayLight),
                Math.max(existing.nightLight, incoming.nightLight));
    }

    private VertexSample buildOutputVertex(Map<Long, VertexSample> sourceVertices, int worldX, int worldZ, int sourceVertexStep) {
        double maxY = world.minY;
        boolean valid = false;
        float dayLight = 0.0F;
        float nightLight = 0.0F;
        for (int dz = 0; dz <= sourceVertexStep; dz += sourceVertexStep) {
            for (int dx = 0; dx <= sourceVertexStep; dx += sourceVertexStep) {
                VertexSample sample = sourceVertices.get(vertexKey(worldX + dx, worldZ + dz));
                if (sample != null) {
                    valid = true;
                    if (sample.y > maxY) {
                        maxY = sample.y;
                        dayLight = sample.dayLight;
                        nightLight = sample.nightLight;
                    } else if (sample.y == maxY) {
                        dayLight = Math.max(dayLight, sample.dayLight);
                        nightLight = Math.max(nightLight, sample.nightLight);
                    }
                }
            }
        }
        return valid ? new VertexSample(maxY, dayLight, nightLight) : null;
    }

    private ExportMaterial[] buildQuarterMaterials(String baseMaterialId, ExportedTextureData textureData) {
        ExportMaterial[] materials = new ExportMaterial[HEIGHT_MAP_QUARTER_COUNT];
        for (int quarter = 0; quarter < HEIGHT_MAP_QUARTER_COUNT; quarter++) {
            materials[quarter] = ExportMaterial.customTexture(baseMaterialId + "_q" + quarter, textureData, false);
        }
        return materials;
    }

    private int[][] buildQuarterBounds(int minBlockX, int widthBlocks, int minBlockZ, int depthBlocks, int cellStep) {
        int halfWidth = widthBlocks / 2;
        int halfDepth = depthBlocks / 2;
        return new int[][] {
                { minBlockX, minBlockX + halfWidth - cellStep, minBlockZ, minBlockZ + halfDepth - cellStep },
                { minBlockX + halfWidth, minBlockX + widthBlocks - cellStep, minBlockZ, minBlockZ + halfDepth - cellStep },
                { minBlockX, minBlockX + halfWidth - cellStep, minBlockZ + halfDepth, minBlockZ + depthBlocks - cellStep },
                { minBlockX + halfWidth, minBlockX + widthBlocks - cellStep, minBlockZ + halfDepth, minBlockZ + depthBlocks - cellStep } };
    }

    private static int[] buildTriangleIndices(boolean[] validVertices, int vertexWidth, int vertexDepth, int minWorldX, int minWorldZ,
            int vertexStep, int cellMinX, int cellMaxX, int cellMinZ, int cellMaxZ) {
        int quadCount = 0;
        for (int z = 0; z < vertexDepth - 1; z++) {
            int worldZ = minWorldZ + (z * vertexStep);
            if ((worldZ < cellMinZ) || (worldZ > cellMaxZ)) {
                continue;
            }
            for (int x = 0; x < vertexWidth - 1; x++) {
                int worldX = minWorldX + (x * vertexStep);
                if ((worldX < cellMinX) || (worldX > cellMaxX)) {
                    continue;
                }
                int v00 = (z * vertexWidth) + x;
                int v10 = v00 + 1;
                int v01 = ((z + 1) * vertexWidth) + x;
                int v11 = v01 + 1;
                if (validVertices[v00] || validVertices[v10] || validVertices[v01] || validVertices[v11]) {
                    quadCount++;
                }
            }
        }
        int[] indices = new int[quadCount * 6];
        int offset = 0;
        for (int z = 0; z < vertexDepth - 1; z++) {
            int worldZ = minWorldZ + (z * vertexStep);
            if ((worldZ < cellMinZ) || (worldZ > cellMaxZ)) {
                continue;
            }
            for (int x = 0; x < vertexWidth - 1; x++) {
                int worldX = minWorldX + (x * vertexStep);
                if ((worldX < cellMinX) || (worldX > cellMaxX)) {
                    continue;
                }
                int v00 = (z * vertexWidth) + x;
                int v10 = v00 + 1;
                int v01 = ((z + 1) * vertexWidth) + x;
                int v11 = v01 + 1;
                if (!(validVertices[v00] || validVertices[v10] || validVertices[v01] || validVertices[v11])) {
                    continue;
                }
                indices[offset++] = v00;
                indices[offset++] = v01;
                indices[offset++] = v11;
                indices[offset++] = v00;
                indices[offset++] = v11;
                indices[offset++] = v10;
            }
        }
        return indices;
    }

    private boolean emitEdgeCurtains(BlockModelExportSink sink, ExportMaterial material, double[] xyz, double[] uv, float[] vertexColors,
            float[] nightLights, boolean[] validVertices, int vertexWidth, int vertexDepth, int minWorldX, int minWorldZ, int vertexStep,
            int meshMinX, int meshMaxX, int meshMinZ, int meshMaxZ, int tileMinX, int tileMaxX, int tileMinZ, int tileMaxZ,
            GLBExport.LightingMode lightingMode) throws IOException {
        boolean emitted = false;
        if (meshMinX == tileMinX) {
            for (int localZ = 0; localZ < vertexDepth - 1; localZ++) {
                emitted |= emitCurtainSegment(sink, material, xyz, uv, vertexColors, nightLights, validVertices, localZ * vertexWidth,
                        (localZ + 1) * vertexWidth, lightingMode);
            }
        }
        if (meshMaxX == tileMaxX) {
            for (int localZ = 0; localZ < vertexDepth - 1; localZ++) {
                emitted |= emitCurtainSegment(sink, material, xyz, uv, vertexColors, nightLights, validVertices,
                        (localZ * vertexWidth) + (vertexWidth - 1),
                        ((localZ + 1) * vertexWidth) + (vertexWidth - 1), lightingMode);
            }
        }
        if (meshMinZ == tileMinZ) {
            for (int localX = 0; localX < vertexWidth - 1; localX++) {
                emitted |= emitCurtainSegment(sink, material, xyz, uv, vertexColors, nightLights, validVertices, localX, localX + 1,
                        lightingMode);
            }
        }
        if (meshMaxZ == tileMaxZ) {
            int rowStart = (vertexDepth - 1) * vertexWidth;
            for (int localX = 0; localX < vertexWidth - 1; localX++) {
                emitted |= emitCurtainSegment(sink, material, xyz, uv, vertexColors, nightLights, validVertices, rowStart + localX,
                        rowStart + localX + 1, lightingMode);
            }
        }
        return emitted;
    }

    private boolean emitCurtainSegment(BlockModelExportSink sink, ExportMaterial material, double[] xyz, double[] uv, float[] vertexColors,
            float[] nightLights, boolean[] validVertices, int topIndex0, int topIndex1, GLBExport.LightingMode lightingMode)
            throws IOException {
        if (!(validVertices[topIndex0] || validVertices[topIndex1])) {
            return false;
        }
        double[] curtainXyz = new double[] { xyz[topIndex0 * 3], xyz[(topIndex0 * 3) + 1], xyz[(topIndex0 * 3) + 2],
                xyz[topIndex1 * 3], xyz[(topIndex1 * 3) + 1], xyz[(topIndex1 * 3) + 2], xyz[topIndex1 * 3], 0.0,
                xyz[(topIndex1 * 3) + 2], xyz[topIndex0 * 3], 0.0, xyz[(topIndex0 * 3) + 2] };
        double[] curtainUv = new double[] { uv[topIndex0 * 2], uv[(topIndex0 * 2) + 1], uv[topIndex1 * 2], uv[(topIndex1 * 2) + 1],
                uv[topIndex1 * 2], uv[(topIndex1 * 2) + 1], uv[topIndex0 * 2], uv[(topIndex0 * 2) + 1] };
        sink.addTriangleMesh(curtainXyz, curtainUv, DOUBLE_SIDED_QUAD_INDICES, material,
                buildCurtainVertexColors(vertexColors, topIndex0, topIndex1),
                buildCurtainNightLights(nightLights, topIndex0, topIndex1, lightingMode));
        return true;
    }

    private float[] buildCurtainNightLights(float[] nightLights, int topIndex0, int topIndex1, GLBExport.LightingMode lightingMode) {
        if (lightingMode != GLBExport.LightingMode.BOTH) {
            return null;
        }
        return new float[] { nightLights[topIndex0], nightLights[topIndex1], nightLights[topIndex1], nightLights[topIndex0] };
    }

    private float[] buildCurtainVertexColors(float[] vertexColors, int topIndex0, int topIndex1) {
        return new float[] { vertexColors[topIndex0 * 3], vertexColors[(topIndex0 * 3) + 1], vertexColors[(topIndex0 * 3) + 2],
                vertexColors[topIndex1 * 3], vertexColors[(topIndex1 * 3) + 1], vertexColors[(topIndex1 * 3) + 2],
                vertexColors[topIndex1 * 3], vertexColors[(topIndex1 * 3) + 1], vertexColors[(topIndex1 * 3) + 2],
                vertexColors[topIndex0 * 3], vertexColors[(topIndex0 * 3) + 1], vertexColors[(topIndex0 * 3) + 2] };
    }

    private float getPrimaryLight(float dayLight, float nightLight, GLBExport.LightingMode lightingMode) {
        return (lightingMode == GLBExport.LightingMode.NIGHT) ? nightLight : dayLight;
    }

    private ExportedTextureData encodeTextureImage(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage opaqueImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int[] fallbackPixels = computeOpaqueFallbackPixels(pixels, width, height);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = flattenToOpaque(pixels[i], fallbackPixels[i]);
        }
        opaqueImage.setRGB(0, 0, width, height, pixels, 0, width);

        BufferOutputStream imageStream = ImageIOManager.imageIOEncode(opaqueImage, MapType.ImageFormat.FORMAT_PNG);
        if (imageStream == null) {
            throw new IOException("Failed to encode height map zoomout texture");
        }
        ExportedTextureData data = new ExportedTextureData();
        data.imagePng = new byte[imageStream.len];
        System.arraycopy(imageStream.buf, 0, data.imagePng, 0, imageStream.len);
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int pixel : pixels) {
            int alpha = (pixel >> 24) & 0xFF;
            red += alpha * ((pixel >> 16) & 0xFF);
            green += alpha * ((pixel >> 8) & 0xFF);
            blue += alpha * (pixel & 0xFF);
            weight += alpha;
        }
        data.diffuseColor = (weight > 0) ? new Color((int) (red / weight), (int) (green / weight), (int) (blue / weight))
                : new Color();
        data.material = null;
        data.hasAlpha = false;
        data.hasTranslucentAlpha = false;
        return data;
    }

    private int[] computeOpaqueFallbackPixels(int[] pixels, int width, int height) {
        int[] filled = new int[pixels.length];
        boolean[] valid = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int alpha = (pixels[i] >> 24) & 0xFF;
            if (alpha > 0) {
                filled[i] = 0xFF000000 | (pixels[i] & 0x00FFFFFF);
                valid[i] = true;
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            int[] nextFilled = new int[filled.length];
            System.arraycopy(filled, 0, nextFilled, 0, filled.length);
            boolean[] nextValid = new boolean[valid.length];
            System.arraycopy(valid, 0, nextValid, 0, valid.length);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * width) + x;
                    if (valid[index]) {
                        continue;
                    }
                    int red = 0;
                    int green = 0;
                    int blue = 0;
                    int count = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int sampleY = y + dy;
                        if ((sampleY < 0) || (sampleY >= height)) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            int sampleX = x + dx;
                            if ((dx == 0) && (dy == 0)) {
                                continue;
                            }
                            if ((sampleX < 0) || (sampleX >= width)) {
                                continue;
                            }
                            int sampleIndex = (sampleY * width) + sampleX;
                            if (!valid[sampleIndex]) {
                                continue;
                            }
                            int sample = filled[sampleIndex];
                            red += (sample >> 16) & 0xFF;
                            green += (sample >> 8) & 0xFF;
                            blue += sample & 0xFF;
                            count++;
                        }
                    }
                    if (count <= 0) {
                        continue;
                    }
                    nextFilled[index] =
                            0xFF000000 | (((red / count) & 0xFF) << 16) | (((green / count) & 0xFF) << 8) | ((blue / count) & 0xFF);
                    nextValid[index] = true;
                    changed = true;
                }
            }
            filled = nextFilled;
            valid = nextValid;
        }
        for (int i = 0; i < filled.length; i++) {
            if (!valid[i]) {
                filled[i] = 0xFF000000;
            }
        }
        return filled;
    }

    private int flattenToOpaque(int pixel, int fallbackOpaque) {
        int alpha = (pixel >> 24) & 0xFF;
        if (alpha <= 0) {
            return fallbackOpaque;
        }
        if (alpha >= 0xFF) {
            return 0xFF000000 | (pixel & 0x00FFFFFF);
        }
        int srcRed = (pixel >> 16) & 0xFF;
        int srcGreen = (pixel >> 8) & 0xFF;
        int srcBlue = pixel & 0xFF;
        int fallbackRed = (fallbackOpaque >> 16) & 0xFF;
        int fallbackGreen = (fallbackOpaque >> 8) & 0xFF;
        int fallbackBlue = fallbackOpaque & 0xFF;
        int inverseAlpha = 0xFF - alpha;
        int red = ((srcRed * alpha) + (fallbackRed * inverseAlpha) + 127) / 255;
        int green = ((srcGreen * alpha) + (fallbackGreen * inverseAlpha) + 127) / 255;
        int blue = ((srcBlue * alpha) + (fallbackBlue * inverseAlpha) + 127) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private int getTileWidthBlocks(int zoom) {
        return (map.getGranularity() << zoom) * 16;
    }

    private int getTileMinBlockX(int tileX) {
        return tileX * map.getGranularity() * 16;
    }

    private int getTileMinBlockZ(int tileZ) {
        return tileZ * map.getGranularity() * 16;
    }

    private static String buildMaterialId(MapStorageTile zoomTile) {
        return "height_map_zoomout_" + zoomTile.x + "_" + zoomTile.y + "_z" + zoomTile.zoom;
    }

    private static int roundCoordinate(double value) {
        return (int) Math.round(value);
    }

    private static long vertexKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static final class IncompatibleHeightMapTileException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
