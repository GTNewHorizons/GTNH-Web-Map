package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.Map;

public class TinkersClearGlassBlockRenderer extends CustomRenderer {
    int block;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        block = blkid;
        return true;
    }
    int getVersion(MapDataContext mapDataCtx, ForgeDirection down, ForgeDirection up, ForgeDirection left, ForgeDirection right){
        int version = 0;

        if(mapDataCtx.getBlockTypeIDAt(down.offsetX, down.offsetY, down.offsetZ) == block)
            version |= 1;

        if(mapDataCtx.getBlockTypeIDAt(up.offsetX, up.offsetY, up.offsetZ) == block)
            version |= 2;

        if(mapDataCtx.getBlockTypeIDAt(left.offsetX, left.offsetY, left.offsetZ) == block)
            version |= 4;

        if(mapDataCtx.getBlockTypeIDAt(right.offsetX, right.offsetY, right.offsetZ) == block)
            version |= 8;

        return version;
    }
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int[] versions = new int[6];

        for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            switch (dir) {
                case DOWN:
                    versions[0] = getVersion(mapDataCtx, ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST);
                    break;
                case UP:
                    versions[1] = getVersion(mapDataCtx, ForgeDirection.SOUTH, ForgeDirection.NORTH, ForgeDirection.WEST, ForgeDirection.EAST);
                    break;
                case NORTH:
                    versions[4] = getVersion(mapDataCtx, ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.EAST, ForgeDirection.WEST);
                    break;
                case SOUTH:
                    versions[5] = getVersion(mapDataCtx, ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.WEST, ForgeDirection.EAST);
                    break;
                case WEST:
                    versions[2] = getVersion(mapDataCtx, ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.NORTH, ForgeDirection.SOUTH);
                    break;
                case EAST:
                    versions[3] = getVersion(mapDataCtx, ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.SOUTH, ForgeDirection.NORTH);
                    break;
                case UNKNOWN:
                    break;
            }

        }

        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0, 1, 0, 1, 0, 1, versions);

        return list.toArray(new RenderPatch[versions.length]);
    }

}
