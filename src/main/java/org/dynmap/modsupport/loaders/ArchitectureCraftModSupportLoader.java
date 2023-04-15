package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomModSupportLoader;

public class ArchitectureCraftModSupportLoader extends CustomModSupportLoader {
    @Override
    public void initializeModSupport(DynmapCore core) {
        GWM_Util.initialize(core);
    }
}
