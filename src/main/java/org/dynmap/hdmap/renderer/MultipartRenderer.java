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
    public static MultipartRenderer INSTANCE = null;

    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    private RenderPatch[] fullBlock;
    private RenderPatch[][] facePatches;
    private RenderPatch[][] postPatches;
    private RenderPatch[][] cornerPatches;
    private RenderPatch[][] edgePatches;
    private RenderPatch[][] hollowPatches;

    public int blockId = -1000;

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
        initializeHollowPatches(rpf);

        blockId = blkid;
        INSTANCE = this;

        if(MECableRenderer.INSTANCE != null){
            MECableRenderer.INSTANCE.multipartBlockId = blkid;
        }

        return true;
    }

    private void initializeHollowPatches(RenderPatchFactory rpf) {
        hollowPatches = new RenderPatch[128][];
        for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS){
            for(int i = 0; i < 7; i++){
                int id = dir.ordinal() + 16 * (i+1);
                double w = (i+1) / 8.0;
                switch (dir){
                    case DOWN:
                        hollowPatches[id] = combineMultiple(makeBox(rpf, 0, 0.25, 0, w, 0, 1),
                                makeBox(rpf, 0.75, 1, 0, w, 0, 1),
                                makeBox(rpf, 0.25, 0.75, 0, w, 0, 0.25),
                                makeBox(rpf, 0.25, 0.75, 0, w, 0.75, 1));
                        break;
                    case UP:
                        hollowPatches[id] = combineMultiple(makeBox(rpf, 0, 0.25, 1 - w, 1, 0, 1),
                                makeBox(rpf, 0.75, 1, 1 - w, 1, 0, 1),
                                makeBox(rpf, 0.25, 0.75, 1 - w, 1, 0, 0.25),
                                makeBox(rpf, 0.25, 0.75, 1 - w, 1, 0.75, 1));
                        break;
                    case NORTH:
                        hollowPatches[id] = combineMultiple(makeBox(rpf, 0, 0.25, 0, 1, 0, w),
                                makeBox(rpf, 0.75, 1, 0, 1, 0, w),
                                makeBox(rpf, 0.25, 0.75, 0, 0.25, 0, w),
                                makeBox(rpf, 0.25, 0.75, 0.75, 1, 0, w));
                        break;
                    case SOUTH:
                        hollowPatches[id] = combineMultiple(makeBox(rpf, 0, 0.25, 0, 1, 1 - w, 1),
                                makeBox(rpf, 0.75, 1, 0, 1, 1 - w, 1),
                                makeBox(rpf, 0.25, 0.75, 0, 0.25, 1 - w, 1),
                                makeBox(rpf, 0.25, 0.75, 0.75, 1, 1 - w, 1));
                        break;
                    case WEST:
                        hollowPatches[id] = combineMultiple(makeBox(rpf, 0, w, 0, 0.25, 0, 1),
                                makeBox(rpf, 0, w, 0.75, 1, 0, 1),
                                makeBox(rpf, 0, w, 0.25, 0.75, 0, 0.25),
                                makeBox(rpf, 0, w, 0.25, 0.75, 0.75, 1));
                        break;
                    case EAST:
                        hollowPatches[id] = combineMultiple(makeBox(rpf, 1 - w, 1, 0, 0.25, 0, 1),
                                makeBox(rpf, 1 - w, 1, 0.75, 1, 0, 1),
                                makeBox(rpf, 1 - w, 1, 0.25, 0.75, 0, 0.25),
                                makeBox(rpf, 1 - w, 1, 0.25, 0.75, 0.75, 1));
                        break;
                    case UNKNOWN:
                        break;
                }
            }
        }
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

    RenderPatch[] combineMultiple(RenderPatch[]... list){
        int count = 0;
        for(RenderPatch[] rp : list){
            count += rp.length;
        }
        RenderPatch[] ret = new RenderPatch[count];

        int j = 0;
        for(RenderPatch[] rp : list){
            for(int i = 0; i < rp.length; i++)
                ret[j++] = rp[i];
        }
        return ret;
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
                        } else if(strId.equals("mcr_hllw")){
                            if(hollowPatches[shape] != null)
                                mrd.addSimpleShape(rpf, hollowPatches[shape], mat);
                        } else if(strId.equals("ae2_cablebus") && MECableRenderer.INSTANCE != null){
                            CustomRendererData crd = MECableRenderer.INSTANCE.getRenderData(mapDataCtx);
                            mrd.addComplexShape(rpf, crd, MECableRenderer.INSTANCE.meCableBusBlockId, 0);
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
                try {
                    data = Integer.parseInt(block.substring(lastIndexOfUnderscore + 1));
                    if(data >= 0 && data <= 15) {
                        blockName = block.substring(0, lastIndexOfUnderscore);
                    } else {
                        data = 0;
                    }
                } catch(NumberFormatException nfe){
                    data = 0;
                }
            }

            int blockId = GWM_Util.blockNameToId(blockName, true);

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

        public void addComplexShape(RenderPatchFactory rpf, CustomRendererData crd, int blockId, int blockMeta) {
            CustomTextureMapper ctm = crd.getCustomTextureMapper();

            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blockId, blockMeta, 0);

            if(ctm != null) {
                for (RenderPatch rp : crd.getCustomMesh()) {
                    int[] textureLayersForPatchId = ctm.getTextureLayersForPatchId(rp.getTextureIndex());
                    if (textureLayersForPatchId != null) {
                        if(textureLayersForPatchId.length > 0) {
                            patchToTex.put(patchNum, textureLayersForPatchId[0]);
                            this.patches.add(rpf.getRotatedPatch(rp, 0, 0, 0, patchNum++));
                        }
                    } else {
                        patchToTex.put(patchNum, map.getIndexForFace(rp.getTextureIndex()));
                        this.patches.add(rpf.getRotatedPatch(rp, 0, 0, 0, patchNum++));
                    }
                }
            } else {
                for (RenderPatch rp : crd.getCustomMesh()) {
                    patchToTex.put(patchNum, map.getIndexForFace(rp.getTextureIndex()));
                    this.patches.add(rpf.getRotatedPatch(rp, 0, 0, 0, patchNum++));
                }
            }
        }
    }
}
