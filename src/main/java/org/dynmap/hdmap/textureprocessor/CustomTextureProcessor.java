package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public abstract class CustomTextureProcessor {

    public int getTextureCount(){ return 0; }

    public abstract void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) ;
}
