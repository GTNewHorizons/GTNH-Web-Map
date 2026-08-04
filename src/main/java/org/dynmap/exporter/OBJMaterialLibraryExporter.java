package org.dynmap.exporter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.dynmap.Color;
import org.dynmap.hdmap.TexturePack;

public final class OBJMaterialLibraryExporter {
    private static final class ExportedTexture {
        String filename;
        String alphaFilename;
        Color diffuseColor;
        TexturePack.MaterialType material;
        boolean emissive;
    }

    private OBJMaterialLibraryExporter() {
    }

    public static void export(TexturePack texturePack, OBJExport export, String name, Iterable<ExportMaterial> materials)
            throws IOException {
        Map<String, ExportedTexture> textures = new HashMap<String, ExportedTexture>();
        for (ExportMaterial material : materials) {
            if ((material != null) && !textures.containsKey(material.getMaterialId())) {
                textures.put(material.getMaterialId(), exportTexture(texturePack, export, name, material));
            }
        }
        writeMaterialLibrary(export, name, textures);
    }

    private static ExportedTexture exportTexture(TexturePack texturePack, OBJExport export, String name,
            ExportMaterial material) throws IOException {
        ExportedTextureData data =
                material.hasCustomTexture() ? material.getCustomTextureData() : texturePack.exportTexture(material);
        String filename = name + "/" + material.getMaterialId() + ".png";
        export.startExportedFile(filename);
        export.addBytesToExportedFile(data.imagePng, 0, data.imagePng.length);
        export.finishExportedFile();

        String alphaFilename = null;
        if (data.alphaPng != null) {
            alphaFilename = name + "/" + material.getMaterialId() + "_a.png";
            export.startExportedFile(alphaFilename);
            export.addBytesToExportedFile(data.alphaPng, 0, data.alphaPng.length);
            export.finishExportedFile();
        }

        ExportedTexture texture = new ExportedTexture();
        texture.filename = filename;
        texture.alphaFilename = alphaFilename;
        texture.diffuseColor = data.diffuseColor;
        texture.material = material.getMaterialType();
        texture.emissive = material.isEmissive();
        return texture;
    }

    private static void writeMaterialLibrary(OBJExport export, String name, Map<String, ExportedTexture> textures)
            throws IOException {
        export.startExportedFile(name + ".mtl");
        for (String id : new TreeSet<String>(textures.keySet())) {
            ExportedTexture texture = textures.get(id);
            String lines = "newmtl " + id + "\n";
            lines += String.format(Locale.US, "Ka %.3f %.3f %.3f\n", (double) texture.diffuseColor.getRed() / 256.0,
                    (double) texture.diffuseColor.getGreen() / 256.0, (double) texture.diffuseColor.getBlue() / 256.0);
            lines += String.format(Locale.US, "Kd %.3f %.3f %.3f\n", (double) texture.diffuseColor.getRed() / 256.0,
                    (double) texture.diffuseColor.getGreen() / 256.0, (double) texture.diffuseColor.getBlue() / 256.0);
            lines += "map_Kd " + texture.filename + "\n";
            lines += "map_Ka " + texture.filename + "\n";
            if (texture.alphaFilename != null) {
                lines += "map_d " + texture.alphaFilename + "\n";
            }
            if (texture.emissive) {
                lines += "Ke 1.000 1.000 1.000\n";
                lines += "map_Ke " + texture.filename + "\n";
            }
            if (texture.material != null) {
                lines += String.format(Locale.US, "Ni %.3f\n", texture.material.Ni);
                lines += String.format(Locale.US, "Ns %.3f\n", texture.material.Ns);
                lines += "Ks 0.500 0.500 0.500\n";
                lines += String.format("illum %d\n", texture.material.illum);
            } else {
                lines += "Ks 0.000 0.000 0.000\n";
            }
            lines += "\n";
            export.addStringToExportedFile(lines);
        }
        export.finishExportedFile();
    }
}
