package org.dynmap.exporter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.TexturePack.ExportedTextureData;
import org.dynmap.hdmap.TexturePackHDShader;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.PatchDefinition;

public class GLBExport implements BlockModelExportSink {
    public enum LightingMode {
        DAY("day"),
        NIGHT("night"),
        BOTH("both");

        private final String id;

        LightingMode(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int FILTER_NEAREST = 9728;
    private static final int WRAP_CLAMP_TO_EDGE = 33071;
    private static final int ARRAY_BUFFER_TARGET = 34962;
    private static final int COMPONENT_TYPE_BYTE = 5120;
    private static final int COMPONENT_TYPE_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_TYPE_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final String GLTF_ASSET_VERSION = "2.0";
    private static final int GLB_HEADER_LENGTH = 12;
    private static final int GLB_CHUNK_HEADER_LENGTH = 8;
    private static final int GLB_VERSION = 2;
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A;
    private static final int BIN_CHUNK_TYPE = 0x004E4942;
    private static final byte JSON_CHUNK_PADDING_BYTE = (byte) 0x20;
    private static final byte BIN_CHUNK_PADDING_BYTE = (byte) 0x00;
    private static final int FOUR_BYTE_ALIGNMENT = 4;
    private static final int FOUR_BYTE_ALIGNMENT_MASK = FOUR_BYTE_ALIGNMENT - 1;

    private static final class PrimitiveData {
        final ExportMaterial material;
        final FloatArrayBuilder positions = new FloatArrayBuilder();
        final FloatArrayBuilder normals = new FloatArrayBuilder();
        final FloatArrayBuilder colors = new FloatArrayBuilder();
        final FloatArrayBuilder nightLights = new FloatArrayBuilder();
        final FloatArrayBuilder texcoords = new FloatArrayBuilder();
        final IntArrayBuilder indices = new IntArrayBuilder();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        PrimitiveData(ExportMaterial material) {
            this.material = material;
        }

        int addVertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b,
                float nightLight, float u, float v) {
            int index = positions.size() / 3;
            positions.add(x);
            positions.add(y);
            positions.add(z);
            normals.add(nx);
            normals.add(ny);
            normals.add(nz);
            colors.add(r);
            colors.add(g);
            colors.add(b);
            nightLights.add(nightLight);
            texcoords.add(u);
            texcoords.add(v);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            return index;
        }
    }

    private static final class FaceNormal {
        final float x;
        final float y;
        final float z;

        FaceNormal(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        FaceNormal invert() {
            return new FaceNormal(-x, -y, -z);
        }
    }

    private static final class AccessorBinary {
        final byte[] bytes;
        final int componentType;
        final boolean normalized;

        AccessorBinary(byte[] bytes, int componentType, boolean normalized) {
            this.bytes = bytes;
            this.componentType = componentType;
            this.normalized = normalized;
        }
    }

    private final File destination;
    private final TexturePackHDShader shader;
    private final DynmapWorld world;
    private final DynmapCore core;
    private final String basename;

    private final LinkedHashMap<String, PrimitiveData> primitives = new LinkedHashMap<String, PrimitiveData>();

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private double originX;
    private double originY;
    private double originZ;
    private double scale = 1.0;
    private boolean centerOrigin = true;
    private boolean cullExportRegionEdges;
    private LightingMode lightingMode = LightingMode.DAY;
    private BlockModelExportMode exportMode = BlockModelExportMode.FULL;
    private int simplifiedMinSkyLight = 7;
    private String heightMapTextureMap;
    private int heightMapTextureDetail = 1;

    public GLBExport(File destination, TexturePackHDShader shader, DynmapWorld world, DynmapCore core, String basename) {
        this.destination = destination;
        this.shader = shader;
        this.world = world;
        this.core = core;
        this.basename = basename;
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
        if (centerOrigin) {
            originX = (maxX + minX) / 2.0;
            originY = minY;
            originZ = (maxZ + minZ) / 2.0;
        }
    }

