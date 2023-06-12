package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class CarpentersBlocksRenderer extends CustomRenderer {
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

        TexturePack.HDTextureMap map;
        public TextureSelector(MapDataContext mapDataCtx) {
            Object objAttrList = mapDataCtx.getBlockTileEntityField("cbAttrList");

            if(objAttrList instanceof ArrayList){
                ArrayList attrList = (ArrayList) objAttrList;

                if(attrList.size() > 0){
                    Object first = attrList.get(0);
                    if(first instanceof HashMap){
                        HashMap<String, Object> attrs = (HashMap<String, Object>) first;

                        Object blockId = attrs.get("id");
                        Object blockData = attrs.get("Damage");

                        if(blockId instanceof Integer && blockData instanceof Integer){
                            int id = ((Integer)blockId).intValue();
                            int data = ((Integer)blockData).intValue();

                            map = TexturePack.HDTextureMap.getMap(id, data, 0);
                        } else if(blockId instanceof Short && blockData instanceof Short){
                            int id = ((Short)blockId).intValue();
                            int data = ((Short)blockData).intValue();

                            map = TexturePack.HDTextureMap.getMap(id, data, 0);
                        } else {
                            blockId.toString();
                        }
                    }
                }
            }
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(map != null)
                return new int[]{map.getIndexForFace(patchId)};
            return new int[0];
        }
    }

    static String[] nbtFieldsNeeded = {"cbMetadata", "cbAttrList"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
