package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public class RemoveTranslucencyProcessor extends CustomTextureProcessor {
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {
        int[] texture = new int[native_scale * native_scale];
        TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, texture);

        for (int i = 0; i < texture.length; i++) {
            if ((texture[i] >>> 24) != 0) {
                texture[i] |= 0xFF000000;
            }
        }

        texturePack.setTileARGB(tileToDyntile[0], texture);
    }

    @Override
    public int getTextureCount() {
        return 1;
    }
}
