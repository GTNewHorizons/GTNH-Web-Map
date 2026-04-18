package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.cropsnh.CropsNHSupport;
import org.dynmap.modsupport.ic2.CropsSupport;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.HashMap;
import java.util.Map;

public class CropsNHRenderer extends CustomRenderer {

    private final static int MAX_STEPS = 16;
    RenderPatch[] sticksPatches;
    RenderPatch[] crossSticksPatches;
    RenderPatch[][] cropPatchesX, cropPatchesHash;

    CustomRendererData sticksData, crossSticksData;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        sticksPatches = CustomRenderer.combineMultiple(
                getBoxSingleTextureInt(rpf, 2, 3, 0, 13, 2, 3, 0, false),
                getBoxSingleTextureInt(rpf, 13, 14, 0, 13, 2, 3, 0, false),
                getBoxSingleTextureInt(rpf, 2, 3, 0, 13, 13, 14, 0, false),
                getBoxSingleTextureInt(rpf, 13, 14, 0, 13, 13, 14, 0, false)
        );
        crossSticksPatches = CustomRenderer.combineMultiple(
                sticksPatches,
                getBoxSingleTextureInt(rpf, 0, 16, 10, 11, 2, 3, 0, false),
                getBoxSingleTextureInt(rpf, 2, 3, 10, 11, 0, 16, 0, false),
                getBoxSingleTextureInt(rpf, 0, 16, 10, 11, 13, 14, 0, false),
                getBoxSingleTextureInt(rpf, 13, 14, 10, 11, 0, 16, 0, false)
        );

        int maxSteps = MAX_STEPS;
        cropPatchesX = new RenderPatch[maxSteps][];
        cropPatchesHash = new RenderPatch[maxSteps][];
        for (int i = 0; i < maxSteps; i++) {
            RenderPatch[] hashPatches = new RenderPatch[4];
            hashPatches[0] = rpf.getPatch(0, 0, 0.25, 1, 0, 0.25, 0, 1, 0.25, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, i + 1);
            hashPatches[1] = rpf.getPatch(0, 0, 0.75, 1, 0, 0.75, 0, 1, 0.75, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, i + 1);
            hashPatches[2] = rpf.getPatch(0.25, 0, 0, 0.25, 0, 1, 0.25, 1, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, i + 1);
            hashPatches[3] = rpf.getPatch(0.75, 0, 0, 0.75, 0, 1, 0.75, 1, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, i + 1);
            cropPatchesHash[i] = combineMultiple(sticksPatches, hashPatches);

            RenderPatch[] xPatches = new RenderPatch[2];
            xPatches[0] = rpf.getPatch(0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, i + 1);
            xPatches[1] = rpf.getPatch(1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, i + 1);
            cropPatchesX[i] = combineMultiple(sticksPatches, xPatches);

        }

        sticksData = new CustomRendererData(sticksPatches, null, null);
        crossSticksData = new CustomRendererData(crossSticksPatches, null, null);

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return new RenderPatch[0];
    }
    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        int crossCrop = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("crossCrop"),0);
        if(crossCrop > 0)
            return crossSticksData;

        Object objCrop = mapDataCtx.getBlockTileEntityField("crop");
        if(objCrop == null)
            return sticksData;

        if(objCrop instanceof HashMap) {
            HashMap cropMap = (HashMap) objCrop;

            String cropName = cropMap.get("crop").toString();
            CropsNHSupport.CropEntry entry = CropsNHSupport.getCrop(cropName);

            if(entry != null) {
                int progress = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("progress"), 0);
                int step = entry.progressToStep(progress);

                if(step >= MAX_STEPS)
                    step = MAX_STEPS-1;

                RenderPatch[] patches = entry.isHash ? cropPatchesHash[step] : cropPatchesX[step];

                return new CustomRendererData(patches, null, entry);
            }
        }
        return sticksData;
    }

    static String[] nbtFieldsNeeded = {"crossCrop", "crop", "progress"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
