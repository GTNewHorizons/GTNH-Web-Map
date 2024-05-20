package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.ic2.CropsSupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class IC2CropsSupportLoader extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {

        String crop = data.get("crop");

        int steps = GWM_Util.objectToInt(data.get("steps"),1);

        int[] arr = new int[steps];

        for(int i = 0; i< steps; i++) {
            arr[i] = TexturePack.parseTextureIndex(filetoidx, data.get("step"+i));
        }

        CropsSupport.registerCrop(crop, arr);

        super.processData(line, data);
    }
}
