package org.dynmap.exporter;

import org.dynmap.hdmap.TexturePack;

public class ExportMaterial {
    private final String materialId;
    private final int textureIndex;
    private final int colorMultiplier;
    private final int rotation;
    private final TexturePack.MaterialType materialType;
    private final boolean emissive;

    public ExportMaterial(String materialId, int textureIndex, int colorMultiplier, int rotation,
            TexturePack.MaterialType materialType, boolean emissive) {
        this.materialId = materialId;
        this.textureIndex = textureIndex;
        this.colorMultiplier = colorMultiplier;
        this.rotation = rotation;
        this.materialType = materialType;
        this.emissive = emissive;
    }

    public String getMaterialId() {
        return materialId;
    }

    public int getTextureIndex() {
        return textureIndex;
    }

    public int getColorMultiplier() {
        return colorMultiplier;
    }

    public int getRotation() {
        return rotation;
    }

    public TexturePack.MaterialType getMaterialType() {
        return materialType;
    }

    public boolean isEmissive() {
        return emissive;
    }

    public static ExportMaterial fromLegacyString(String material) {
        if (material == null) {
            return null;
        }
        int rotation = 0;
        int rotationIndex = material.indexOf('@');
        if (rotationIndex >= 0) {
            rotation = material.charAt(rotationIndex + 1) - '0';
            material = material.substring(0, rotationIndex);
        }
        return new ExportMaterial(material, -1, 0xFFFFFF, rotation, null, false);
    }
}
