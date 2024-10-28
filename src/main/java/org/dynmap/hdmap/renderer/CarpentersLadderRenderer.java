package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class CarpentersLadderRenderer extends CarpentersBlocksRenderer {

    RenderPatch[][] basics = new RenderPatch[4][];
    RenderPatch[][] basicsRot = new RenderPatch[4][];

    int thisBlockId;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        super.initializeRenderer(rpf, blkid, blockdatamask, custparm);

        thisBlockId = blkid;

        RenderPatch[] ribs = getRibs(rpf);
        RenderPatch[] lowLeg = getBoxSingleTextureInt(rpf, 0,2,0,16,6,10, 0, false);
        RenderPatch[] highLeg = getBoxSingleTextureInt(rpf, 14,16,0,16,6,10, 0, false);

        basics[0] = combineMultiple(lowLeg, ribs, highLeg);
        basics[1] = combineMultiple(ribs, highLeg);
        basics[2] = combineMultiple(lowLeg, ribs);
        basics[3] = ribs;

        for(int i = 0; i < 4; i++)
            basicsRot[i] = getRotatedSet(rpf, basics[i], 0, 90, 0, false);

        shapes[8] = combineMultiple(
                ribs,
                getBoxSingleTextureInt(rpf, 2,4,0,16,6,7,7, false),
                getBoxSingleTextureInt(rpf, 2,4,0,16,9,10,7, false),
                getBoxSingleTextureInt(rpf, 12,14,0,16,6,7,7, false),
                getBoxSingleTextureInt(rpf, 12,14,0,16,9,10,7, false)
        );

        shapes[9] = getRotatedSet(rpf, shapes[8], 0, 90, 0, false);

        shapes[16] = combineMultiple(
                getBoxSingleTextureInt(rpf, 6, 10, 0, 16, 6, 10, 0, false),
                getBoxSingleTextureInt(rpf, 1, 6, 2, 3, 7, 9, 0, false),
                getBoxSingleTextureInt(rpf, 10, 15, 12, 13, 7, 9, 0, false)
        );
        shapes[17] = getRotatedSet(rpf, shapes[16], 0, 90, 0, false);

        return true;
    }

    RenderPatch[] getRibs(RenderPatchFactory rpf){
        return combineMultiple(
                getBoxSingleTextureInt(rpf, 0, 16,2,3,7,9,0,false),
                getBoxSingleTextureInt(rpf, 0, 16,6,7,7,9,0,false),
                getBoxSingleTextureInt(rpf, 0, 16,10,11,7,9,0,false),
                getBoxSingleTextureInt(rpf, 0, 16,14,15,7,9,0,false)
        );
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int meta = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("cbMetadata"), -1);

        if(meta == 0){
            int combo = 0;
            if(mapDataCtx.getBlockTypeIDAt(-1,0,0) == thisBlockId)
                combo |= 1;
            if(mapDataCtx.getBlockTypeIDAt(1,0,0) == thisBlockId)
                combo |= 2;

            return basics[combo];
        } else if(meta == 1){
            int combo = 0;
            if(mapDataCtx.getBlockTypeIDAt(0,0,-1) == thisBlockId)
                combo |= 1;
            if(mapDataCtx.getBlockTypeIDAt(0,0,1) == thisBlockId)
                combo |= 2;

            return basicsRot[combo];
        }

        return super.getRenderPatchList(mapDataCtx);
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new TextureSelector(mapDataCtx, 8));
    }
}
