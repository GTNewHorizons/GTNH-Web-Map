package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

public class RailCraftStairBlockRenderer extends StairBlockRenderer {
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        Object objStair = ctx.getBlockTileEntityField("stair");
        RenderPatch[] renderPatchList = super.getRenderPatchList(ctx);

        if(objStair instanceof String){
            RailCraftSlabBlockRenderer.RailCraftBlocks tex = RailCraftSlabBlockRenderer.RailCraftBlocks.NOTHING;
            tex = RailCraftSlabBlockRenderer.RailCraftBlocks.valueOf((String)objStair);

            RenderPatch[] ret = new RenderPatch[renderPatchList.length];
            for(int i = 0; i  < renderPatchList.length; i++)
                ret[i] = ctx.getPatchFactory().getRotatedPatch(renderPatchList[i],0,0,0, tex.ordinal());
            return ret;
        }

        return renderPatchList;
    }

    static String[] nbtFieldsNeeded = {"stair"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
