package org.dynmap.hdmap.renderer;

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

public class GregTechFrameRenderer extends CustomRenderer {
    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    private static RenderPatch[] fullBlock;

    static FrameRenderData[]oreRenderDataCache = new FrameRenderData[4096];

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


        int oreType = mapDataCtx.getBlockDataFull();


        if(oreRenderDataCache[oreType] == null)
            oreRenderDataCache[oreType]= new FrameRenderData(oreType);

        return oreRenderDataCache[oreType];
    }
    static String[] nbtFieldsNeeded = {"m"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
    static class FrameRenderData extends CustomRendererData implements CustomTextureMapper {

        SimpleColorMultiplier multiplier;
        public FrameRenderData(int oreType) {
            super(fullBlock, null, null);

            GregTechSupport.MaterialEntry ent = GregTechSupport.INSTANCE.getMaterial(oreType);

            if(ent != null){
                multiplier = new SimpleColorMultiplier(ent.color);

            }
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return null;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return null;
        }

        @Override
        public CustomColorMultiplier getCustomColorMultiplier(int patchId, int layer) {
            return multiplier;
        }
    }
}
