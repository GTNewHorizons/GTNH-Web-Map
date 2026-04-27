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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int FILTER_NEAREST = 9728;
    private static final int WRAP_CLAMP_TO_EDGE = 33071;
    private static final int ARRAY_BUFFER_TARGET = 34962;
    private static final int ELEMENT_ARRAY_BUFFER_TARGET = 34963;
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

        int addVertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float u,
                float v) {
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

    public boolean processExport(DynmapCommandSender sender) {
        try {
            BufferOutputStream glb = exportToBuffer();
            if (glb == null) {
                throw new IOException("Export produced no geometry");
            }
            writeGLB(new BufferedOutputStream(new FileOutputStream(destination)), glb.buf, glb.len);
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

    @Override
    public void setChunk(int chunkX, int chunkZ) throws IOException {
    }

    @Override
    public void setBlock(int blockId, int blockData) throws IOException {
    }

    @Override
    public void addPatch(PatchDefinition patch, double x, double y, double z, ExportMaterial material, float[] vertexColors)
            throws IOException {
        if (material == null) {
            return;
        }
        PrimitiveData primitive = primitives.get(material.getMaterialId());
        if (primitive == null) {
            primitive = new PrimitiveData(material);
            primitives.put(material.getMaterialId(), primitive);
        }
        ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(patch, x, y, z, material.getRotation());
        addGeometry(primitive, geometry, vertexColors);
    }

    private void addGeometry(PrimitiveData primitive, ExportPatchGeometry.Geometry geometry, float[] vertexColors) {
        switch (geometry.sideVisible) {
            case TOP:
                addFrontFace(primitive, geometry.xyz, geometry.uv, vertexColors, geometry.vertexCount);
                break;
            case BOTTOM:
                addBackFace(primitive, geometry.xyz, geometry.uv, vertexColors, geometry.vertexCount);
                break;
            case BOTH:
                addFrontFace(primitive, geometry.xyz, geometry.uv, vertexColors, geometry.vertexCount);
                addBackFace(primitive, geometry.xyz, geometry.uv, vertexColors, geometry.vertexCount);
                break;
            case FLIP:
                addFrontFace(primitive, geometry.xyz, geometry.uv, vertexColors, geometry.vertexCount);
                addBackFace(primitive, geometry.xyz, buildFlipBackUVs(geometry.uv, geometry.vertexCount), vertexColors,
                        geometry.vertexCount);
                break;
        }
    }

    private void addFrontFace(PrimitiveData primitive, double[] xyz, double[] uv, float[] vertexColors, int vertexCount) {
        FaceNormal normal = buildFaceNormal(xyz);
        int v0 = addVertex(primitive, xyz, uv, vertexColors, normal, 0);
        int v1 = addVertex(primitive, xyz, uv, vertexColors, normal, 1);
        int v2 = addVertex(primitive, xyz, uv, vertexColors, normal, 2);
        primitive.indices.add(v0);
        primitive.indices.add(v1);
        primitive.indices.add(v2);
        if (vertexCount == 4) {
            int v3 = addVertex(primitive, xyz, uv, vertexColors, normal, 3);
            primitive.indices.add(v0);
            primitive.indices.add(v2);
            primitive.indices.add(v3);
        }
    }

    private void addBackFace(PrimitiveData primitive, double[] xyz, double[] uv, float[] vertexColors, int vertexCount) {
        FaceNormal normal = buildFaceNormal(xyz).invert();
        if (vertexCount == 4) {
            int v3 = addVertex(primitive, xyz, uv, vertexColors, normal, 3);
            int v2 = addVertex(primitive, xyz, uv, vertexColors, normal, 2);
            int v1 = addVertex(primitive, xyz, uv, vertexColors, normal, 1);
            int v0 = addVertex(primitive, xyz, uv, vertexColors, normal, 0);
            primitive.indices.add(v3);
            primitive.indices.add(v2);
            primitive.indices.add(v1);
            primitive.indices.add(v3);
            primitive.indices.add(v1);
            primitive.indices.add(v0);
        } else {
            int v2 = addVertex(primitive, xyz, uv, vertexColors, normal, 2);
            int v1 = addVertex(primitive, xyz, uv, vertexColors, normal, 1);
            int v0 = addVertex(primitive, xyz, uv, vertexColors, normal, 0);
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

    private int addVertex(PrimitiveData primitive, double[] xyz, double[] uv, float[] vertexColors, FaceNormal normal,
            int vertexIndex) {
        int xyzOffset = vertexIndex * 3;
        int uvOffset = vertexIndex * 2;
        int colorOffset = vertexIndex * 3;
        return primitive.addVertex(transform(xyz[xyzOffset], originX), transform(xyz[xyzOffset + 1], originY),
                transform(xyz[xyzOffset + 2], originZ), normal.x, normal.y, normal.z, vertexColors[colorOffset],
                vertexColors[colorOffset + 1], vertexColors[colorOffset + 2], (float) uv[uvOffset],
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

    private BufferOutputStream buildGLB(TexturePack texturePack) throws IOException {
        ArrayList<PrimitiveData> primitiveList = new ArrayList<PrimitiveData>(primitives.values());
        ArrayList<ExportedTextureData> textures = new ArrayList<ExportedTextureData>(primitiveList.size());
        for (PrimitiveData primitive : primitiveList) {
            textures.add(texturePack.exportTexture(primitive.material));
        }

        ArrayList<String> bufferViews = new ArrayList<String>();
        ArrayList<String> accessors = new ArrayList<String>();
        ByteArrayBuilder binary = new ByteArrayBuilder();
        StringBuilder primitivesJson = new StringBuilder();
        StringBuilder imagesJson = new StringBuilder();
        StringBuilder samplersJson = new StringBuilder();
        StringBuilder texturesJson = new StringBuilder();
        StringBuilder materialsJson = new StringBuilder();

        appendJsonEntry(samplersJson, String.format(Locale.US,
                "{\"magFilter\":%d,\"minFilter\":%d,\"wrapS\":%d,\"wrapT\":%d}", FILTER_NEAREST, FILTER_NEAREST,
                WRAP_CLAMP_TO_EDGE, WRAP_CLAMP_TO_EDGE));

        for (int i = 0; i < primitiveList.size(); i++) {
            PrimitiveData primitive = primitiveList.get(i);
            ExportedTextureData texture = textures.get(i);
            AccessorBinary normalBinary = toNormalizedSignedByteBytes(primitive.normals);
            AccessorBinary colorBinary = toNormalizedUnsignedByteBytes(primitive.colors);
            AccessorBinary uvBinary = toNormalizedUnsignedShortBytes(primitive.texcoords);
            AccessorBinary indexBinary = toIndexBytes(primitive.indices);

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

            int indexView = appendSegment(binary, indexBinary.bytes, ELEMENT_ARRAY_BUFFER_TARGET);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, ELEMENT_ARRAY_BUFFER_TARGET));
            int indexAccessor = accessors.size();
            accessors.add(makeAccessor(indexView, indexBinary.componentType, primitive.indices.size(), "SCALAR",
                    indexBinary.normalized, null, null, null, null, null, null));

            int imageView = appendSegment(binary, texture.imagePng, null);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, null));
            int imageIndex = i;
            appendJsonEntry(imagesJson, String.format(Locale.US, "{\"bufferView\":%d,\"mimeType\":\"image/png\"}",
                    imageView));
            appendJsonEntry(texturesJson, String.format(Locale.US, "{\"sampler\":0,\"source\":%d}", imageIndex));

            StringBuilder materialJson = new StringBuilder();
            materialJson.append("{\"name\":\"").append(primitive.material.getMaterialId())
                    .append("\",\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":").append(i)
                    .append("},\"metallicFactor\":0.0,\"roughnessFactor\":1.0}");
            if (texture.hasAlpha) {
                if (texture.hasTranslucentAlpha) {
                    materialJson.append(",\"alphaMode\":\"BLEND\"");
                } else {
                    materialJson.append(",\"alphaMode\":\"MASK\",\"alphaCutoff\":0.5");
                }
            }
            if (primitive.material.isEmissive()) {
                materialJson.append(",\"emissiveFactor\":[1.0,1.0,1.0],\"emissiveTexture\":{\"index\":").append(i)
                        .append("}");
            }
            materialJson.append("}");
            appendJsonEntry(materialsJson, materialJson.toString());

            appendJsonEntry(primitivesJson, String.format(Locale.US,
                    "{\"attributes\":{\"POSITION\":%d,\"NORMAL\":%d,\"COLOR_0\":%d,\"TEXCOORD_0\":%d},\"indices\":%d,\"material\":%d}",
                    positionAccessor, normalAccessor, colorAccessor, uvAccessor, indexAccessor, i));
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

    private void writeGLB(OutputStream out, byte[] buffer, int length) throws IOException {
        try {
            out.write(buffer, 0, length);
        } finally {
            out.close();
        }
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

    private static AccessorBinary toIndexBytes(IntArrayBuilder values) {
        int maxValue = 0;
        for (int i = 0; i < values.size(); i++) {
            maxValue = Math.max(maxValue, values.get(i));
        }
        if (maxValue <= 0xFF) {
            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                bytes[i] = (byte) values.get(i);
            }
            return new AccessorBinary(bytes, COMPONENT_TYPE_UNSIGNED_BYTE, false);
        }
        if (maxValue <= 0xFFFF) {
            ByteBuffer buffer = ByteBuffer.allocate(values.size() * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < values.size(); i++) {
                buffer.putShort((short) values.get(i));
            }
            return new AccessorBinary(buffer.array(), COMPONENT_TYPE_UNSIGNED_SHORT, false);
        }
        ByteBuffer buffer = ByteBuffer.allocate(values.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < values.size(); i++) {
            buffer.putInt(values.get(i));
        }
        return new AccessorBinary(buffer.array(), COMPONENT_TYPE_UNSIGNED_INT, false);
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
