package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class ThaumcraftMetalDeviceRenderer extends CustomRenderer {

    RenderPatch[] fullBlock, alembic;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        fullBlock = getFullBlock(rpf, -1);

        alembic = getAlembicModel(rpf);

        return true;
    }

    private RenderPatch[] getAlembicModel(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};

            addBox(rpf, list, 0.175, 0.825, 0.125, 0.875, 0.175, 0.825, patchTextureIds);
            addBox(rpf, list, 0.275, 0.725, 0.875, 1, 0.275, 0.725, patchTextureIds);
        }

        return list.toArray(new RenderPatch[list.size()]);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int data = mapDataCtx.getBlockData();

        if(data == 1)
            return alembic;


        return fullBlock;
    }


    static String[] nbtFieldsNeeded = {"facing"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
