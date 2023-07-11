package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CarpentersBlocksRenderer extends CustomRenderer {
    private RenderPatch[] fullBlock;
    // Patch index ordering, corresponding to BlockStep ordinal order
    private static final int patchlist[] = { 0, 1, 4, 5, 2, 3 };

    RenderPatch[][] shapes = new RenderPatch[256][];
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchlist);
        fullBlock = list.toArray(new RenderPatch[patchlist.length]);

        String type = custparm.get("type");
        if(type == null)
            type = "";

        if(type.equals("slope")){
            initSlopeShapes(rpf);

        } else if(type.equals("stairs")){
            initStairsShapes(rpf);
        } else if(type.equals("block")){
            initBlockShapes(rpf);
        }

        return true;
    }

    private void initBlockShapes(RenderPatchFactory rpf) {
        shapes[0] = fullBlock;
        shapes[1] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 0.5, 0.0, 1.0, 0.0, 1.0,0, false);
        shapes[2] = CustomRenderer.getBoxSingleTexture(rpf, 0.5, 1.0, 0.0, 1.0, 0.0, 1.0,0, false);
        shapes[3] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.0, 0.5, 0.0, 1.0,0, false);
        shapes[4] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.5, 1.0, 0.0, 1.0,0, false);
        shapes[5] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.0, 1.0, 0.0, 0.5,0, false);
        shapes[6] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.0, 1.0, 0.5, 1.0,0, false);
    }

    private void initStairsShapes(RenderPatchFactory rpf) {
        shapes[8] = ArchitectureCraftShapeRenderer.makeStairs(rpf);
        shapes[9] = getRotatedSet(rpf, shapes[8], 0, 180, 0);
        shapes[10] = getRotatedSet(rpf, shapes[8], 0, 270, 0);
        shapes[11] = getRotatedSet(rpf, shapes[8], 0, 90, 0);
    }

    private void initSlopeShapes(RenderPatchFactory rpf) {
        shapes[8] = ArchitectureCraftShapeRenderer.makeRoofPatches(rpf);
        shapes[9] = getRotatedSet(rpf, shapes[8], 0, 180, 0);
        shapes[10] = getRotatedSet(rpf, shapes[8], 0, 270, 0);
        shapes[11] = getRotatedSet(rpf, shapes[8], 0, 90, 0);

        shapes[45] = getSpikeTop(rpf);
    }

    private RenderPatch[] getSpikeTop(RenderPatchFactory rpf) {
        RenderPatch[] ret = new RenderPatch[5];

        ret[0] = rpf.getPatch(0,0,0, 1,0,0,0,0,1,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0);

        ret[1] = rpf.getPatch(0,0,0, 0.5,0.5,0.5,1,0,0, 1, RenderPatchFactory.SideVisible.TOP, 0);
        ret[2] = rpf.getPatch(1,0,0, 0.5,0.5,0.5,1,0,1, 1, RenderPatchFactory.SideVisible.TOP, 0);
        ret[3] = rpf.getPatch(1,0,1, 0.5,0.5,0.5,0,0,1, 1, RenderPatchFactory.SideVisible.TOP, 0);
        ret[4] = rpf.getPatch(0,0,1, 0.5,0.5,0.5,0,0,0, 1, RenderPatchFactory.SideVisible.TOP, 0);

        return ret;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        Object objMetaData = mapDataCtx.getBlockTileEntityField("cbMetadata");
        int metaData = 0;

        if(objMetaData instanceof Integer)
            metaData = ((Integer)objMetaData).intValue();
        else if(objMetaData instanceof Short)
            metaData = ((Short)objMetaData).intValue();
        else
            return fullBlock;

        if(metaData >= 0 && metaData < shapes.length && shapes[metaData] != null)
            return shapes[metaData];

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

                for (Object attrSet : attrList) {
                    if (attrSet instanceof HashMap) {
                        HashMap<String, Object> attrs = (HashMap<String, Object>) attrSet;

                        Object strId = attrs.get("cbUniqueId");
                        int id;
                        if(strId instanceof String){
                            id = GWM_Util.blockNameToId((String)strId);
                        }
                        else {
                            id = GWM_Util.objectToInt(attrs.get("id"), 0);
                        }

                        int data = GWM_Util.objectToInt(attrs.get("Damage"), 0);

                        map = TexturePack.HDTextureMap.getMap(id, data, 0);

                        if (map != null)
                            break;
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
