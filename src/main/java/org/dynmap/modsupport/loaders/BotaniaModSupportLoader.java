package org.dynmap.modsupport.loaders;

import org.dynmap.DynmapCore;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.botania.BotaniaSupport;
import org.dynmap.renderer.CustomModSupportLoader;

import java.util.HashMap;

public class BotaniaModSupportLoader extends CustomModSupportLoader {
    HashMap<String, Integer> filetoidx;
    @Override
    public void initializeModSupport(DynmapCore core, HashMap<String, Integer> filetoidx) {
        this.filetoidx = filetoidx;
    }

    @Override
    public void processData(String line, HashMap<String, String> data){
        String type = data.get("type");

        if(type == null)
            return;

        if(type.equals("flower")){
            String id = data.get("id");
            String tex = data.get("tex");

            if (id != null && tex != null) {
                int texAsInt = TexturePack.parseTextureIndex(filetoidx, tex);
                BotaniaSupport.registerFlower(id, texAsInt);
            }
        }
    }
}
