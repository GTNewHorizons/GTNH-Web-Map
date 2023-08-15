package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class RailCraftCokeOvenRenderer extends CustomRenderer {
    RenderPatch[] basicBox, inactive, active;
    int thisBlockId;
    static int[][] offsets = {{-1,0},{1,0},{0,-1},{0,1}};

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        basicBox = getBoxSingleTextureInt(rpf, 0,16,0,16,0,16,0,false);
        inactive = getBoxSingleTextureInt(rpf, 0,16,0,16,0,16,1,false);
        active = getBoxSingleTextureInt(rpf, 0,16,0,16,0,16,2,false);
        thisBlockId = blkid;
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        for(int i = 0; i < 4; i++) {
            if (mapDataCtx.getBlockTypeIDAt(offsets[i][0],-1, offsets[i][1]) == thisBlockId && mapDataCtx.getBlockTileEntityFieldAt("uuid", offsets[i][0],-1, offsets[i][1]) != null){
                if(GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityFieldAt("cooking", offsets[i][0],-1, offsets[i][1]),0) == 1)
                    return active;
                return inactive;
            }
        }
        return basicBox;
    }
    static String[] nbtFieldsNeeded = {"uuid", "cooking"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
