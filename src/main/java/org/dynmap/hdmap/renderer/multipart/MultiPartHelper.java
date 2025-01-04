package org.dynmap.hdmap.renderer.multipart;

import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.hdmap.textureprocessor.CustomTextureProcessor;

import java.util.HashMap;

public class MultiPartHelper {

    static HashMap<String, MultiPartRenderer> renderers = new HashMap<>();

    public static void processData(HashMap<String, Integer> filetoidx, HashMap<String, String> data) {
        String partName = data.get("part");
        String clsName = data.get("class");

        if(partName != null && clsName != null){
            Class<?> cls = null;   /* Get class */
            try {
                cls = Class.forName(clsName);
                MultiPartRenderer renderer = (MultiPartRenderer) cls.newInstance();

                renderer.initialize(HDBlockModels.getPatchDefinitionFactory(), filetoidx, data);

                renderers.put(partName, renderer);

            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

        }
    }

    public static MultiPartRenderer getRendererForPart(String part){
        return renderers.get(part);
    }
}
