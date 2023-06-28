package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public class ExtraUtilitiesGreenScreenProcessor extends CustomTextureProcessor {

    int[] colorMul = new int[]{0xFFFFFF, 0xFF7F00, 0xFF00FF, 0x007FD8, 0xFFFF00, 0x00FF00, 0xFF99A5, 0x7F7F7F, 0xCCCCCC, 0x00FFFF, 0xB233FF, 0x0000FF, 0x7F3300, 0x009900, 0xFF0000, 0xFFFFFF};
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {
        for(int i = 0; i < 16; i++) {
            int blended[] = new int[native_scale * native_scale];
            TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, blended);

            if(colorMul[i] != 0xFFFFFF) {
                int mulR = (colorMul[i] & 0xFF0000) >> 16;
                int mulG = (colorMul[i] & 0x00FF00) >> 8;
                int mulB = (colorMul[i] & 0x0000FF);
                for (int p = 0; p < blended.length; p++) {
                    int srcR = (blended[p] & 0xFF0000) >> 16;
                    int srcG = (blended[p] & 0x00FF00) >> 8;
                    int srcB = (blended[p] & 0x0000FF);

                    int dstR = (mulR * srcR / 255) & 0xFF;
                    int dstG = (mulG * srcG / 255) & 0xFF;
                    int dstB = (mulB * srcB / 255) & 0xFF;

                    blended[p] = (blended[p] & 0xFF000000) | (dstR << 16) | (dstG << 8) | dstB;
                }
            }

            texturePack.setTileARGB(tileToDyntile[i], blended);
        }
    }

    @Override
    public int getTextureCount() {
        return 16;
    }
}
