package org.dynmap.renderer;

import org.dynmap.DynmapCore;

import java.util.HashMap;

public abstract class CustomModSupportLoader {

    public abstract void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) ;

    public void processData(String line, HashMap<String, String> data) {

    }
}