    public void setOrigin(double originX, double originY, double originZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.centerOrigin = false;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setCullExportRegionEdges(boolean cullExportRegionEdges) {
        this.cullExportRegionEdges = cullExportRegionEdges;
    }

    public void setLightingMode(LightingMode lightingMode) {
        this.lightingMode = (lightingMode == null) ? LightingMode.DAY : lightingMode;
    }

    public void setExportMode(BlockModelExportMode exportMode) {
        this.exportMode = (exportMode == null) ? BlockModelExportMode.FULL : exportMode;
    }

    public void setSimplifiedMinSkyLight(int simplifiedMinSkyLight) {
        this.simplifiedMinSkyLight = Math.max(0, Math.min(15, simplifiedMinSkyLight));
    }

    public void setHeightMapTextureMap(String heightMapTextureMap) {
        this.heightMapTextureMap = heightMapTextureMap;
    }

    public void setHeightMapTextureDetail(int heightMapTextureDetail) {
        this.heightMapTextureDetail = Math.max(1, heightMapTextureDetail);
    }

    public boolean processExport(DynmapCommandSender sender) {
        return processExport(sender, false);
    }

    public boolean processExport(DynmapCommandSender sender, boolean gzipOutput) {
        try {
            if (destination == null) {
                throw new IOException("Export destination not configured");
            }
            BufferOutputStream glb = exportToBuffer();
            if (glb == null) {
                throw new IOException("Export produced no geometry");
            }
            BufferOutputStream output = gzipOutput ? gzip(glb) : glb;
            writeGLB(new BufferedOutputStream(new FileOutputStream(destination)), output.buf, output.len);
            sender.sendMessage("Export completed - " + destination.getPath());
            return true;
        } catch (IOException iox) {
            sender.sendMessage("Export failed: " + iox.getMessage());
            return false;
        }
    }

    public BufferOutputStream exportToBuffer() throws IOException {
        return exportToBuffer(null);
    }

    public BufferOutputStream exportToBuffer(MapChunkCache cache) throws IOException {
        TexturePack texturePack = shader.getTexturePackForExport();
        if (texturePack == null) {
            throw new IOException("Export unsupported - invalid texture pack");
        }
        primitives.clear();
        BlockModelExporter exporter = new BlockModelExporter(world, core, shader);
        exporter.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
        exporter.setCullExportRegionEdges(cullExportRegionEdges);
        exporter.setLightingMode(lightingMode);
        exporter.setExportMode(exportMode);
        exporter.setSimplifiedMinSkyLight(simplifiedMinSkyLight);
        exporter.setHeightMapTextureMap(heightMapTextureMap);
        exporter.setHeightMapTextureDetail(heightMapTextureDetail);
        if (cache != null) {
            exporter.export(cache, this);
        } else {
            exporter.export(this);
        }
        if (primitives.isEmpty()) {
            return null;
        }
        return buildGLB(texturePack);
    }

    public BufferOutputStream finishToBuffer() throws IOException {
        TexturePack texturePack = shader.getTexturePackForExport();
        if (texturePack == null) {
            throw new IOException("Export unsupported - invalid texture pack");
        }
        if (primitives.isEmpty()) {
            return null;
        }
        return buildGLB(texturePack);
    }

    @Override
    public void setChunk(int chunkX, int chunkZ) throws IOException {
    }

    @Override
    public void setBlock(int blockId, int blockData) throws IOException {
    }

    @Override
    public void addPatch(PatchDefinition patch, double x, double y, double z, ExportMaterial material,
            float[] vertexColors, float[] nightVertexLights) throws IOException {
        if (material == null) {
            return;
        }
        PrimitiveData primitive = primitives.get(material.getMaterialId());
        if (primitive == null) {
            primitive = new PrimitiveData(material);
            primitives.put(material.getMaterialId(), primitive);
        }
        ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(patch, x, y, z, material.getRotation());
        addGeometry(primitive, geometry, vertexColors, nightVertexLights);
    }

    @Override
    public void addQuad(double[] xyz, ExportMaterial material, float[] vertexColors, float[] nightVertexLights)
            throws IOException {
        if ((material == null) || (xyz == null) || (xyz.length != 12)) {
            return;
        }
        PrimitiveData primitive = primitives.get(material.getMaterialId());
        if (primitive == null) {
            primitive = new PrimitiveData(material);
            primitives.put(material.getMaterialId(), primitive);
        }
        addGeometry(primitive, ExportPatchGeometry.buildQuad(xyz), vertexColors, nightVertexLights);
    }

    @Override
    public void addPolygon(double[] xyz, ExportMaterial material, float[] vertexColors, float[] nightVertexLights)
            throws IOException {
        if ((material == null) || (xyz == null) || ((xyz.length % 3) != 0) || (xyz.length < 9)) {
            return;
        }
        PrimitiveData primitive = primitives.get(material.getMaterialId());
        if (primitive == null) {
            primitive = new PrimitiveData(material);
            primitives.put(material.getMaterialId(), primitive);
        }
        int vertexCount = xyz.length / 3;
        for (int i = 1; i < vertexCount - 1; i++) {
            double[] tri = new double[] { xyz[0], xyz[1], xyz[2], xyz[i * 3], xyz[(i * 3) + 1], xyz[(i * 3) + 2],
                    xyz[(i + 1) * 3], xyz[((i + 1) * 3) + 1], xyz[((i + 1) * 3) + 2] };
            addGeometry(primitive, ExportPatchGeometry.buildTriangle(tri),
                    sliceVertexColors(vertexColors, 0, i, i + 1),
                    (nightVertexLights != null) ? sliceNightLights(nightVertexLights, 0, i, i + 1) : null);
        }
    }

    @Override
    public void addTriangleMesh(double[] xyz, double[] uv, int[] indices, ExportMaterial material, float[] vertexColors,
            float[] nightVertexLights) throws IOException {
        if ((material == null) || (xyz == null) || (uv == null) || (indices == null) || ((xyz.length % 3) != 0)
                || ((uv.length % 2) != 0) || ((indices.length % 3) != 0)) {
            return;
        }
        int vertexCount = xyz.length / 3;
        if ((vertexCount <= 0) || (uv.length != (vertexCount * 2))) {
            return;
        }
        PrimitiveData primitive = primitives.get(material.getMaterialId());
        if (primitive == null) {
            primitive = new PrimitiveData(material);
            primitives.put(material.getMaterialId(), primitive);
        }
        float[] normals = buildMeshNormals(xyz, indices, vertexCount);
        int[] mappedIndices = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            int xyzOffset = i * 3;
            int uvOffset = i * 2;
            int colorOffset = i * 3;
            float red = ((vertexColors != null) && (vertexColors.length >= (colorOffset + 3))) ? vertexColors[colorOffset] : 1.0F;
            float green =
                    ((vertexColors != null) && (vertexColors.length >= (colorOffset + 3))) ? vertexColors[colorOffset + 1] : 1.0F;
            float blue =
                    ((vertexColors != null) && (vertexColors.length >= (colorOffset + 3))) ? vertexColors[colorOffset + 2] : 1.0F;
            float nightLight = ((nightVertexLights != null) && (nightVertexLights.length > i)) ? nightVertexLights[i] : 0.0F;
            mappedIndices[i] = primitive.addVertex(transform(xyz[xyzOffset], originX), transform(xyz[xyzOffset + 1], originY),
                    transform(xyz[xyzOffset + 2], originZ), normals[xyzOffset], normals[xyzOffset + 1], normals[xyzOffset + 2],
                    red, green, blue, nightLight, (float) uv[uvOffset], flipV((float) uv[uvOffset + 1]));
        }
        for (int index : indices) {
            if ((index < 0) || (index >= vertexCount)) {
                throw new IOException("Invalid mesh index " + index + " for vertex count " + vertexCount);
            }
            primitive.indices.add(mappedIndices[index]);
        }
    }

