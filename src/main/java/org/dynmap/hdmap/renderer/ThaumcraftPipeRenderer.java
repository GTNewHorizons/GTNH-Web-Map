package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ThaumcraftPipeRenderer extends PipeRendererBase {

    RenderPatch[][] pipesWithoutGoldBox;
    RenderPatch[][] pipesWithGoldBox;

    int[] xOff = {0,0,-1,1,0,0};
    int[] yOff = {-1,1,0,0,0,0};
    int[] zOff = {0,0,0,0,-1,1};
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        pipesWithoutGoldBox = generateSingleSize(rpf, 1.0/16, 1.0/8, 0, 0);
        pipesWithGoldBox = generateSingleSize(rpf, 1.0/16, 1.0/6, 0, 1);
        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        Object open = mapDataCtx.getBlockTileEntityField("open");
        int version = 0;
        if(open instanceof byte[])
        {
            byte[] barr = (byte[])open;
            for (int i = 0; i < 6; i++) {
                if(barr[i] != 0)
                {
                    int id = mapDataCtx.getBlockTypeIDAt(xOff[i], yOff[i], zOff[i]);

                    if(id == mapDataCtx.getBlockTypeID())
                        version |= 1 << i;
                }
            }
        }

        version = (version & 3) | ((version & 12) << 2) | ((version &48) >> 2);

        return pipesWithoutGoldBox[version];
    }

    static String[] nbtFieldsNeeded = {"open"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
