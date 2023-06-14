package org.dynmap.hdmap.renderer;

import net.minecraft.client.renderer.entity.Render;
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
        }

        return true;
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

    RenderPatch[] getRotatedSet(RenderPatchFactory rpf, RenderPatch[] input, int rotX, int rotY, int rotZ){
        RenderPatch[] ret = new RenderPatch[input.length];

        for (int i = 0; i < input.length; i++){
            ret[i] = rpf.getRotatedPatch(input[i], 0, rotY, 0, input[i].getTextureIndex());
        }
        if(rotX != 0) {
            for (int i = 0; i < input.length; i++){
                ret[i] = rpf.getRotatedPatch(ret[i], rotX, 0, 0, ret[i].getTextureIndex());
            }
        }
        if(rotZ != 0) {
            for (int i = 0; i < input.length; i++){
                ret[i] = rpf.getRotatedPatch(ret[i], 0, 0, rotZ, ret[i].getTextureIndex());
            }
        }
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
