package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.Map;

public class ArchitectureCraftShapeRenderer extends CustomRenderer {
    RenderPatch[] basicBox;
    private static final int patchlist[] = { 1, 4, 2, 5, 0, 3 };
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchlist);
        basicBox = list.toArray(new RenderPatch[patchlist.length]);

        return true;
    }
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        Object shape = mapDataCtx.getBlockTileEntityField("Shape");
        if(shape instanceof Integer)
        {
            int actualShape = ((Integer)shape).intValue();

            switch (actualShape)
            {
                case 0:

                    break;
            }
        }

        return basicBox;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new ArchitectureCraftTextureLookupThing(mapDataCtx));
    }

    static String[] nbtFieldsNeeded = {"turn", "Shape", "side", "BaseName", "BaseData"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class ArchitectureCraftTextureLookupThing implements CustomTextureMapper {

        private MapDataContext mapDataCtx;
        int[] textures = new int[0];

        public ArchitectureCraftTextureLookupThing(MapDataContext mapDataCtx) {

            this.mapDataCtx = mapDataCtx;

            Object blockNameObj = mapDataCtx.getBlockTileEntityField("BaseName");
            Object blockDataObj = mapDataCtx.getBlockTileEntityField("BaseData");

            if(blockNameObj instanceof String){
                String blockName = (String)blockNameObj;
                int data = 0;
                if(blockDataObj instanceof Integer) {
                    data = ((Integer) blockDataObj).intValue();
                }

                int blockId = GWM_Util.blockNameToId(blockName);

                TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blockId, data, 0);

                textures = new int[]{ map.getIndexForFace(0), map.getIndexForFace(1), map.getIndexForFace(2), map.getIndexForFace(3), map.getIndexForFace(4), map.getIndexForFace(5) };
            }
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return new int[]{ textures[patchId] };
        }
    }
}
