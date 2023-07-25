package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.HashMap;
import java.util.Map;

public class ExtraUtilitiesPipeRenderer extends PipeRendererBase {
    RenderPatch[][] pipeVersions;
    RenderPatch[][] pipeVersionsBigCenter;
    RenderPatch[][] connectors;
    static HashMap<Integer, Boolean> connectability = new HashMap<>();
    boolean isnode;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        pipeVersions = generateSingleSize(rpf, 2.0/16, 2.0/16, 0, 0);
        pipeVersionsBigCenter = generateSingleSize(rpf, 2.0/16, 5.0/16, 0, 6);


        isnode = custparm.containsKey("isnode");
        connectability.put(blkid, isnode);

        connectors = new RenderPatch[6][];
        connectors[0] = combineMultiple(
                getBoxSingleTexture(rpf, 1.0/16, 15.0/16, 0, 1.0/16, 1.0/16, 15.0/16, 6, false),
                getBoxSingleTexture(rpf, 3.0/16, 12.0/16, 1.0/16, 4.0/16, 3.0/16, 12.0/16, 6, false),
                getBoxSingleTexture(rpf, 6.0/16, 10.0/16, 4.0/16, 6.0/16, 6.0/16, 10.0/16, 6, false));

        connectors[1] = CustomRenderer.getRotatedSet(rpf, connectors[0], 180, 0, 0);
        connectors[2] = CustomRenderer.getRotatedSet(rpf, connectors[0], 270, 0, 0);
        connectors[3] = CustomRenderer.getRotatedSet(rpf, connectors[0], 90, 0, 0);
        connectors[4] = CustomRenderer.getRotatedSet(rpf, connectors[0], 0, 0, 90);
        connectors[5] = CustomRenderer.getRotatedSet(rpf, connectors[0], 0, 0, 270);

        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int version = 0;
        int myDir = mapDataCtx.getBlockData();
        for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS){
            if(isnode && dir.ordinal() == myDir)
                continue;

            int otherBlockId = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);
            Boolean check = connectability.get(otherBlockId);
            if(check == null)
                continue;

            if(check == false) {
                version |= dir.flag;
            } else if(mapDataCtx.getBlockDataAt(dir.offsetX, dir.offsetY, dir.offsetZ) != dir.getOpposite().ordinal()){
                version |= dir.flag;
            }
        }
        if (isnode) {
            if (myDir >= 12) {
                return pipeVersionsBigCenter[version];
            }
            myDir %= 6;
            return combineMultiple(connectors[myDir], pipeVersions[version]);
        }

        return pipeVersions[version];
    }
}
