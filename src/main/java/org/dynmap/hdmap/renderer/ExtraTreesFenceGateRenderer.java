package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

public class ExtraTreesFenceGateRenderer extends FenceGateBlockRenderer{
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        return ExtraTreesHelper.adjustPatches(ctx, super.getRenderPatchList(ctx), true);
    }

    static String[] nbtFieldsNeeded = {"meta"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
