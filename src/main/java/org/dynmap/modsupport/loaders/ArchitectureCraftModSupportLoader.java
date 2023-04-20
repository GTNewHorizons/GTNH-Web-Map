package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class ArchitectureCraftModSupportLoader extends CustomModSupportLoader {
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        GWM_Util.initialize(core);
    }
}
