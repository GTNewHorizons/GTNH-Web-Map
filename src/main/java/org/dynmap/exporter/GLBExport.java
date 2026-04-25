package org.dynmap.exporter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.dynmap.utils.PatchDefinition;

public class GLBExport implements BlockModelExportSink {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final class PrimitiveData {
        final ExportMaterial material;
        final ArrayList<Float> positions = new ArrayList<Float>();
        final ArrayList<Float> texcoords = new ArrayList<Float>();
        final ArrayList<Integer> indices = new ArrayList<Integer>();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        PrimitiveData(ExportMaterial material) {
            this.material = material;
        }

        int addVertex(float x, float y, float z, float u, float v) {
            int index = positions.size() / 3;
            positions.add(Float.valueOf(x));
            positions.add(Float.valueOf(y));
            positions.add(Float.valueOf(z));
            texcoords.add(Float.valueOf(u));
            texcoords.add(Float.valueOf(v));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            return index;
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

    public boolean processExport(DynmapCommandSender sender) {
        try {
            TexturePack texturePack = shader.getTexturePackForExport();
            if (texturePack == null) {
                throw new IOException("Export unsupported - invalid texture pack");
            }
            primitives.clear();
            BlockModelExporter exporter = new BlockModelExporter(world, core, shader);
            exporter.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
            exporter.export(this);
            writeGLB(texturePack);
            sender.sendMessage("Export completed - " + destination.getPath());
            return true;
        } catch (IOException iox) {
            sender.sendMessage("Export failed: " + iox.getMessage());
            return false;
        }
    }

    @Override
    public void setChunk(int chunkX, int chunkZ) throws IOException {
    }

    @Override
    public void setBlock(int blockId, int blockData) throws IOException {
    }

    @Override
    public void addPatch(PatchDefinition patch, double x, double y, double z, ExportMaterial material) throws IOException {
        if (material == null) {
            return;
        }
        PrimitiveData primitive = primitives.get(material.getMaterialId());
        if (primitive == null) {
            primitive = new PrimitiveData(material);
            primitives.put(material.getMaterialId(), primitive);
        }
        ExportPatchGeometry.Geometry geometry = ExportPatchGeometry.build(patch, x, y, z, material.getRotation());
        addGeometry(primitive, geometry);
    }

    private void addGeometry(PrimitiveData primitive, ExportPatchGeometry.Geometry geometry) {
        switch (geometry.sideVisible) {
            case TOP:
                addFrontFace(primitive, geometry.xyz, geometry.uv, geometry.vertexCount);
                break;
            case BOTTOM:
                addBackFace(primitive, geometry.xyz, geometry.uv, geometry.vertexCount);
                break;
            case BOTH:
                addFrontFace(primitive, geometry.xyz, geometry.uv, geometry.vertexCount);
                addBackFace(primitive, geometry.xyz, geometry.uv, geometry.vertexCount);
                break;
            case FLIP:
                addFrontFace(primitive, geometry.xyz, geometry.uv, geometry.vertexCount);
                addBackFace(primitive, geometry.xyz, buildFlipBackUVs(geometry.uv, geometry.vertexCount),
                        geometry.vertexCount);
                break;
        }
    }

    private void addFrontFace(PrimitiveData primitive, double[] xyz, double[] uv, int vertexCount) {
        int v0 = addVertex(primitive, xyz, uv, 0);
        int v1 = addVertex(primitive, xyz, uv, 1);
        int v2 = addVertex(primitive, xyz, uv, 2);
        primitive.indices.add(Integer.valueOf(v0));
        primitive.indices.add(Integer.valueOf(v1));
        primitive.indices.add(Integer.valueOf(v2));
        if (vertexCount == 4) {
            int v3 = addVertex(primitive, xyz, uv, 3);
            primitive.indices.add(Integer.valueOf(v0));
            primitive.indices.add(Integer.valueOf(v2));
            primitive.indices.add(Integer.valueOf(v3));
        }
    }

    private void addBackFace(PrimitiveData primitive, double[] xyz, double[] uv, int vertexCount) {
        if (vertexCount == 4) {
            int v3 = addVertex(primitive, xyz, uv, 3);
            int v2 = addVertex(primitive, xyz, uv, 2);
            int v1 = addVertex(primitive, xyz, uv, 1);
            int v0 = addVertex(primitive, xyz, uv, 0);
            primitive.indices.add(Integer.valueOf(v3));
            primitive.indices.add(Integer.valueOf(v2));
            primitive.indices.add(Integer.valueOf(v1));
            primitive.indices.add(Integer.valueOf(v3));
            primitive.indices.add(Integer.valueOf(v1));
            primitive.indices.add(Integer.valueOf(v0));
        } else {
            int v2 = addVertex(primitive, xyz, uv, 2);
            int v1 = addVertex(primitive, xyz, uv, 1);
            int v0 = addVertex(primitive, xyz, uv, 0);
            primitive.indices.add(Integer.valueOf(v2));
            primitive.indices.add(Integer.valueOf(v1));
            primitive.indices.add(Integer.valueOf(v0));
        }
    }

    private double[] buildFlipBackUVs(double[] uv, int vertexCount) {
        if (vertexCount == 4) {
            return new double[] { uv[2], uv[3], uv[0], uv[1], uv[6], uv[7], uv[4], uv[5] };
        }
        return new double[] { uv[0], uv[1], uv[4], uv[5], uv[2], uv[3] };
    }

    private int addVertex(PrimitiveData primitive, double[] xyz, double[] uv, int vertexIndex) {
        int xyzOffset = vertexIndex * 3;
        int uvOffset = vertexIndex * 2;
        return primitive.addVertex(transform(xyz[xyzOffset], originX), transform(xyz[xyzOffset + 1], originY),
                transform(xyz[xyzOffset + 2], originZ), (float) uv[uvOffset], (float) uv[uvOffset + 1]);
    }

    private float transform(double coordinate, double origin) {
        return (float) ((coordinate - origin) * scale);
    }

    private void writeGLB(TexturePack texturePack) throws IOException {
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
        StringBuilder texturesJson = new StringBuilder();
        StringBuilder materialsJson = new StringBuilder();

        for (int i = 0; i < primitiveList.size(); i++) {
            PrimitiveData primitive = primitiveList.get(i);
            ExportedTextureData texture = textures.get(i);

            int positionView = appendSegment(binary, toFloatBytes(primitive.positions), 34962);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, 34962));
            int positionAccessor = accessors.size();
            accessors.add(makeAccessor(positionView, 5126, primitive.positions.size() / 3, "VEC3", primitive.minX,
                    primitive.minY, primitive.minZ, primitive.maxX, primitive.maxY, primitive.maxZ));

            int uvView = appendSegment(binary, toFloatBytes(primitive.texcoords), 34962);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, 34962));
            int uvAccessor = accessors.size();
            accessors.add(makeAccessor(uvView, 5126, primitive.texcoords.size() / 2, "VEC2", null, null, null, null,
                    null, null));

