package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public class AE2MonitorProcessor extends CustomTextureProcessor {

    int[] otherColorMul = new int[]{0xFFFFFF, 0xFF7F00, 0xFF00FF, 0x007FD8, 0xFFFF00, 0x00FF00, 0xFF99A5, 0x7F7F7F, 0xCCCCCC, 0x00FFFF, 0xB233FF, 0x0000FF, 0x7F3300, 0x009900, 0xFF0000, 0x000000, 0x875BA6};
    static int[] darkColorMul = new int[]{0xD8D8D8, 0xF7AC43, 0xB629B6, 0x80AAE4, 0xF5FC49, 0xB9FC50, 0xF5B3D4, 0x9E9E9E, 0xCBCBCB, 0x50A8C4, 0xA252CC, 0x5049FC, 0xB5947D, 0x5FE02D, 0xFC003B, 0x555555, 0x875BA6};
    static int[] brightColorMul = new int[]{0xF7F7F7, 0xF1DBC1, 0xC396C6, 0xD5F3FC, 0xFCFCE5, 0xE4F4D4, 0xE4F4D4, 0xC7C7C7, 0xECECEC, 0xACDAF1, 0xC5A1CA, 0xDAE3FC, 0xDDCFC6, 0xE0EFE0, 0xFCE3EA, 0x828282, 0xD4B9E9};
    static int[] mediumColorMul = new int[]{0xBCBCBC, 0xF69538, 0x801E80, 0x618BC9, 0xFCF4A8, 0x7BFC49, 0xD98BB3, 0x7B7B7B, 0x9B9B9B, 0x2E99A3, 0x802FB0, 0x2C289E, 0x714D34, 0x449E21, 0xA30028, 0x2A2A2A, 0x1B2343};
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {
        for(int i = 0; i < darkColorMul.length; i++) {
            int blended[] = new int[native_scale * native_scale];
            TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, blended);

            if(darkColorMul[i] != 0xFFFFFF) {
                int mulR = (darkColorMul[i] & 0xFF0000) >> 16;
                int mulG = (darkColorMul[i] & 0x00FF00) >> 8;
                int mulB = (darkColorMul[i] & 0x0000FF);
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
        return 17;
    }
}
