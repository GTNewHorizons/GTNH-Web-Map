package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class ThaumcraftWoodenDeviceRenderer extends CustomRenderer {
    RenderPatch[] fullBlock, bellows;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        fullBlock = getFullBlock(rpf, -1);

        bellows = getBellowsModel(rpf);

        return true;
    }

    private RenderPatch[] getBellowsModel(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Top
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};
            addBox(rpf, list, 0, 1, 0, 0.125, 0, 1, patchTextureIds);
            //addBox(rpf, list, 0, 1, 7.0 / 16, 9.0 / 16, 0, 1, patchTextureIds);
            addBox(rpf, list, 0, 1, 0.875, 1, 0, 1, patchTextureIds);
        }
        {
            int patchTextureIds[] = {1, 1, 1, 1, 1, 1};
            addBox(rpf, list, 0.125, 0.875, 0.125, 0.875, 0.125, 0.875, patchTextureIds);
            //addBox(rpf, list, 0.125, 0.875, 9.0 / 16, 0.875, 0.125, 0.875, patchTextureIds);
        }
        return list.toArray(new RenderPatch[list.size()]);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int data= mapDataCtx.getBlockData();
        switch (data){
            case 0:
                return bellows;
        }
        return fullBlock;
    }




    static String[] nbtFieldsNeeded = {"orientation"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
