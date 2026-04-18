package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.cropsnh.CropsNHSupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class CropsNHSupportLoader  extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        try {
            String baseTexture = data.get("basetex");
            if (baseTexture != null) {
                CropsNHSupport.setBaseTexture(TexturePack.parseTextureIndex(filetoidx, baseTexture));
                return;
            }

            String crop = data.get("crop");

            int steps = GWM_Util.objectToInt(data.get("steps"), 1);
            int growthDuration = GWM_Util.objectToInt(data.get("growthduration"), 1);

            boolean isHash = true;

            if ("X".equals(data.get("shape")))
                isHash = false;

            int[] arr = new int[steps];

            for (int i = 0; i < steps; i++) {
                arr[i] = TexturePack.parseTextureIndex(filetoidx, data.get("step" + i));
            }

            CropsNHSupport.registerCrop(crop, growthDuration, isHash, arr);

            super.processData(line, data);
        }
        catch (Exception e) {
            Log.severe(e.getMessage());
        }
    }


}
