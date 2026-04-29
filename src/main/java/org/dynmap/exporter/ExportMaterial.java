package org.dynmap.exporter;

import org.dynmap.hdmap.TexturePack;

public class ExportMaterial {
    private final String materialId;
    private final int textureIndex;
    private final int colorMultiplier;
    private final int rotation;
    private final TexturePack.MaterialType materialType;
    private final boolean emissive;
    private final ExportMaterial[] bakedLayers;
    private final Integer solidColorArgb;

    public ExportMaterial(String materialId, int textureIndex, int colorMultiplier, int rotation,
            TexturePack.MaterialType materialType, boolean emissive) {
        this(materialId, textureIndex, colorMultiplier, rotation, materialType, emissive, null, null);
    }

    private ExportMaterial(String materialId, int textureIndex, int colorMultiplier, int rotation,
            TexturePack.MaterialType materialType, boolean emissive, ExportMaterial[] bakedLayers, Integer solidColorArgb) {
        this.materialId = materialId;
        this.textureIndex = textureIndex;
        this.colorMultiplier = colorMultiplier;
        this.rotation = rotation;
        this.materialType = materialType;
        this.emissive = emissive;
        this.bakedLayers = bakedLayers;
        this.solidColorArgb = solidColorArgb;
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

    public ExportMaterial[] getBakedLayers() {
        return bakedLayers;
    }

    public boolean isSolidColor() {
        return solidColorArgb != null;
    }

    public int getSolidColorArgb() {
        return (solidColorArgb != null) ? solidColorArgb.intValue() : 0xFFFFFFFF;
    }

    public static ExportMaterial bakeLayers(ExportMaterial[] layers) {
        if ((layers == null) || (layers.length == 0)) {
            return null;
        }
        if (layers.length == 1) {
            return layers[0];
        }
        StringBuilder materialId = new StringBuilder("baked");
        TexturePack.MaterialType materialType = null;
        for (ExportMaterial layer : layers) {
            materialId.append("__").append(layer.materialId);
            if (layer.rotation != 0) {
                materialId.append("_r").append(layer.rotation);
            }
            if (layer.materialType != null) {
                materialType = layer.materialType;
            }
        }
        return new ExportMaterial(materialId.toString(), -1, 0xFFFFFF, 0, materialType, false, layers.clone(), null);
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

    public static ExportMaterial solidColor(String materialId, int argb, TexturePack.MaterialType materialType,
            boolean emissive) {
        return new ExportMaterial(materialId, -1, 0xFFFFFF, 0, materialType, emissive, null, Integer.valueOf(argb));
    }
}
