package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.forestry.ForestrySupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class ForestryModSupportLoader extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {

        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data) {
        String type = data.get("type");
        if(type == null)
            return;
        if(type.equals("tree")){
            int saplingId = TexturePack.parseTextureIndex(filetoidx, data.get("sapling"));
            int leavesId = TexturePack.parseTextureIndex(filetoidx, data.get("leaf"));
            int leavesChangedId = TexturePack.parseTextureIndex(filetoidx, data.get("leafChanged"));
            int color = Integer.parseInt(data.get("color"), 16);

            ForestrySupport.registerTreeType(data.get("name"), saplingId, leavesId, color);

        }
    }
}
