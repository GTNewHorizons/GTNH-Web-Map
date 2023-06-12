package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.Map;

public class ChiselCTMBlockRenderer extends CustomRenderer {
    private RenderPatch[] fullBlock;
    // Patch index ordering, corresponding to BlockStep ordinal order
    private static final int patchlist[] = { 0, 1, 4, 5, 2, 3 };
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchlist);
        fullBlock = list.toArray(new RenderPatch[patchlist.length]);

        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new TextureSelector(mapDataCtx));
    }

    class TextureSelector implements CustomTextureMapper {

        private final MapDataContext mapDataCtx;

        int block, data, baseTex;

        public TextureSelector(MapDataContext mapDataCtx) {

            this.mapDataCtx = mapDataCtx;
            block = mapDataCtx.getBlockTypeID();
            data = mapDataCtx.getBlockData();
            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(block, data, 0);
            baseTex = map.getIndexForFace(0);
        }

        int getVersion(ForgeDirection down, ForgeDirection up, ForgeDirection left, ForgeDirection right){
            int version = 0;

            if(mapDataCtx.getBlockTypeIDAt(down.offsetX, down.offsetY, down.offsetZ) != block ||  mapDataCtx.getBlockDataAt(down.offsetX, down.offsetY, down.offsetZ) != data)
                version |= 1;

            if(mapDataCtx.getBlockTypeIDAt(up.offsetX, up.offsetY, up.offsetZ) != block ||  mapDataCtx.getBlockDataAt(up.offsetX, up.offsetY, up.offsetZ) != data)
                version |= 2;

            if(mapDataCtx.getBlockTypeIDAt(left.offsetX, left.offsetY, left.offsetZ) != block ||  mapDataCtx.getBlockDataAt(left.offsetX, left.offsetY, left.offsetZ) != data)
                version |= 4;

            if(mapDataCtx.getBlockTypeIDAt(right.offsetX, right.offsetY, right.offsetZ) != block ||  mapDataCtx.getBlockDataAt(right.offsetX, right.offsetY, right.offsetZ) != data)
                version |= 8;

            return version;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            ForgeDirection dir = ForgeDirection.getOrientation(patchId);

            int version = 0;
            switch (dir){
                case DOWN:
                    version = getVersion(ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST);
                    break;
                case UP:
                    version = getVersion(ForgeDirection.SOUTH, ForgeDirection.NORTH, ForgeDirection.WEST, ForgeDirection.EAST);
                    break;
                case NORTH:
                    version = getVersion(ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.EAST, ForgeDirection.WEST);
                    break;
                case SOUTH:
                    version = getVersion(ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.WEST, ForgeDirection.EAST);
                    break;
                case WEST:
                    version = getVersion(ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.NORTH, ForgeDirection.SOUTH);
                    break;
                case EAST:
                    version = getVersion(ForgeDirection.DOWN, ForgeDirection.UP, ForgeDirection.SOUTH, ForgeDirection.NORTH);
                    break;
                case UNKNOWN:
                    break;
            }

            return new int[]{baseTex + version};
        }
    }
}
