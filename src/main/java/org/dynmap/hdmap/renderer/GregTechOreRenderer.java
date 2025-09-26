package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.SimpleColorMultiplier;
import org.dynmap.modsupport.gregtech.GregTechSupport;
import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class GregTechOreRenderer extends CustomRenderer {
    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    private static RenderPatch[] fullBlock;

    static OreRenderData[][] oreRenderDataCache = new OreRenderData[1000][32];

    static int[] baseTextures;
    private int blkid;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        this.blkid = blkid;
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, fullBlockPatchList);
        fullBlock = list.toArray(new RenderPatch[fullBlockPatchList.length]);

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        if(baseTextures == null){
            baseTextures = new int[16];
            for(int i = 0; i < 16; i++) {
                TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blkid, i, 0);
                baseTextures[i] = map.getIndexForFace(0);
            }
        }

        int oreType = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("m"), 0);

        int meta = (oreType / 1000);
        oreType %= 1000;

        if(oreRenderDataCache[oreType][meta] == null)
            oreRenderDataCache[oreType][meta] = new OreRenderData(meta, oreType);

        return oreRenderDataCache[oreType][meta];
    }
    static String[] nbtFieldsNeeded = {"m"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
    static class OreRenderData extends CustomRendererData implements CustomTextureMapper {

        int[] textures;
        SimpleColorMultiplier multiplier;
        public OreRenderData(int meta, int oreType) {
            super(fullBlock, null, null);

            GregTechSupport.MaterialEntry ent = GregTechSupport.INSTANCE.getMaterial(oreType);

            int actualMeta = meta % 16;
            boolean isSmall = meta >= 16;

            if(ent != null){
                multiplier = new SimpleColorMultiplier(ent.color);
                int overlay = isSmall ? ent.smallOreTextureOverlay : ent.oreTextureOverlay;
                if(overlay >= 0)
                    textures = new int[]{baseTextures[actualMeta], isSmall ? ent.smallOreTexture : ent.oreTexture, overlay};
                else
                    textures = new int[]{baseTextures[actualMeta], isSmall ? ent.smallOreTexture : ent.oreTexture};
            }
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return this;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return textures;
        }

        @Override
        public CustomColorMultiplier getCustomColorMultiplier(int patchId, int layer) {
            return multiplier;
        }
    }
}
