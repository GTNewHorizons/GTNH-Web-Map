package org.dynmap.hdmap.textureprocessor;

import org.dynmap.hdmap.TexturePack;

public class ChiselCTMTextureProcessor extends CustomTextureProcessor{
    @Override
    public void patchTextures(TexturePack texturePack, int[] tileToDyntile, int[] argb, int w, int h, int native_scale) {
        int wpt = w / 2;
        int hpt = h / 2;

        int[][] imagesUnscaled = new int[16][wpt*hpt];
        int[] none = new int[wpt*hpt];
        int[] leftRight = new int[wpt*hpt];
        int[] upDown  = new int[wpt*hpt];

        copySourceToDest(argb, 0, 0, w, h, none);
        copySourceToDest(argb, 1, 0, w, h, leftRight);
        copySourceToDest(argb, 0, 1, w, h, upDown);

        for(int i = 0; i < 16; i++){
            int[] down = (i&1) != 0 ? upDown : none;
            int[] up  = (i&2) != 0 ? upDown : none;
            int[] left = (i&4) != 0 ? leftRight : none;
            int[] right = (i&8) != 0 ? leftRight : none;

            makePart(down, up, left, right, none, imagesUnscaled[i], wpt, hpt);
        }

        if(w != h || native_scale != wpt){
            int[][] imagesScaled = new int[16][native_scale*native_scale];
            for(int i = 0; i < 16; i++) {
                TexturePack.scaleTerrainPNGSubImage(wpt, native_scale, imagesUnscaled[i], imagesScaled[i]);
                texturePack.setTileARGB(tileToDyntile[i], imagesScaled[i]);
            }
        }
        else{
            for(int i = 0; i < 16; i++) {
                texturePack.setTileARGB(tileToDyntile[i], imagesUnscaled[i]);
            }
        }
    }

    void copySourceToDest(int[] source, int tileX, int tileY, int w, int h, int[] dest){
        for(int y = 0; y <h/2; y++){
            for(int x = 0; x < w/2; x++){
                dest[y*w/2+x] = source[tileY*h*w/2 + tileX*w/2 + y*w + x];
            }
        }
    }

    void makePart(int[] downSource, int[] upSource, int[] leftSource, int[] rightSource, int[] plain, int[] dest, int w, int h){
        for(int y = 0; y <h; y++){
            for(int x = 0; x < w; x++){
                int pixel = y * w + x;
                if(x >= y){
                    if(x < (w-y)) {
                        dest[pixel] = upSource[pixel];
                    }
                    else {
                        dest[pixel] = rightSource[pixel];
                    }
                }
                else{
                    if(x < h-y) {
                        dest[pixel] = leftSource[pixel];
                    } else {
                        dest[pixel] = downSource[pixel];
                    }
                }

                if(dest[pixel] == plain[pixel] && x == y || x == h-y || x-1 == y || x+1 == y || x -1 == h-y || x+1 == h-y){
                    if(x < w / 2 && y < h / 2){
                        if(upSource[pixel] != plain[pixel])
                            dest[pixel] = upSource[pixel];
                        else if(leftSource[pixel] != plain[pixel])
                            dest[pixel] = leftSource[pixel];
                    } else if(x >= w / 2 && y < h / 2){
                        if(upSource[pixel] != plain[pixel])
                            dest[pixel] = upSource[pixel];
                        else if(rightSource[pixel] != plain[pixel])
                            dest[pixel] = rightSource[pixel];
                    } else if(x < w / 2 && y >= h / 2){
                        if(downSource[pixel] != plain[pixel])
                            dest[pixel] = downSource[pixel];
                        else if(leftSource[pixel] != plain[pixel])
                            dest[pixel] = leftSource[pixel];
                    } else if(x >= w / 2 && y >= h / 2){
                        if(downSource[pixel] != plain[pixel])
                            dest[pixel] = downSource[pixel];
                        else if(rightSource[pixel] != plain[pixel])
                            dest[pixel] = rightSource[pixel];
                    }
                }
            }
        }
    }

    @Override
    public int getTextureCount() {
        return 16;
    }
}
