package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ChiselCTMVHRenderer extends CustomRenderer {
    static RenderPatch[][] vertical, horizontal;
    int thisBlock;
    boolean isHorizontal;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if(vertical == null) {
            vertical = new RenderPatch[][]{
                    getFullBlock(rpf, new int[]{4, 4, 0, 0, 0, 0}),
                    getFullBlock(rpf, new int[]{4, 4, 1, 1, 1, 1}),
                    getFullBlock(rpf, new int[]{4, 4, 2, 2, 2, 2}),
                    getFullBlock(rpf, new int[]{4, 4, 3, 3, 3, 3})
            };
        }
        if(horizontal == null){
            horizontal = new RenderPatch[][]{
                    getFullBlock(rpf, new int[]{4, 4, 0, 0, 0, 0}),
                    getFullBlock(rpf, new int[]{4, 4, 2, 1, 0, 0}),
                    getFullBlock(rpf, new int[]{4, 4, 1, 2, 0, 0}),
                    getFullBlock(rpf, new int[]{4, 4, 3, 3, 0, 0}),

                    getFullBlock(rpf, new int[]{4, 4, 0, 0, 2, 1}),
                    getFullBlock(rpf, new int[]{4, 4, 2, 1, 2, 1}),
                    getFullBlock(rpf, new int[]{4, 4, 1, 2, 2, 1}),
                    getFullBlock(rpf, new int[]{4, 4, 3, 3, 2, 1}),

                    getFullBlock(rpf, new int[]{4, 4, 0, 0, 1, 2}),
                    getFullBlock(rpf, new int[]{4, 4, 2, 1, 1, 2}),
                    getFullBlock(rpf, new int[]{4, 4, 1, 2, 1, 2}),
                    getFullBlock(rpf, new int[]{4, 4, 3, 3, 1, 2}),

                    getFullBlock(rpf, new int[]{4, 4, 0, 0, 3, 3}),
                    getFullBlock(rpf, new int[]{4, 4, 2, 1, 3, 3}),
                    getFullBlock(rpf, new int[]{4, 4, 1, 2, 3, 3}),
                    getFullBlock(rpf, new int[]{4, 4, 3, 3, 3, 3})
            };
        }

        isHorizontal = custparm.containsKey("horizontal");

        thisBlock = blkid;
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int thisMeta = mapDataCtx.getBlockData();
        int mask = 0;

        if(isHorizontal){
            if(mapDataCtx.getBlockTypeIDAt(-1,0,0) == thisBlock && mapDataCtx.getBlockDataAt(-1,0,0) == thisMeta)
                mask |= 4;

            if(mapDataCtx.getBlockTypeIDAt(1,0,0) == thisBlock && mapDataCtx.getBlockDataAt(1,0,0) == thisMeta)
                mask |= 8;

            if(mapDataCtx.getBlockTypeIDAt(0,0,-1) == thisBlock && mapDataCtx.getBlockDataAt(0,0,-1) == thisMeta)
                mask |= 2;

            if(mapDataCtx.getBlockTypeIDAt(0,0,1) == thisBlock && mapDataCtx.getBlockDataAt(0,0,1) == thisMeta)
                mask |= 1;

            return horizontal[mask];
        }

        if(mapDataCtx.getBlockTypeIDAt(0,-1,0) == thisBlock && mapDataCtx.getBlockDataAt(0,-1,0) == thisMeta)
            mask |= 1;

        if(mapDataCtx.getBlockTypeIDAt(0,1,0) == thisBlock && mapDataCtx.getBlockDataAt(0,1,0) == thisMeta)
            mask |= 2;

        return vertical[mask];
    }
}
