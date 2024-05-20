package org.dynmap.modsupport.ic2;

import org.dynmap.renderer.CustomTextureMapper;

import java.util.HashMap;

public class CropsSupport {

    static HashMap<String, TextureSelector> cropMap = new HashMap<>();
    public static void registerCrop(String crop, int[] arr) {

        cropMap.put(crop, new TextureSelector(arr));
    }

    public static CustomTextureMapper getCrop(String cropName) {
        if(cropName == null)
            return null;
        return cropMap.get(cropName);
    }

    public static class TextureSelector implements CustomTextureMapper
    {
        int[][] saved;
        public TextureSelector(int[] arr) {
            saved = new int[arr.length][1];
            for(int i = 0; i < arr.length; i++)
                saved[i][0] = arr[i];
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return saved[patchId % saved.length];
        }
    }
}
