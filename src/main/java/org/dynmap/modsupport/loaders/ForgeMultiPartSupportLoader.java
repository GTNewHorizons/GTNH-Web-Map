package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.hdmap.renderer.multipart.MultiPartHelper;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class ForgeMultiPartSupportLoader extends CustomModSupportLoader {
    private HashMap<String, Integer> filetoidx;

    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        GWM_Util.initialize(core);
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        MultiPartHelper.processData(filetoidx, data);
    }
}
