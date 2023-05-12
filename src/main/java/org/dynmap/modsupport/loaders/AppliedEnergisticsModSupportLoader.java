package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.modsupport.appliedenergistics2.AE2Support;
import org.dynmap.modsupport.gregtech.GregTechSupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class AppliedEnergisticsModSupportLoader  extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        String strDamage = data.get("damage");
        String texture = data.get("tex");
        String size = data.get("size");

        AE2Support.addCableType(filetoidx, Integer.parseInt(strDamage), texture, Integer.parseInt(size));
    }


}
