package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class NuclearControlInformationPanelRenderer extends CustomRenderer {
    int[][] patchIdsByFacing = {
            {0,1,2,2,2,2},
            {1,0,2,2,2,2},
            {2,2,2,2,0,1},
            {2,2,2,2,1,0},
            {2,2,0,1,2,2},
            {2,2,1,0,2,2},
    };

    RenderPatch[][] modelsByFacing = new RenderPatch[6][];
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        for(int i= 0; i < 6; i++) {
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchIdsByFacing[i]);
            modelsByFacing[i] = list.toArray(new RenderPatch[patchIdsByFacing[i].length]);
        }

        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int facing = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("facing"),0);

        if(facing >= 0 && facing < modelsByFacing.length)
            return modelsByFacing[facing];

        return new RenderPatch[0];
    }

    static String[] nbtFieldsNeeded = {"facing", "colorBackground", "transparencyMode", "thickness"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
