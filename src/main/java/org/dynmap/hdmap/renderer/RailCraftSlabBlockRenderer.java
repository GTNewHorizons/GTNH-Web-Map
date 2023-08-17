package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class RailCraftSlabBlockRenderer extends CustomRenderer {

    public enum RailCraftBlocks {
        SNOW,
        ICE,
        PACKED_ICE,
        IRON,
        STEEL,
        COPPER,
        TIN,
        LEAD,
        GOLD,
        DIAMOND,
        OBSIDIAN,
        CONCRETE,
        CREOSOTE,

        ABYSSAL_BRICK,
        ABYSSAL_FITTED,
        ABYSSAL_BLOCK,
        ABYSSAL_COBBLE,

        INFERNAL_BRICK,
        INFERNAL_FITTED,
        INFERNAL_BLOCK,
        INFERNAL_COBBLE,

        BLOODSTAINED_BRICK,
        BLOODSTAINED_FITTED,
        BLOODSTAINED_BLOCK,
        BLOODSTAINED_COBBLE,

        SANDY_BRICK,
        SANDY_FITTED,
        SANDY_BLOCK,
        SANDY_COBBLE,

        BLEACHEDBONE_BRICK,
        BLEACHEDBONE_FITTED,
        BLEACHEDBONE_BLOCK,
        BLEACHEDBONE_COBBLE,

        QUARRIED_BRICK,
        QUARRIED_FITTED,
        QUARRIED_BLOCK,
        QUARRIED_COBBLE,

        FROSTBOUND_BRICK,
        FROSTBOUND_FITTED,
        FROSTBOUND_BLOCK,
        FROSTBOUND_COBBLE,

        NETHER_FITTED,
        NETHER_BLOCK,
        NETHER_COBBLE,

        NOTHING
    }

    RenderPatch[][] topOnly, bottomOnly, fullBlocks;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        topOnly = new RenderPatch[RailCraftBlocks.values().length][];
        bottomOnly = new RenderPatch[RailCraftBlocks.values().length][];
        fullBlocks = new RenderPatch[RailCraftBlocks.values().length][];
        for(RailCraftBlocks b : RailCraftBlocks.values()){


            int ord = b.ordinal();
            int tex = ord;

            if(b.equals(RailCraftBlocks.NOTHING))
                tex = 0;

            topOnly[ord] = CustomRenderer.getBoxSingleTexture(rpf, 0, 1, 0.5, 1, 0, 1, tex, false);
            bottomOnly[ord] = CustomRenderer.getBoxSingleTexture(rpf, 0, 1, 0, 0.5, 0, 1, tex, false);
            fullBlocks[ord] = CustomRenderer.getBoxSingleTexture(rpf, 0, 1, 0, 1, 0, 1, tex, false);
        }

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }


    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        Object objTop = mapDataCtx.getBlockTileEntityField("top");
        Object objBottom = mapDataCtx.getBlockTileEntityField("bottom");

        RailCraftBlocks top = RailCraftBlocks.NOTHING;
        RailCraftBlocks bottom = RailCraftBlocks.NOTHING;

        if(objTop != null)
            top = RailCraftBlocks.valueOf((String)objTop);

        if(objBottom != null)
            bottom = RailCraftBlocks.valueOf((String)objBottom);

        if(top.equals(bottom))
            return fullBlocks[top.ordinal()];

        if(top.equals(RailCraftBlocks.NOTHING))
            return bottomOnly[bottom.ordinal()];
        else if(bottom.equals(RailCraftBlocks.NOTHING))
            return topOnly[top.ordinal()];

        return combineMultiple(topOnly[top.ordinal()], bottomOnly[bottom.ordinal()]);
    }

    static String[] nbtFieldsNeeded = {"top", "bottom"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