    private float[] sliceVertexColors(float[] vertexColors, int i0, int i1, int i2) {
        if ((vertexColors == null) || (vertexColors.length < 9)) {
            return new float[] { 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F };
        }
        return new float[] { vertexColors[i0 * 3], vertexColors[(i0 * 3) + 1], vertexColors[(i0 * 3) + 2],
                vertexColors[i1 * 3], vertexColors[(i1 * 3) + 1], vertexColors[(i1 * 3) + 2], vertexColors[i2 * 3],
                vertexColors[(i2 * 3) + 1], vertexColors[(i2 * 3) + 2] };
    }

    private float[] sliceNightLights(float[] nightVertexLights, int i0, int i1, int i2) {
        if ((nightVertexLights == null) || (nightVertexLights.length < 3)) {
            return null;
        }
        return new float[] { nightVertexLights[i0], nightVertexLights[i1], nightVertexLights[i2] };
    }

    private void addGeometry(PrimitiveData primitive, ExportPatchGeometry.Geometry geometry, float[] vertexColors,
            float[] nightVertexLights) {
        switch (geometry.sideVisible) {
            case TOP:
                addFrontFace(primitive, geometry.xyz, geometry.uv, vertexColors, nightVertexLights, geometry.vertexCount);
                break;
            case BOTTOM:
                addBackFace(primitive, geometry.xyz, geometry.uv, vertexColors, nightVertexLights, geometry.vertexCount);
                break;
            case BOTH:
                addFrontFace(primitive, geometry.xyz, geometry.uv, vertexColors, nightVertexLights, geometry.vertexCount);
                addBackFace(primitive, geometry.xyz, geometry.uv, vertexColors, nightVertexLights, geometry.vertexCount);
                break;
            case FLIP:
                addFrontFace(primitive, geometry.xyz, geometry.uv, vertexColors, nightVertexLights, geometry.vertexCount);
                addBackFace(primitive, geometry.xyz, buildFlipBackUVs(geometry.uv, geometry.vertexCount), vertexColors,
                        nightVertexLights, geometry.vertexCount);
                break;
        }
    }

