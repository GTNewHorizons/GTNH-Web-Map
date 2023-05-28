package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultipartRenderer extends CustomRenderer {
    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    private RenderPatch[] fullBlock;

    private RenderPatch[][] facePatches;
    private RenderPatch[][] postPatches;
    private RenderPatch[][] cornerPatches;
    private RenderPatch[][] edgePatches;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, fullBlockPatchList);
        fullBlock = list.toArray(new RenderPatch[fullBlockPatchList.length]);



        initializeFacePatches(rpf);
        initializePostPatches(rpf);
        initializeCornerPatches(rpf);
        initializeEdgePatches(rpf);

        return true;
    }

    private void initializeEdgePatches(RenderPatchFactory rpf) {
        edgePatches = new RenderPatch[256][];
        for(int c = 0; c < 12; c++) {
            for (int i = 0; i < 7; i++) {
                int shape = c + 16 * (i + 1);
                double negativeMax = (i + 1) * (1.0 / 8);
                double positiveMin = 1 - negativeMax;

                switch (c){
                    case 0: // NYN
                        edgePatches[shape] = makeBox(rpf, 0, negativeMax, 0, 1, 0, negativeMax);
                        break;
                    case 1: // NYP
                        edgePatches[shape] = makeBox(rpf, 0, negativeMax, 0, 1, positiveMin, 1);
                        break;
                    case 2: // PYN
                        edgePatches[shape] = makeBox(rpf, positiveMin,1, 0, 1, 0, negativeMax);
                        break;
                    case 3: // PYP
                        edgePatches[shape] = makeBox(rpf, positiveMin, 1, 0, 1, positiveMin, 1);
                        break;
                    case 4: // NNZ
                        edgePatches[shape] = makeBox(rpf, 0, negativeMax, 0, negativeMax, 0, 1);
                        break;
                    case 5: // PNZ
                        edgePatches[shape] = makeBox(rpf, positiveMin,1, 0, negativeMax, 0, 1);
                        break;
                    case 6: // NPZ
                        edgePatches[shape] = makeBox(rpf, 0, negativeMax, positiveMin,1, 0, 1);
                        break;
                    case 7: // PPZ
                        edgePatches[shape] = makeBox(rpf, positiveMin, 1, positiveMin,1, 0, 1);
                        break;
                    case 8: // XNN
                        edgePatches[shape] = makeBox(rpf, 0, 1, 0, negativeMax, 0, negativeMax);
                        break;
                    case 9: // XPN
                        edgePatches[shape] = makeBox(rpf, 0, 1, positiveMin, 1, 0, negativeMax);
                        break;
                    case 10: // XNP
                        edgePatches[shape] = makeBox(rpf, 0, 1, 0, negativeMax, positiveMin, 1);
                        break;
                    case 11: // ZPP
                        edgePatches[shape] = makeBox(rpf, 0, 1, positiveMin, 1, positiveMin, 1);
                        break;

                }
            }
        }
    }

    private void initializeCornerPatches(RenderPatchFactory rpf) {
        cornerPatches = new RenderPatch[256][];

        for(int c = 0; c < 8; c++){
            for(int i = 0; i < 7; i++){
                int shape = c + 16*(i+1);
                double negativeMax = (i+1)*(1.0/8);
                double positiveMin = 1 - negativeMax;
                switch (c){
                    case 0: // NNN
                        cornerPatches[shape] = makeBox(rpf, 0, negativeMax, 0, negativeMax, 0, negativeMax);
                        break;
                    case 1: // NPN
                        cornerPatches[shape] = makeBox(rpf, 0, negativeMax, positiveMin, 1, 0, negativeMax);
                        break;
                    case 2: // NNP
                        cornerPatches[shape] = makeBox(rpf, 0, negativeMax, 0, negativeMax, positiveMin,1);
                        break;
                    case 3: // NPP
                        cornerPatches[shape] = makeBox(rpf, 0, negativeMax, positiveMin, 1, positiveMin, 1);
                        break;
                    case 4: // PNN
                        cornerPatches[shape] = makeBox(rpf, positiveMin,1, 0, negativeMax, 0, negativeMax);
                        break;
                    case 5: // PPN
                        cornerPatches[shape] = makeBox(rpf, positiveMin, 1, positiveMin, 1, 0, negativeMax);
                        break;
                    case 6: // PNP
                        cornerPatches[shape] = makeBox(rpf, positiveMin,1, 0, negativeMax, positiveMin,1);
                        break;
                    case 7: // PPP
                        cornerPatches[shape] = makeBox(rpf, positiveMin,1, positiveMin,1, positiveMin,1);
                        break;
                }
            }
        }
    }

    private void initializePostPatches(RenderPatchFactory rpf) {
        postPatches = new RenderPatch[256][];

        for(int i = 0; i < 7; i++) {
            double min = 0.5 - (i+1.0) / 16;
            double max = 1- min;

            postPatches[0 + 16*(i+1)] = makeBox(rpf, min, max, 0, 1, min, max);
            postPatches[1 + 16*(i+1)] = makeBox(rpf, min, max, min, max, 0, 1);
            postPatches[2 + 16*(i+1)] = makeBox(rpf, 0, 1, min, max, min, max);
        }
    }



    private void initializeFacePatches(RenderPatchFactory rpf) {
        facePatches = new RenderPatch[128][];
        for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS){
            for(int i = 0; i < 7; i++){
                int id = dir.ordinal() + 16 * (i+1);
                switch (dir){
                    case DOWN:
                        facePatches[id] = makeBox(rpf, 0,1,0, (i+1) / 8.0, 0, 1);
                        break;
                    case UP:
                        facePatches[id] = makeBox(rpf, 0,1,1 - (i+1) / 8.0, 1, 0, 1);
                        break;
                    case NORTH:
                        facePatches[id] = makeBox(rpf, 0,1, 0, 1,0, (i+1) / 8.0);
                        break;
                    case SOUTH:
                        facePatches[id] = makeBox(rpf, 0,1, 0, 1,1 - (i+1) / 8.0, 1);
                        break;
                    case WEST:
                        facePatches[id] = makeBox(rpf, 0, (i+1) / 8.0, 0,1,0, 1);
                        break;
                    case EAST:
                        facePatches[id] = makeBox(rpf, 1 - (i+1) / 8.0, 1, 0,1,0, 1);
                        break;
                    case UNKNOWN:
                        break;
                }
            }
        }
    }

    RenderPatch[] makeBox(RenderPatchFactory rpf, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax){
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, xmin, xmax, ymin, ymax,zmin, zmax, fullBlockPatchList);
        return list.toArray(new RenderPatch[fullBlockPatchList.length]);
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        Object rawParts = mapDataCtx.getBlockTileEntityField("parts");

        MyRenderData mrd = new MyRenderData();
        RenderPatchFactory rpf = mapDataCtx.getPatchFactory();

        if(rawParts instanceof ArrayList){
            ArrayList parts = (ArrayList) rawParts;

            for(Object tmp : parts){
                if(tmp instanceof HashMap){
                    HashMap fields = (HashMap<String, Object>) tmp;

                    Object rawId = fields.get("id");
                    if(rawId != null){
                        String strId = (String)rawId;
                        int shape = 0;
                        Object rawShape = fields.get("shape");
                        if(rawShape instanceof Byte){
                            shape = ((Byte)rawShape).byteValue();
                        }else if(rawShape instanceof Short){
                            shape = ((Short)rawShape).shortValue();
                        }else if(rawShape instanceof Integer){
                            shape = ((Integer)rawShape).intValue();
                        }

                        String mat = "minecraft:stone";
                        Object objMaterial = fields.get("material");
                        if(objMaterial != null)
                            mat = (String)objMaterial;


                        if(strId.equals("mcr_face")){
                            if(facePatches[shape] != null)
                                mrd.addSimpleShape(rpf, facePatches[shape], mat);
                        } else if(strId.equals("mcr_post")){
                            if(postPatches[shape] != null)
                                mrd.addSimpleShape(rpf, postPatches[shape], mat);
                        } else if(strId.equals("mcr_edge")){
                            if(edgePatches[shape] != null)
                                mrd.addSimpleShape(rpf, edgePatches[shape], mat);
                        } else if(strId.equals("mcr_cnr")){
                            if(cornerPatches[shape] != null)
                                mrd.addSimpleShape(rpf, cornerPatches[shape], mat);
                        }

                    }

                }
            }
        }

        return mrd;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        return fullBlock;
    }

    static String[] nbtFieldsNeeded = {"parts"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class MyRenderData extends CustomRendererData implements CustomTextureMapper {
        public MyRenderData() {
            super(null, null, null);
        }
        List<RenderPatch> patches = new ArrayList<>();
        int patchNum = 0;
        HashMap<Integer, Integer> patchToTex = new HashMap<>();

        public void addSimpleShape(RenderPatchFactory rpf, RenderPatch[] patches, String block){
            String blockName = block;
            int data = 0;
            int lastIndexOfUnderscore = block.lastIndexOf('_');
            if(lastIndexOfUnderscore > 0){
                blockName = block.substring(0, lastIndexOfUnderscore);
                data = Integer.parseInt(block.substring(lastIndexOfUnderscore+1));
            }

            int blockId = GWM_Util.blockNameToId(blockName);
            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blockId, data, 0);

            for(RenderPatch rp : patches){
                patchToTex.put(patchNum, map.getIndexForFace(rp.getTextureIndex()));
                this.patches.add(rpf.getRotatedPatch(rp, 0,0,0, patchNum++));
            }
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return new int[]{patchToTex.get(patchId).intValue()};
        }

        @Override
        public RenderPatch[] getCustomMesh() {
            return patches.toArray(new RenderPatch[patches.size()]);
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return this;
        }
    }
}
