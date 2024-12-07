package org.dynmap.modsupport.botania;

import java.util.HashMap;

public class BotaniaSupport {
    static HashMap<String, Integer> nameToTexture = new HashMap<>();
    public static int getTextureForFlower(String flowerName){
        Integer tex = nameToTexture.get(flowerName);
        if(tex != null)
            return tex;

        return -1;
    }

    public static void registerFlower(String name, int tex){
        nameToTexture.put(name, tex);
    }
}
