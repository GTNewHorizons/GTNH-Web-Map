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
    private static final class SampledTextureData {
        final int width;
        final int height;
        final int[] pixels;

        SampledTextureData(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private final TexturePack exportTexturePack;
    private final Map<String, Integer> averageColorCache = new HashMap<String, Integer>();
    private final Map<String, ExportMaterial> solidColorMaterialCache = new HashMap<String, ExportMaterial>();
    private final Map<String, SampledTextureData> sampledTextureCache = new HashMap<String, SampledTextureData>();

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

    int sampleColor(ExportMaterial material, double u, double v, int targetDetail) throws IOException {
        if (material == null) {
            return 0xFFFFFFFF;
        }
        if (material.isSolidColor()) {
            return material.getSolidColorArgb();
        }

        SampledTextureData sampledTexture = getSampledTextureData(material);
        if (sampledTexture == null) {
            return computeAverageColor(material);
        }

        double clampedU = Math.max(0.0, Math.min(0.999999, u));
        double clampedV = Math.max(0.0, Math.min(0.999999, v));
        int sampleX = Math.min(sampledTexture.width - 1, Math.max(0, (int) Math.floor(clampedU * sampledTexture.width)));
        int sampleY = Math.min(sampledTexture.height - 1, Math.max(0, (int) Math.floor(clampedV * sampledTexture.height)));
        if ((targetDetail > 0) && ((targetDetail * 2) < sampledTexture.width) && ((targetDetail * 2) < sampledTexture.height)) {
            return sampleAveragedNeighborColor(sampledTexture, clampedU, clampedV, sampleX, sampleY);
        }
        return sampledTexture.pixels[(sampleY * sampledTexture.width) + sampleX];
    }

    private int sampleAveragedNeighborColor(SampledTextureData sampledTexture, double u, double v, int fallbackX, int fallbackY) {
        double scaledX = u * sampledTexture.width;
        double scaledY = v * sampledTexture.height;
        int sampleX0 = clampSampleCoordinate((int) Math.floor(scaledX - 0.5), sampledTexture.width);
        int sampleX1 = clampSampleCoordinate(sampleX0 + 1, sampledTexture.width);
        int sampleY0 = clampSampleCoordinate((int) Math.floor(scaledY - 0.5), sampledTexture.height);
        int sampleY1 = clampSampleCoordinate(sampleY0 + 1, sampledTexture.height);
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        int[] sampleXs = new int[] { sampleX0, sampleX1 };
        int[] sampleYs = new int[] { sampleY0, sampleY1 };
        for (int sampleY : sampleYs) {
            for (int sampleX : sampleXs) {
                int pixel = sampledTexture.pixels[(sampleY * sampledTexture.width) + sampleX];
                if (((pixel >> 24) & 0xFF) <= 0) {
                    continue;
                }
                alpha += (pixel >> 24) & 0xFF;
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
                count++;
            }
        }
        if (count <= 0) {
            return sampledTexture.pixels[(fallbackY * sampledTexture.width) + fallbackX];
        }
        return (((int) (alpha / count)) << 24) | (((int) (red / count)) << 16) | (((int) (green / count)) << 8)
                | ((int) (blue / count));
    }

    private int clampSampleCoordinate(int value, int limit) {
        return Math.min(limit - 1, Math.max(0, value));
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

    private SampledTextureData getSampledTextureData(ExportMaterial material) throws IOException {
        String cacheKey = getMaterialCacheKey(material);
        if (sampledTextureCache.containsKey(cacheKey)) {
            return sampledTextureCache.get(cacheKey);
        }
        if (exportTexturePack == null) {
            return null;
        }
        if ((material.getTextureIndex() < 0) && !material.hasCustomTexture() && (material.getBakedLayers() == null)) {
            return null;
        }

        ExportedTextureData texture = exportTexturePack.exportTexture(material);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(texture.imagePng));
        if (image == null) {
            sampledTextureCache.put(cacheKey, null);
            return null;
        }

        SampledTextureData sampledTexture = new SampledTextureData(image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
        sampledTextureCache.put(cacheKey, sampledTexture);
        return sampledTexture;
    }
}
