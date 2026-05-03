package org.dynmap.exporter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.hdmap.TexturePack.ExportedTextureData;

final class ExportMaterialColorResolver {
    private final TexturePack exportTexturePack;
    private final Map<String, Integer> averageColorCache = new HashMap<String, Integer>();
    private final Map<String, ExportMaterial> solidColorMaterialCache = new HashMap<String, ExportMaterial>();

    ExportMaterialColorResolver(TexturePack exportTexturePack) {
        this.exportTexturePack = exportTexturePack;
    }

    ExportMaterial toSolidColorMaterial(ExportMaterial material) throws IOException {
        if ((material == null) || material.isSolidColor()) {
            return material;
        }
        int argb = computeAverageColor(material);
        String solidKey = getSolidMaterialKey(argb, material.isEmissive());
        ExportMaterial cached = solidColorMaterialCache.get(solidKey);
        if (cached != null) {
            return cached;
        }
        ExportMaterial solid =
                ExportMaterial.solidColor("solid_" + solidKey, argb, material.getMaterialType(), material.isEmissive());
        solidColorMaterialCache.put(solidKey, solid);
        return solid;
    }

    String getSolidMaterialKey(int argb, boolean emissive) {
        return Integer.toHexString(argb) + "|" + emissive;
    }

    int computeAverageColor(ExportMaterial material) throws IOException {
        String cacheKey = getMaterialCacheKey(material);
        Integer cached = averageColorCache.get(cacheKey);
        if (cached != null) {
            return cached.intValue();
        }
        int blendColor = getBlendColorArgb(material);
        if (blendColor >= 0) {
            averageColorCache.put(cacheKey, Integer.valueOf(blendColor));
            return blendColor;
        }
        if (exportTexturePack == null) {
            return 0xFFFFFFFF;
        }
        ExportedTextureData texture = exportTexturePack.exportTexture(material);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(texture.imagePng));
        if (image == null) {
            return 0xFFFFFFFF;
        }
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        for (int pixel : pixels) {
            if (((pixel >> 24) & 0xFF) <= 127) {
                continue;
            }
            red += (pixel >> 16) & 0xFF;
            green += (pixel >> 8) & 0xFF;
            blue += pixel & 0xFF;
            count++;
        }
        if (count <= 0) {
            averageColorCache.put(cacheKey, Integer.valueOf(0xFFFFFFFF));
            return 0xFFFFFFFF;
        }
        int argb = 0xFF000000 | (((int) (red / count)) << 16) | (((int) (green / count)) << 8) | ((int) (blue / count));
        averageColorCache.put(cacheKey, Integer.valueOf(argb));
        return argb;
    }

    private String getMaterialCacheKey(ExportMaterial material) {
        if (material == null) {
            return "null";
        }
        StringBuilder key = new StringBuilder();
        appendMaterialCacheKey(key, material);
        return key.toString();
    }

    private void appendMaterialCacheKey(StringBuilder key, ExportMaterial material) {
        key.append(material.getMaterialId()).append('|').append(material.getTextureIndex()).append('|')
                .append(material.getColorMultiplier()).append('|').append(material.getRotation()).append('|')
                .append(material.getMaterialType()).append('|').append(material.isEmissive());
        ExportMaterial[] bakedLayers = material.getBakedLayers();
        if (bakedLayers != null) {
            key.append('[');
            for (ExportMaterial layer : bakedLayers) {
                appendMaterialCacheKey(key, layer);
                key.append(';');
            }
            key.append(']');
        }
    }

    private int getBlendColorArgb(ExportMaterial material) {
        if (material == null) {
            return -1;
        }
        int colorMultiplier = material.getColorMultiplier() & 0xFFFFFF;
        if (colorMultiplier != 0xFFFFFF) {
            return 0xFF000000 | colorMultiplier;
        }
        ExportMaterial[] bakedLayers = material.getBakedLayers();
        if (bakedLayers != null) {
            for (ExportMaterial layer : bakedLayers) {
                int layerColor = getBlendColorArgb(layer);
                if (layerColor >= 0) {
                    return layerColor;
                }
            }
        }
        return -1;
    }
}
