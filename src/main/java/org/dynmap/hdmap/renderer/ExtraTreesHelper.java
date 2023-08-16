package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

public class ExtraTreesHelper {
    public static RenderPatch[] adjustPatches(MapDataContext ctx, RenderPatch[] renderPatchList, boolean offset) {
        int meta = GWM_Util.objectToInt(ctx.getBlockTileEntityField("meta"),0);

        if(offset) {
            if (meta >= 32)
                meta -= 26;
        } else {
            meta += 6;
        }
        if(meta < 0 || meta > 40)
            meta = 0;

        RenderPatch[] ret = new RenderPatch[renderPatchList.length];
        for(int i = 0; i  < renderPatchList.length; i++)
            ret[i] = ctx.getPatchFactory().getRotatedPatch(renderPatchList[i],0,0,0, meta);

        return ret;
    }
}
