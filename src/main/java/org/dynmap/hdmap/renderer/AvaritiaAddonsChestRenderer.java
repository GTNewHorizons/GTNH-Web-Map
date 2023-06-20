package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class AvaritiaAddonsChestRenderer extends CustomRenderer {

    private RenderPatch[] fullBlock;
    private static final int patchlist[] = { 0, 1, 4, 5, 2, 3 };

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0.0625, 0.9375, 0, 0.875, 0.0625, 0.9375, patchlist);
        fullBlock = list.toArray(new RenderPatch[patchlist.length]);
        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int rot = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("FacingSide"), 0);

        if(rot != 0)
            return getRotatedSet(mapDataCtx.getPatchFactory(), fullBlock, 0, rot, 0);

        return fullBlock;
    }

    static String[] nbtFieldsNeeded = {"FacingSide"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