    private void addFrontFace(PrimitiveData primitive, double[] xyz, double[] uv, float[] vertexColors,
            float[] nightVertexLights, int vertexCount) {
        FaceNormal normal = buildFaceNormal(xyz);
        int v0 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 0);
        int v1 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 1);
        int v2 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 2);
        primitive.indices.add(v0);
        primitive.indices.add(v1);
        primitive.indices.add(v2);
        if (vertexCount == 4) {
            int v3 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 3);
            primitive.indices.add(v0);
            primitive.indices.add(v2);
            primitive.indices.add(v3);
        }
    }

    private void addBackFace(PrimitiveData primitive, double[] xyz, double[] uv, float[] vertexColors,
            float[] nightVertexLights, int vertexCount) {
        FaceNormal normal = buildFaceNormal(xyz).invert();
        if (vertexCount == 4) {
            int v3 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 3);
            int v2 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 2);
            int v1 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 1);
            int v0 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 0);
            primitive.indices.add(v3);
            primitive.indices.add(v2);
            primitive.indices.add(v1);
            primitive.indices.add(v3);
            primitive.indices.add(v1);
            primitive.indices.add(v0);
        } else {
            int v2 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 2);
            int v1 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 1);
            int v0 = addVertex(primitive, xyz, uv, vertexColors, nightVertexLights, normal, 0);
            primitive.indices.add(v2);
            primitive.indices.add(v1);
            primitive.indices.add(v0);
        }
    }

    private double[] buildFlipBackUVs(double[] uv, int vertexCount) {
        if (vertexCount == 4) {
            return new double[] { uv[2], uv[3], uv[0], uv[1], uv[6], uv[7], uv[4], uv[5] };
        }
        return new double[] { uv[0], uv[1], uv[4], uv[5], uv[2], uv[3] };
    }

    private int addVertex(PrimitiveData primitive, double[] xyz, double[] uv, float[] vertexColors,
            float[] nightVertexLights, FaceNormal normal, int vertexIndex) {
        int xyzOffset = vertexIndex * 3;
        int uvOffset = vertexIndex * 2;
        int colorOffset = vertexIndex * 3;
        float nightLight = (nightVertexLights != null) ? nightVertexLights[vertexIndex] : 0.0F;
        return primitive.addVertex(transform(xyz[xyzOffset], originX), transform(xyz[xyzOffset + 1], originY),
                transform(xyz[xyzOffset + 2], originZ), normal.x, normal.y, normal.z, vertexColors[colorOffset],
                vertexColors[colorOffset + 1], vertexColors[colorOffset + 2], nightLight, (float) uv[uvOffset],
                flipV((float) uv[uvOffset + 1]));
    }

    private FaceNormal buildFaceNormal(double[] xyz) {
        double ax = xyz[3] - xyz[0];
        double ay = xyz[4] - xyz[1];
        double az = xyz[5] - xyz[2];
        double bx = xyz[6] - xyz[0];
        double by = xyz[7] - xyz[1];
        double bz = xyz[8] - xyz[2];
        double nx = (ay * bz) - (az * by);
        double ny = (az * bx) - (ax * bz);
        double nz = (ax * by) - (ay * bx);
        double len = Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
        if (len < 1.0E-9) {
            return new FaceNormal(0.0F, 1.0F, 0.0F);
        }
        return new FaceNormal((float) (nx / len), (float) (ny / len), (float) (nz / len));
    }

    private float transform(double coordinate, double origin) {
        return (float) ((coordinate - origin) * scale);
    }

    private float flipV(float v) {
        return 1.0F - v;
    }

    private float[] buildMeshNormals(double[] xyz, int[] indices, int vertexCount) {
        float[] normals = new float[vertexCount * 3];
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];
            if ((i0 < 0) || (i0 >= vertexCount) || (i1 < 0) || (i1 >= vertexCount) || (i2 < 0) || (i2 >= vertexCount)) {
                continue;
            }
            int off0 = i0 * 3;
            int off1 = i1 * 3;
            int off2 = i2 * 3;
            double ax = xyz[off1] - xyz[off0];
            double ay = xyz[off1 + 1] - xyz[off0 + 1];
            double az = xyz[off1 + 2] - xyz[off0 + 2];
            double bx = xyz[off2] - xyz[off0];
            double by = xyz[off2 + 1] - xyz[off0 + 1];
            double bz = xyz[off2 + 2] - xyz[off0 + 2];
            float nx = (float) ((ay * bz) - (az * by));
            float ny = (float) ((az * bx) - (ax * bz));
            float nz = (float) ((ax * by) - (ay * bx));
            normals[off0] += nx;
            normals[off0 + 1] += ny;
            normals[off0 + 2] += nz;
            normals[off1] += nx;
            normals[off1 + 1] += ny;
            normals[off1 + 2] += nz;
            normals[off2] += nx;
            normals[off2 + 1] += ny;
            normals[off2 + 2] += nz;
        }
        for (int i = 0; i < normals.length; i += 3) {
            double len = Math.sqrt((normals[i] * normals[i]) + (normals[i + 1] * normals[i + 1])
                    + (normals[i + 2] * normals[i + 2]));
            if (len < 1.0E-9) {
                normals[i] = 0.0F;
                normals[i + 1] = 1.0F;
                normals[i + 2] = 0.0F;
            } else {
                normals[i] /= len;
                normals[i + 1] /= len;
                normals[i + 2] /= len;
            }
        }
        return normals;
    }

    private BufferOutputStream buildGLB(TexturePack texturePack) throws IOException {
        ArrayList<PrimitiveData> primitiveList = new ArrayList<PrimitiveData>(primitives.values());
        ArrayList<String> bufferViews = new ArrayList<String>();
        ArrayList<String> accessors = new ArrayList<String>();
        ByteArrayBuilder binary = new ByteArrayBuilder();
        StringBuilder primitivesJson = new StringBuilder();
        StringBuilder imagesJson = new StringBuilder();
        StringBuilder samplersJson = new StringBuilder();
        StringBuilder texturesJson = new StringBuilder();
        StringBuilder materialsJson = new StringBuilder();
        Map<ExportedTextureData, Integer> customTextureIndices = new HashMap<ExportedTextureData, Integer>();

        appendJsonEntry(samplersJson, String.format(Locale.US,
                "{\"magFilter\":%d,\"minFilter\":%d,\"wrapS\":%d,\"wrapT\":%d}", FILTER_NEAREST, FILTER_NEAREST,
                WRAP_CLAMP_TO_EDGE, WRAP_CLAMP_TO_EDGE));

        int textureIndex = 0;
        for (int i = 0; i < primitiveList.size(); i++) {
            PrimitiveData primitive = expandToTriangleList(primitiveList.get(i));
            AccessorBinary normalBinary = toNormalizedSignedByteBytes(primitive.normals);
            AccessorBinary colorBinary = toNormalizedUnsignedByteBytes(primitive.colors);
            AccessorBinary nightLightBinary = toNormalizedUnsignedByteBytes(primitive.nightLights);
            AccessorBinary uvBinary = toNormalizedUnsignedShortBytes(primitive.texcoords);

            int positionView = appendSegment(binary, toFloatBytes(primitive.positions), ARRAY_BUFFER_TARGET);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, ARRAY_BUFFER_TARGET));
            int positionAccessor = accessors.size();
            accessors.add(makeAccessor(positionView, COMPONENT_TYPE_FLOAT, primitive.positions.size() / 3, "VEC3", false,
                    primitive.minX,
                    primitive.minY, primitive.minZ, primitive.maxX, primitive.maxY, primitive.maxZ));

            int normalView = appendSegment(binary, normalBinary.bytes, ARRAY_BUFFER_TARGET);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, ARRAY_BUFFER_TARGET));
            int normalAccessor = accessors.size();
            accessors.add(makeAccessor(normalView, normalBinary.componentType, primitive.normals.size() / 3, "VEC3",
                    normalBinary.normalized, null, null, null, null, null, null));

            int colorView = appendSegment(binary, colorBinary.bytes, ARRAY_BUFFER_TARGET);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, ARRAY_BUFFER_TARGET));
            int colorAccessor = accessors.size();
            accessors.add(makeAccessor(colorView, colorBinary.componentType, primitive.colors.size() / 3, "VEC3",
                    colorBinary.normalized, null, null, null, null, null, null));

            int uvView = appendSegment(binary, uvBinary.bytes, ARRAY_BUFFER_TARGET);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, ARRAY_BUFFER_TARGET));
            int uvAccessor = accessors.size();
            accessors.add(makeAccessor(uvView, uvBinary.componentType, primitive.texcoords.size() / 2, "VEC2",
                    uvBinary.normalized, null, null, null, null, null, null));

            Integer nightLightAccessor = null;
            if (lightingMode == LightingMode.BOTH) {
                int nightLightView = appendSegment(binary, nightLightBinary.bytes, ARRAY_BUFFER_TARGET);
                bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, ARRAY_BUFFER_TARGET));
                nightLightAccessor = Integer.valueOf(accessors.size());
                accessors.add(makeAccessor(nightLightView, nightLightBinary.componentType, primitive.nightLights.size(),
                        "SCALAR", nightLightBinary.normalized, null, null, null, null, null, null));
            }

            StringBuilder materialJson = new StringBuilder();
            materialJson.append("{\"name\":\"").append(primitive.material.getMaterialId()).append("\",");
            if (primitive.material.isSolidColor()) {
                appendSolidColorMaterial(materialJson, primitive.material);
            } else {
                ExportedTextureData texture = primitive.material.hasCustomTexture() ? primitive.material.getCustomTextureData()
                        : texturePack.exportTexture(primitive.material);
                Integer existingTextureIndex =
                        primitive.material.hasCustomTexture() ? customTextureIndices.get(texture) : null;
                int imageIndex;
                if (existingTextureIndex != null) {
                    imageIndex = existingTextureIndex.intValue();
                } else {
                    int imageView = appendSegment(binary, texture.imagePng, null);
                    bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, null));
                    imageIndex = textureIndex;
                    appendJsonEntry(imagesJson, String.format(Locale.US, "{\"bufferView\":%d,\"mimeType\":\"image/png\"}",
                            imageView));
                    appendJsonEntry(texturesJson, String.format(Locale.US, "{\"sampler\":0,\"source\":%d}", imageIndex));
                    if (primitive.material.hasCustomTexture()) {
                        customTextureIndices.put(texture, Integer.valueOf(imageIndex));
                    }
                    textureIndex++;
                }

                materialJson.append("\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":").append(imageIndex)
                        .append("},\"metallicFactor\":0.0,\"roughnessFactor\":1.0}");
                if (texture.hasAlpha) {
                    if (texture.hasTranslucentAlpha) {
                        materialJson.append(",\"alphaMode\":\"BLEND\"");
                    } else {
                        materialJson.append(",\"alphaMode\":\"MASK\",\"alphaCutoff\":0.5");
                    }
                }
                if (primitive.material.isEmissive()) {
                    materialJson.append(",\"emissiveFactor\":[1.0,1.0,1.0],\"emissiveTexture\":{\"index\":")
                            .append(imageIndex).append("}");
                }
            }
            materialJson.append("}");
            appendJsonEntry(materialsJson, materialJson.toString());

            StringBuilder attributesJson = new StringBuilder();
            attributesJson.append("\"POSITION\":").append(positionAccessor).append(",\"NORMAL\":").append(normalAccessor)
                    .append(",\"COLOR_0\":").append(colorAccessor).append(",\"TEXCOORD_0\":").append(uvAccessor);
            if (nightLightAccessor != null) {
                attributesJson.append(",\"_NIGHTLIGHT\":").append(nightLightAccessor.intValue());
            }
            appendJsonEntry(primitivesJson, String.format(Locale.US,
                    "{\"attributes\":{%s},\"material\":%d,\"mode\":4}", attributesJson.toString(), i));
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"asset\":{\"version\":\"").append(GLTF_ASSET_VERSION).append("\",\"generator\":\"GTNH-Web-Map\"},");
        json.append("\"scene\":0,");
        json.append("\"scenes\":[{\"name\":\"").append(basename).append("\",\"nodes\":[0]}],");
        json.append("\"nodes\":[{\"mesh\":0}],");
        json.append("\"meshes\":[{\"primitives\":[");
        json.append(primitivesJson);
        json.append("]}],");
        json.append("\"buffers\":[{\"byteLength\":").append(binary.size()).append("}],");
        json.append("\"bufferViews\":[");
        json.append(join(bufferViews));
        json.append("],");
        json.append("\"accessors\":[");
        json.append(join(accessors));
        json.append("],");
        json.append("\"images\":[");
        json.append(imagesJson);
        json.append("],");
        json.append("\"samplers\":[");
        json.append(samplersJson);
        json.append("],");
        json.append("\"textures\":[");
        json.append(texturesJson);
        json.append("],");
        json.append("\"materials\":[");
        json.append(materialsJson);
        json.append("]}");

        byte[] jsonBytes = pad(json.toString().getBytes(UTF8), JSON_CHUNK_PADDING_BYTE);
        byte[] binBytes = pad(binary.toByteArray(), BIN_CHUNK_PADDING_BYTE);
        int totalLength = GLB_HEADER_LENGTH + GLB_CHUNK_HEADER_LENGTH + jsonBytes.length + GLB_CHUNK_HEADER_LENGTH
                + binBytes.length;

        BufferOutputStream out = new BufferOutputStream();
        out.write(intToBytes(GLB_MAGIC));
        out.write(intToBytes(GLB_VERSION));
        out.write(intToBytes(totalLength));
        out.write(intToBytes(jsonBytes.length));
        out.write(intToBytes(JSON_CHUNK_TYPE));
        out.write(jsonBytes);
        out.write(intToBytes(binBytes.length));
        out.write(intToBytes(BIN_CHUNK_TYPE));
        out.write(binBytes);
        return out;
    }

    private void appendSolidColorMaterial(StringBuilder materialJson, ExportMaterial material) {
        int argb = material.getSolidColorArgb();
        float alpha = ((argb >> 24) & 0xFF) / 255.0F;
        float red = srgbToLinear(((argb >> 16) & 0xFF) / 255.0F);
        float green = srgbToLinear(((argb >> 8) & 0xFF) / 255.0F);
        float blue = srgbToLinear((argb & 0xFF) / 255.0F);
        materialJson.append("\"pbrMetallicRoughness\":{\"baseColorFactor\":[")
                .append(String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", red, green, blue, alpha))
                .append("],\"metallicFactor\":0.0,\"roughnessFactor\":1.0}");
        if (alpha < 0.999F) {
            materialJson.append(",\"alphaMode\":\"BLEND\"");
        }
        if (material.isEmissive()) {
            materialJson.append(",\"emissiveFactor\":[")
                    .append(String.format(Locale.US, "%.6f,%.6f,%.6f", red, green, blue)).append("]");
        }
    }

    private float srgbToLinear(float value) {
        if (value <= 0.04045F) {
            return value / 12.92F;
        }
        return (float) Math.pow((value + 0.055F) / 1.055F, 2.4F);
    }

    private void writeGLB(OutputStream out, byte[] buffer, int length) throws IOException {
        try {
            out.write(buffer, 0, length);
        } finally {
            out.close();
        }
    }

    private static BufferOutputStream gzip(BufferOutputStream input) throws IOException {
        BufferOutputStream compressed = new BufferOutputStream();
        OutputStream gzip = new GZIPOutputStream(compressed);
        try {
            gzip.write(input.buf, 0, input.len);
        } finally {
            gzip.close();
        }
        return compressed;
    }

    private static String join(ArrayList<String> items) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(items.get(i));
        }
        return out.toString();
    }

    private static void appendJsonEntry(StringBuilder builder, String entry) {
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(entry);
    }

    private static String makeBufferView(int offset, int length, Integer target) {
        if (target != null) {
            return String.format(Locale.US, "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d,\"target\":%d}", offset,
                    length, target.intValue());
        }
        return String.format(Locale.US, "{\"buffer\":0,\"byteOffset\":%d,\"byteLength\":%d}", offset, length);
    }

    private static String makeAccessor(int bufferView, int componentType, int count, String type, boolean normalized,
            Float minX, Float minY, Float minZ, Float maxX, Float maxY, Float maxZ) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"bufferView\":").append(bufferView).append(",\"componentType\":").append(componentType)
                .append(",\"count\":").append(count).append(",\"type\":\"").append(type).append("\"");
        if (normalized) {
            builder.append(",\"normalized\":true");
        }
        if ((minX != null) && (maxX != null)) {
            builder.append(",\"min\":[")
                    .append(String.format(Locale.US, "%.6f,%.6f,%.6f", minX.floatValue(), minY.floatValue(),
                            minZ.floatValue()))
                    .append("],\"max\":[")
                    .append(String.format(Locale.US, "%.6f,%.6f,%.6f", maxX.floatValue(), maxY.floatValue(),
                            maxZ.floatValue()))
                    .append(']');
        }
        builder.append('}');
        return builder.toString();
    }

    private int appendSegment(ByteArrayBuilder binary, byte[] data, Integer target) {
        int offset = binary.size();
        binary.write(data);
        binary.padTo4();
        binary.lastOffset = offset;
        binary.lastLength = data.length;
        return binary.segmentCount++;
    }

    private PrimitiveData expandToTriangleList(PrimitiveData primitive) throws IOException {
        PrimitiveData expanded = new PrimitiveData(primitive.material);
        int vertexCount = primitive.positions.size() / 3;
        for (int i = 0; i < primitive.indices.size(); i++) {
            int sourceIndex = primitive.indices.get(i);
            if ((sourceIndex < 0) || (sourceIndex >= vertexCount)) {
                throw new IOException("Invalid primitive index " + sourceIndex + " for vertex count " + vertexCount);
            }
            copyVertex(expanded, primitive, sourceIndex);
        }
        return expanded;
    }

    private void copyVertex(PrimitiveData target, PrimitiveData source, int vertexIndex) {
        int positionOffset = vertexIndex * 3;
        int uvOffset = vertexIndex * 2;
        target.addVertex(
                source.positions.get(positionOffset),
                source.positions.get(positionOffset + 1),
                source.positions.get(positionOffset + 2),
                source.normals.get(positionOffset),
                source.normals.get(positionOffset + 1),
                source.normals.get(positionOffset + 2),
                source.colors.get(positionOffset),
                source.colors.get(positionOffset + 1),
                source.colors.get(positionOffset + 2),
                source.nightLights.get(vertexIndex),
                source.texcoords.get(uvOffset),
                source.texcoords.get(uvOffset + 1));
    }

    private static byte[] toFloatBytes(FloatArrayBuilder values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < values.size(); i++) {
            buffer.putFloat(values.get(i));
        }
        return buffer.array();
    }

    private static AccessorBinary toNormalizedSignedByteBytes(FloatArrayBuilder values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < values.size(); i++) {
            float value = clamp(values.get(i), -1.0F, 1.0F);
            if (value <= -1.0F) {
                bytes[i] = (byte) -128;
            } else if (value >= 1.0F) {
                bytes[i] = (byte) 127;
            } else {
                bytes[i] = (byte) Math.round(value * 127.0F);
            }
        }
        return new AccessorBinary(bytes, COMPONENT_TYPE_BYTE, true);
    }

    private static AccessorBinary toNormalizedUnsignedByteBytes(FloatArrayBuilder values) {
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < values.size(); i++) {
            int encoded = Math.round(clamp(values.get(i), 0.0F, 1.0F) * 255.0F);
            bytes[i] = (byte) encoded;
        }
        return new AccessorBinary(bytes, COMPONENT_TYPE_UNSIGNED_BYTE, true);
    }

    private static AccessorBinary toNormalizedUnsignedShortBytes(FloatArrayBuilder values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.size() * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < values.size(); i++) {
            int encoded = Math.round(clamp(values.get(i), 0.0F, 1.0F) * 65535.0F);
            buffer.putShort((short) encoded);
        }
        return new AccessorBinary(buffer.array(), COMPONENT_TYPE_UNSIGNED_SHORT, true);
    }

    private static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static byte[] pad(byte[] bytes, byte padByte) {
        int paddedLength = (bytes.length + FOUR_BYTE_ALIGNMENT_MASK) & ~FOUR_BYTE_ALIGNMENT_MASK;
        if (paddedLength == bytes.length) {
            return bytes;
        }
        byte[] padded = new byte[paddedLength];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        for (int i = bytes.length; i < padded.length; i++) {
            padded[i] = padByte;
        }
        return padded;
    }

    private static final class ByteArrayBuilder {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int segmentCount = 0;
        int lastOffset = 0;
        int lastLength = 0;

        void write(byte[] bytes) {
            out.write(bytes, 0, bytes.length);
        }

        void padTo4() {
            while ((out.size() & FOUR_BYTE_ALIGNMENT_MASK) != 0) {
                out.write(0);
            }
        }

        int size() {
            return out.size();
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    private static final class FloatArrayBuilder {
        private float[] values = new float[64];
        private int size = 0;

        void add(float value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        float get(int index) {
            return values[index];
        }

        int size() {
            return size;
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= values.length) {
                return;
            }
            float[] expanded = new float[Math.max(capacity, values.length * 2)];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }
    }

    private static final class IntArrayBuilder {
        private int[] values = new int[64];
        private int size = 0;

        void add(int value) {
            ensureCapacity(size + 1);
            values[size++] = value;
        }

        int get(int index) {
            return values[index];
        }

        int size() {
            return size;
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= values.length) {
                return;
            }
            int[] expanded = new int[Math.max(capacity, values.length * 2)];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }
    }
}
