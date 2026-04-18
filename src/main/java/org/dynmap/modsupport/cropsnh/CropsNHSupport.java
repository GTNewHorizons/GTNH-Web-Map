package org.dynmap.modsupport.cropsnh;

import org.dynmap.renderer.CustomTextureMapper;

import java.util.HashMap;

public class CropsNHSupport {
    static HashMap<String, CropEntry> cropMap = new HashMap<>();
    static int[] baseTexture = { 0 };
    public static void registerCrop(String crop, int growthDuration, boolean isHash, int[] arr) {

        cropMap.put(crop, new CropEntry(growthDuration, isHash, arr));
    }

    public static CropEntry getCrop(String cropName) {
        if(cropName == null)
            return null;
        return cropMap.get(cropName);
    }

    public static void setBaseTexture(int texture){
        baseTexture[0] = texture;
    }

    public static class CropEntry implements CustomTextureMapper
    {
        private final int duration;
        public final boolean isHash;
        int[][] saved;
        public CropEntry(int duration, boolean isHash, int[] arr) {
            this.duration = duration;
            this.isHash = isHash;
            saved = new int[arr.length][1];
            for(int i = 0; i < arr.length; i++)
                saved[i][0] = arr[i];
        }

        public int progressToStep(int progress){
            if(progress < 0)
                return 0;

            int step = progress * (saved.length - 1) / duration;

            if(step >= saved.length)
                step = saved.length - 1;
            if(step < 0)
                step = 0;

            return step;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(patchId <= 0)
                return baseTexture;
            return saved[(patchId-1) % saved.length];
        }
    }
}