            int indexView = appendSegment(binary, toIntBytes(primitive.indices), 34963);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, 34963));
            int indexAccessor = accessors.size();
            accessors.add(makeAccessor(indexView, 5125, primitive.indices.size(), "SCALAR", null, null, null, null,
                    null, null));

            int imageView = appendSegment(binary, texture.imagePng, null);
            bufferViews.add(makeBufferView(binary.lastOffset, binary.lastLength, null));
            int imageIndex = i;
            appendJsonEntry(imagesJson, String.format(Locale.US, "{\"bufferView\":%d,\"mimeType\":\"image/png\"}",
                    imageView));
            appendJsonEntry(texturesJson, String.format(Locale.US, "{\"source\":%d}", imageIndex));

            StringBuilder materialJson = new StringBuilder();
            materialJson.append("{\"name\":\"").append(primitive.material.getMaterialId())
                    .append("\",\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":").append(i)
                    .append("},\"metallicFactor\":0.0,\"roughnessFactor\":1.0}");
            if (texture.hasAlpha) {
                materialJson.append(",\"alphaMode\":\"BLEND\"");
            }
            if (primitive.material.isEmissive()) {
                materialJson.append(",\"emissiveFactor\":[1.0,1.0,1.0],\"emissiveTexture\":{\"index\":").append(i)
                        .append("}");
            }
            materialJson.append("}");
            appendJsonEntry(materialsJson, materialJson.toString());

            appendJsonEntry(primitivesJson, String.format(Locale.US,
                    "{\"attributes\":{\"POSITION\":%d,\"TEXCOORD_0\":%d},\"indices\":%d,\"material\":%d}", positionAccessor,
                    uvAccessor, indexAccessor, i));
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"asset\":{\"version\":\"2.0\",\"generator\":\"GTNH-Web-Map\"},");
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
        json.append("\"textures\":[");
        json.append(texturesJson);
        json.append("],");
        json.append("\"materials\":[");
        json.append(materialsJson);
        json.append("]}");

        byte[] jsonBytes = pad(json.toString().getBytes(UTF8), (byte) 0x20);
        byte[] binBytes = pad(binary.toByteArray(), (byte) 0x00);
        int totalLength = 12 + 8 + jsonBytes.length + 8 + binBytes.length;

        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination));
        try {
            out.write(intToBytes(0x46546C67));
            out.write(intToBytes(2));
            out.write(intToBytes(totalLength));
            out.write(intToBytes(jsonBytes.length));
            out.write(intToBytes(0x4E4F534A));
            out.write(jsonBytes);
            out.write(intToBytes(binBytes.length));
            out.write(intToBytes(0x004E4942));
            out.write(binBytes);
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

    private static String makeAccessor(int bufferView, int componentType, int count, String type, Float minX, Float minY,
            Float minZ, Float maxX, Float maxY, Float maxZ) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"bufferView\":").append(bufferView).append(",\"componentType\":").append(componentType)
                .append(",\"count\":").append(count).append(",\"type\":\"").append(type).append("\"");
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

    private static byte[] toFloatBytes(ArrayList<Float> values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Float value : values) {
            buffer.putFloat(value.floatValue());
        }
        return buffer.array();
    }

    private static byte[] toIntBytes(ArrayList<Integer> values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Integer value : values) {
            buffer.putInt(value.intValue());
        }
        return buffer.array();
    }

    private static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }

    private static byte[] pad(byte[] bytes, byte padByte) {
        int paddedLength = (bytes.length + 3) & ~3;
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
            while ((out.size() & 3) != 0) {
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
}
