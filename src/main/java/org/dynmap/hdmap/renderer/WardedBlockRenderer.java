package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;
import scala.Int;

import java.util.ArrayList;
import java.util.Map;

public class WardedBlockRenderer extends CustomRenderer {
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
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        return new CustomRendererData(fullBlock, null, new Texturer(mapDataCtx.getBlockTileEntityField("bi"), mapDataCtx.getBlockTileEntityField("md")));
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    static String[] nbtFieldsNeeded = {"md", "bi"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class Texturer implements CustomTextureMapper{

        TexturePack.HDTextureMap map;

        public Texturer(Object bi, Object md) {
            int blockId = GWM_Util.objectToInt(bi,0);
            int meta = GWM_Util.objectToInt(md,0);

            if(blockId != 0){
                map = TexturePack.HDTextureMap.getMap(blockId, meta, 0);
            }
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(map == null)
                return null;
            return new int[]{map.getIndexForFace(patchId)};
        }
    }
}
