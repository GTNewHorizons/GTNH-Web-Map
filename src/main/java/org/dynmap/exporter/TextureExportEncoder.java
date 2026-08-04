package org.dynmap.exporter;

import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.dynmap.Color;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.utils.BufferOutputStream;
import org.dynmap.utils.DynmapBufferedImage;

public final class TextureExportEncoder {
    private TextureExportEncoder() {
    }

    public static ExportedTextureData encode(TexturePack texturePack, ExportMaterial material) throws IOException {
        DynmapBufferedImage image = DynmapBufferedImage.allocateBufferedImage(texturePack.getNativeScale(),
                texturePack.getNativeScale());
        try {
            fillTexturePixels(texturePack, material, image.argb_buf);
            return encodePixels(image, material.getMaterialType());
        } finally {
            DynmapBufferedImage.freeBufferedImage(image);
        }
    }

    private static void fillTexturePixels(TexturePack texturePack, ExportMaterial material, int[] pixels) {
        ExportMaterial[] bakedLayers = material.getBakedLayers();
        if ((bakedLayers != null) && (bakedLayers.length > 0)) {
            Arrays.fill(pixels, 0);
            int[] layerPixels = new int[pixels.length];
            for (int i = bakedLayers.length - 1; i >= 0; i--) {
                fillTexturePixels(texturePack, bakedLayers[i], layerPixels);
                blendPixels(pixels, layerPixels);
            }
            return;
        }

        int colorMultiplier = material.getColorMultiplier() & 0xFFFFFF;
        int[] texturePixels = texturePack.getTileARGB(material.getTextureIndex());
        int nativeScale = texturePack.getNativeScale();
        for (int y = 0; y < nativeScale; y++) {
            int rowOffset = y * nativeScale;
            for (int x = 0; x < nativeScale; x++) {
                int pixel = texturePixels[getRotatedPixelIndex(x, y, material.getRotation(), nativeScale)];
                if (colorMultiplier != 0xFFFFFF) {
                    pixel = Color.blendColor(pixel, colorMultiplier | 0xFF000000);
                }
                pixels[rowOffset + x] = pixel;
            }
        }
    }

    private static int getRotatedPixelIndex(int x, int y, int rotation, int nativeScale) {
        int sourceX = x;
        int sourceY = y;
        switch (rotation) {
            case OBJExport.ROT90:
                sourceX = nativeScale - 1 - y;
                sourceY = x;
                break;
            case OBJExport.ROT180:
                sourceX = nativeScale - 1 - x;
                sourceY = nativeScale - 1 - y;
                break;
            case OBJExport.ROT270:
                sourceX = y;
                sourceY = nativeScale - 1 - x;
                break;
            case OBJExport.HFLIP:
                sourceX = nativeScale - 1 - x;
                break;
            default:
                break;
        }
        return (sourceY * nativeScale) + sourceX;
    }

    private static void blendPixels(int[] destination, int[] source) {
        for (int i = 0; i < destination.length; i++) {
            int sourcePixel = source[i];
            int sourceAlpha = (sourcePixel >> 24) & 0xFF;
            if (sourceAlpha == 0) {
                continue;
            }
            if (sourceAlpha == 0xFF) {
                destination[i] = sourcePixel;
                continue;
            }

            int destinationPixel = destination[i];
            int destinationAlpha = (destinationPixel >> 24) & 0xFF;
            if (destinationAlpha == 0) {
                destination[i] = sourcePixel;
                continue;
            }

            int outputAlpha = sourceAlpha + ((destinationAlpha * (255 - sourceAlpha) + 127) / 255);
            int sourceRed = (sourcePixel >> 16) & 0xFF;
            int sourceGreen = (sourcePixel >> 8) & 0xFF;
            int sourceBlue = sourcePixel & 0xFF;
            int destinationRed = (destinationPixel >> 16) & 0xFF;
            int destinationGreen = (destinationPixel >> 8) & 0xFF;
            int destinationBlue = destinationPixel & 0xFF;
            int outputRed = ((sourceRed * sourceAlpha * 255)
                    + (destinationRed * destinationAlpha * (255 - sourceAlpha)) + (outputAlpha * 127))
                    / (outputAlpha * 255);
            int outputGreen = ((sourceGreen * sourceAlpha * 255)
                    + (destinationGreen * destinationAlpha * (255 - sourceAlpha)) + (outputAlpha * 127))
                    / (outputAlpha * 255);
            int outputBlue = ((sourceBlue * sourceAlpha * 255)
                    + (destinationBlue * destinationAlpha * (255 - sourceAlpha)) + (outputAlpha * 127))
                    / (outputAlpha * 255);
            destination[i] = (outputAlpha << 24) | (outputRed << 16) | (outputGreen << 8) | outputBlue;
        }
    }

    private static ExportedTextureData encodePixels(DynmapBufferedImage image, TexturePack.MaterialType material)
            throws IOException {
        boolean hasAlpha = false;
        boolean hasTranslucentAlpha = false;
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        double alphaWeight = 0.0;
        for (int pixel : image.argb_buf) {
            int alpha = (pixel >> 24) & 0xFF;
            red += alpha * ((pixel >> 16) & 0xFF);
            green += alpha * ((pixel >> 8) & 0xFF);
            blue += alpha * (pixel & 0xFF);
            alphaWeight += alpha;
            if (alpha != 0xFF) {
                hasAlpha = true;
                if (alpha != 0) {
                    hasTranslucentAlpha = true;
                }
            }
        }

        BufferOutputStream output = new BufferOutputStream();
        ImageIO.setUseCache(false);
        ImageIO.write(image.buf_img, "png", output);

        ExportedTextureData data = new ExportedTextureData();
        data.imagePng = Arrays.copyOf(output.buf, output.len);
        data.diffuseColor = (alphaWeight > 0)
                ? new Color((int) (red / alphaWeight), (int) (green / alphaWeight), (int) (blue / alphaWeight))
                : new Color();
        data.material = material;
        data.hasAlpha = hasAlpha;
        data.hasTranslucentAlpha = hasTranslucentAlpha;
        if (hasAlpha) {
            for (int i = 0; i < image.argb_buf.length; i++) {
                int alpha = (image.argb_buf[i] >> 24) & 0xFF;
                image.argb_buf[i] = (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha;
            }
            output.reset();
            ImageIO.write(image.buf_img, "png", output);
            data.alphaPng = Arrays.copyOf(output.buf, output.len);
        }
        return data;
    }
}
