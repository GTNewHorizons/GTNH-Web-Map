package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.SimpleColorMultiplier;
import org.dynmap.modsupport.forestry.BinnieSupport;
import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.CustomModSupportLoader;
import org.dynmap.renderer.MapDataContext;

import java.util.HashMap;

public class BinnieModSupportLoader extends CustomModSupportLoader {
    private DynmapCore core;
    private HashMap<String, Integer> filetoidx;

    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {

        this.core = core;
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        String type = data.get("type");

        if(type.equals("color")){
            int id = GWM_Util.objectToInt(data.get("id"), -1);
            if(id >= 0 && id < BinnieSupport.flowerColors.length){
                int color = 0xFF000000 | Integer.parseInt(data.get("color"), 16);
                BinnieSupport.flowerColors[id] = color;
                BinnieSupport.flowerColorMultipliers[id] = new SimpleColorMultiplier(color);
            }
        }
        else if(type.equals("tile")){
            int id = GWM_Util.objectToInt(data.get("id"), -1);
            if(id >= 0){
                String frame = data.get("frame");
                String colorA = data.get("colorA");
                String colorB = data.get("colorB");

                BinnieSupport.ceramicBrickTextures.put(id, new int[][]{{TexturePack.parseTextureIndex(filetoidx, colorA),TexturePack.parseTextureIndex(filetoidx, colorB),TexturePack.parseTextureIndex(filetoidx, frame) }});
            }
        }
        else if(type.equals("pattern")){
            int id = GWM_Util.objectToInt(data.get("id"), -1);
            if(id >= 0){
                String topA = data.get("topA");
                String topB = data.get("topB");

                int arr[][] = new int[6][];

                arr[0] = arr[1] = arr[2] = arr[3] = arr[4] = arr[5] = new int[]{TexturePack.parseTextureIndex(filetoidx, topA), TexturePack.parseTextureIndex(filetoidx, topB) };

                for(int i = 0; i < 6; i++){
                    String sideA = data.get("side"+i+"A");
                    String sideB = data.get("side"+i+"B");

                    if(sideA !=  null && sideB != null)
                        arr[i] = new int[]{TexturePack.parseTextureIndex(filetoidx, sideA), TexturePack.parseTextureIndex(filetoidx, sideB)};
                }

                BinnieSupport.ceramicPatternTextures.put(id, arr);
            }
        }
        else{
            throw new Error("[Binnie] Invalid entry type: " + type);
        }

        super.processData(line, data);
    }
}
