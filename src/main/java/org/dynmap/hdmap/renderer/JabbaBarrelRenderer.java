package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class JabbaBarrelRenderer extends CustomRenderer {

    RenderPatch[][] patchesPerOrientation;

    int maxStructural = 7;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        patchesPerOrientation = new RenderPatch[6*(maxStructural+1)][];



        for(int i = 0; i <= maxStructural; i++){
            int topBottomLabel = 4*i + 0, sideLabel = 4*i + 1, topBottom = 4*i + 2, side = 4*i + 3;

            patchesPerOrientation[i*6+0] = getFullBlock(rpf, new int[]{topBottomLabel, topBottom, side, side, side, side});
            patchesPerOrientation[i*6+1] = getFullBlock(rpf, new int[]{topBottom, topBottomLabel, side, side, side, side});
            patchesPerOrientation[i*6+2] = getFullBlock(rpf, new int[]{topBottom, topBottom, side, side, sideLabel, side});
            patchesPerOrientation[i*6+3] = getFullBlock(rpf, new int[]{topBottom, topBottom, side, side, side, sideLabel});
            patchesPerOrientation[i*6+4] = getFullBlock(rpf, new int[]{topBottom, topBottom, sideLabel, side, side, side});
            patchesPerOrientation[i*6+5] = getFullBlock(rpf, new int[]{topBottom, topBottom, side, sideLabel, side, side});
        }

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int orientation = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("orientation"),0);
        if(orientation < 0 || orientation > 5)
            orientation = 0;
        int structural = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("structural"),0);
        if(structural < 0 || structural > 7)
            structural = 0;
        return patchesPerOrientation[structural*6 + orientation];
    }


    static String[] nbtFieldsNeeded = {"orientation", "structural"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
