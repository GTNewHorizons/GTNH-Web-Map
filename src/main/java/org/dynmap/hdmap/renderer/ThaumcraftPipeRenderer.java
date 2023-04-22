package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ThaumcraftPipeRenderer extends PipeRendererBase {

    RenderPatch[][] pipesWithoutGoldBox;
    RenderPatch[][] pipesWithGoldBox;

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
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if(barr[dir.ordinal()] != 0)
                {
                    int id = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);

                    if(id == mapDataCtx.getBlockTypeID())
                        version |= dir.flag;
                }
            }
        }

        //version = (version & 3) | ((version & 12) << 2) | ((version &48) >> 2);

        return pipesWithoutGoldBox[version];
    }

    static String[] nbtFieldsNeeded = {"open"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
