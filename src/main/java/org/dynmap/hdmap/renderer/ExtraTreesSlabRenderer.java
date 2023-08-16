package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ExtraTreesSlabRenderer extends CustomRenderer {
    RenderPatch[][] upper, lower;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        upper = new RenderPatch[256][];
        lower = new RenderPatch[256][];
        for(int i = 0; i < 64; i++) {
            upper[i] = getBoxSingleTextureInt(rpf, 0, 16,8,16,0,16, i,false);
            lower[i] = getBoxSingleTextureInt(rpf, 0, 16,0,8,0,16, i,false);
        }
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        int meta = GWM_Util.objectToInt(ctx.getBlockTileEntityField("meta"),0);
        int data = ctx.getBlockData();

        meta += 6;

        if(meta < 0 || meta > 40)
            meta = 0;

        return data < 8 ? lower[meta] : upper[meta];
    }

    static String[] nbtFieldsNeeded = {"meta"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

}
