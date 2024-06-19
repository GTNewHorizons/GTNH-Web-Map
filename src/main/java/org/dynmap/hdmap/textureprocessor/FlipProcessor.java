package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public class FlipProcessor extends CustomTextureProcessor {
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {

        int textures[][] = new int[2][native_scale * native_scale];
        TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, textures[0]);

        for (int y = 0; y < native_scale; y++) {
            for(int x = 0; x < native_scale; x++){
                textures[1][y * native_scale + native_scale - x - 1] = textures[0][y*native_scale+x];
            }
        }

        texturePack.setTileARGB(tileToDyntile[0], textures[0]);
        texturePack.setTileARGB(tileToDyntile[1], textures[1]);
    }

    @Override
    public int getTextureCount() {
        return 2;
    }
}
