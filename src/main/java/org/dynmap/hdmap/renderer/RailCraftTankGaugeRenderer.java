package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class RailCraftTankGaugeRenderer extends CustomRenderer {

    RenderPatch[][] versions;
    int thisBlock;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        versions = new RenderPatch[][]{
                getFullBlock(rpf, 0),
                getFullBlock(rpf, 1),
                getFullBlock(rpf, 2),
                getFullBlock(rpf, 3)
        };
        thisBlock = blkid;
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int thisMeta = mapDataCtx.getBlockData();
        int mask = 0;

        if(mapDataCtx.getBlockTypeIDAt(0,-1,0) == thisBlock && mapDataCtx.getBlockDataAt(0,-1,0) == thisMeta)
            mask |= 1;

        if(mapDataCtx.getBlockTypeIDAt(0,1,0) == thisBlock && mapDataCtx.getBlockDataAt(0,1,0) == thisMeta)
            mask |= 2;

        return versions[mask];
    }
}
