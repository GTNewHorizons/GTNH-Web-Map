package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class RailCraftFireboxRenderer extends CustomRenderer {
    RenderPatch[] inactive, active;
    int thisBlockId;
    static int[][] offsets = {{0,0}, {-1,0}, {1,0}, {0,-1}, {0,1}, {-1,-1}, {-1,1}, {1,-1}, {1,1}};

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        inactive = getBoxFull(rpf, new int[]{0,0,1,1,1,1});
        active = getBoxFull(rpf, new int[]{0,0,2,2,2,2});
        thisBlockId = blkid;
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        for(int i = 0; i < offsets.length; i++) {
            if (mapDataCtx.getBlockTypeIDAt(offsets[i][0],0, offsets[i][1]) == thisBlockId && GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityFieldAt("master", offsets[i][0],0, offsets[i][1]),0) == 1){
                Object burnTime = mapDataCtx.getBlockTileEntityFieldAt("burnTime", offsets[i][0], 0, offsets[i][1]);
                if(burnTime instanceof Float && (Float) burnTime > 0)
                    return active;
                if(burnTime instanceof Double && (Double) burnTime > 0)
                    return active;
                return inactive;
            }
        }

        return inactive;
    }
    static String[] nbtFieldsNeeded = {"master", "burnTime"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
