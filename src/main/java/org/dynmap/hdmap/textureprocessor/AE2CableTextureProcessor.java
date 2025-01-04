package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

import java.util.HashMap;

public class AE2CableTextureProcessor extends CustomTextureProcessor {
    boolean noSmart;

    @Override
    public boolean init(HashMap<String, String> data) {
        if(data.get("nosmart") != null)
            noSmart = true;
        return super.init(data);
    }

    @Override
    public int getTextureCount() {
        return 3;
    }

    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {
        int new_argb[] = new int[native_scale*native_scale];
        int new_argb2[] = new int[native_scale*native_scale];
        int new_argb3[] = new int[native_scale*native_scale];

        TexturePack.scaleTerrainPNGSubImage(w, native_scale, argb, new_argb);
        texturePack.setTileARGB(tileToDyntile[0], new_argb);

        boolean foundColor = false;
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                new_argb2[y*w+x] = new_argb[x];
                new_argb3[y*w+x] = new_argb[y*w];

                if((new_argb[x] & 0xFF000000) != 0)
                    foundColor = true;
            }
            if(!foundColor){
                doSmartCoveredThing(w, h, new_argb, new_argb2, new_argb3);
                break;
            }

        }

        texturePack.setTileARGB(tileToDyntile[1], new_argb2);
        texturePack.setTileARGB(tileToDyntile[2], new_argb3);
    }

    private void doSmartCoveredThing(int w, int h, int[] new_argb, int[] new_argb2, int[] new_argb3) {

        for(int y = 0; y < h; y++){
            boolean found = false;
            for(int x = 0; x < w; x++){
                if((new_argb[y*w+x] & 0xFF000000) != 0) {
                    found = true;
                }
            }
            if(!found){
                for(int x = 0; x < w; x++)
                    new_argb[y*w+x] = new_argb[w*h/2 + x];
            }
        }
        for(int x = 0; x < w; x++){
            boolean found = false;
            for(int y = 0; y < h; y++){
                if((new_argb[y*w+x] & 0xFF000000) != 0) {
                    found = true;
                }
            }
            if(!found){
                for(int y = 0; y < h; y++)
                    new_argb[y*w+x] = new_argb[y*h + w/2];
            }
        }


        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                new_argb2[y * w + x] = new_argb[w * h / 2 + x];
                new_argb3[y * w + x] = new_argb[y * w + w / 2];
                if (!noSmart) {
                    if (x == w / 2 || x == w / 2 + 1) {
                        new_argb2[y * w + x] = 0xFF000000;
                    }
                    if (y == h / 2 || y == h / 2 + 1)
                        new_argb3[y * w + x] = 0xFF000000;
                }
            }
        }
    }
}
