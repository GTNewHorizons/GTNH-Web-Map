package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.HashMap;
import java.util.Map;

public class LogisticsPipesPipeRenderer extends PipeRendererBase{
    RenderPatch[][] pipes;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        pipes = generateSingleSize(rpf, 4.0/16, 4.0/16, 0, 0);
        BuildCraftPipeRenderer.buildCraftCompatiblePipeBlocks.set(blkid);
        return true;
    }
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        Object bcPipeNbt = mapDataCtx.getBlockTileEntityField("BC_Pipe_NBT");
        int open = 63;
        if(bcPipeNbt instanceof HashMap){
            HashMap hm = (HashMap) bcPipeNbt;
            open = GWM_Util.objectToInt(hm.get("inputOpen"), 63);
        }
        int version = 0;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if((open & (1 << dir.ordinal())) != 0)
            {
                int id = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);

                if(BuildCraftPipeRenderer.buildCraftCompatiblePipeBlocks.get(id))
                    version |= dir.flag;
            }
        }

        return pipes[version];
    }


    static String[] nbtFieldsNeeded = {"BC_Pipe_NBT"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
