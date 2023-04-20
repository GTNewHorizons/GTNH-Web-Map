package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.gregtech.GregTechSupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class GregTechModSupportLoader extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        GWM_Util.initialize(core);
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        String type = data.get("type");

        if(type == null)
            return;

        if(type.equals("iconset")){
            GregTechSupport.INSTANCE.processIconSet(data, filetoidx);
        }
        else if(type.equals("mte")){
            GregTechSupport.INSTANCE.processMte(data, filetoidx);
        }
        else if(type.equals("hatchbase")){
            GregTechSupport.INSTANCE.processHatchBase(data, filetoidx);
        }
    }
}
