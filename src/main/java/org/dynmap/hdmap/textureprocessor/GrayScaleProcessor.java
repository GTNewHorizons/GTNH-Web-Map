package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public class GrayScaleProcessor extends CustomTextureProcessor{
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {

        int blended[] = new int[native_scale * native_scale];
        TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, blended);

        for (int p = 0; p < blended.length; p++) {
            int srcR = (blended[p] & 0xFF0000) >> 16;
            int srcG = (blended[p] & 0x00FF00) >> 8;
            int srcB = (blended[p] & 0x0000FF);

            int dst = ((srcR + srcG + srcB) / 3) & 0xFF;

            blended[p] = (blended[p] & 0xFF000000) | (dst << 16) | (dst << 8) | dst;
        }
        texturePack.setTileARGB(tileToDyntile[0], blended);
    }

    @Override
    public int getTextureCount() {
        return 1;
    }
}
