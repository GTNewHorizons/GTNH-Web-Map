package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.ic2.CropsSupport;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class IC2CropsRenderer extends CustomRenderer {
    RenderPatch[][] patches = new RenderPatch[16][4];

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        for(int i = 0; i< patches.length; i++){
            patches[i][0] = rpf.getPatch(0,0, 0.25, 1,0,0.25, 0,1,0.25,0,1,0,1, RenderPatchFactory.SideVisible.BOTH, i);
            patches[i][1] = rpf.getPatch(0,0, 0.75, 1,0,0.75, 0,1,0.75,0,1,0,1, RenderPatchFactory.SideVisible.BOTH, i);
            patches[i][2] = rpf.getPatch(0.25,0,0,  0.25,0,1, 0.25,1,0,0,1,0,1, RenderPatchFactory.SideVisible.BOTH, i);
            patches[i][3] = rpf.getPatch(0.75,0,0,  0.75,0,1, 0.75,1,0,0,1,0,1, RenderPatchFactory.SideVisible.BOTH, i);
        }

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int size = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("size"),0);
        int upgraded = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("upgraded"),0);
        if(size > 0)
            size -= 1;

        if(upgraded != 0)
            size = 1;

        if(size >= patches.length)
            size = patches.length-1;

        return patches[size];
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        Object objCropName = mapDataCtx.getBlockTileEntityField("cropName");
        String cropName = null;
        if(objCropName instanceof String)
            cropName = (String)objCropName;

        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, CropsSupport.getCrop(cropName));
    }

    static String[] nbtFieldsNeeded = {"cropName", "size", "upgraded"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
