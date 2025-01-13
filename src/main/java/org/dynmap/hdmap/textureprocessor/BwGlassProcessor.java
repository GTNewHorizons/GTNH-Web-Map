package org.dynmap.hdmap.textureprocessor;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.renderer.CustomRenderer;

public class BwGlassProcessor extends CustomTextureProcessor {
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {

        int outScale = native_scale;

        native_scale = w;

        int blended[][] = new int[16][native_scale * native_scale];
        for(int i = 0; i < 16; i++) {
            TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, blended[i]);

            boolean up = (i & 2) != 0;
            boolean down = (i & 1) != 0;
            boolean left = (i & 4) != 0;
            boolean right = (i & 8) != 0;

            int midColor = blended[i][native_scale * native_scale / 2 + native_scale / 2];

            for(int x = left ? 0 : 1; x < (right ? native_scale : native_scale-1); x++){
                if(up)
                    blended[i][x] = midColor;

                if(down)
                    blended[i][native_scale*(native_scale-1) + x] = midColor;
            }

            for(int y = up ? 0 : 1; y < (down ? native_scale : native_scale-1); y++){
                if(left)
                    blended[i][y*native_scale] = midColor;

                if(right)
                    blended[i][y*native_scale+native_scale-1] = midColor;
            }

            if(native_scale != outScale) {
                int[] out = new int[outScale * outScale];
                TexturePack.scaleTerrainPNGSubImage(native_scale, outScale, blended[i], out);
                texturePack.setTileARGB(tileToDyntile[i], out);
            }
            else{
                texturePack.setTileARGB(tileToDyntile[i], blended[i]);
            }
        }
    }

    @Override
    public int getTextureCount() {
        return 16;
    }
}
