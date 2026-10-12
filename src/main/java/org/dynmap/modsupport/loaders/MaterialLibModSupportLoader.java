package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.materiallib.MaterialLibSupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class MaterialLibModSupportLoader extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;

    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        GWM_Util.initialize(core);
        this.filetoidx = filetoidx;
        Log.info("MaterialLib block rendering enabled");
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        String type = data.get("type");

        if(type == null)
            return;

        if(type.equals("shape")){
            MaterialLibSupport.INSTANCE.processShape(data, filetoidx);
        }
        else if(type.equals("setart")){
            MaterialLibSupport.INSTANCE.processSetArt(data, filetoidx);
        }
        else if(type.equals("blockart")){
            MaterialLibSupport.INSTANCE.processBlockArt(data, filetoidx);
        }
    }
}
