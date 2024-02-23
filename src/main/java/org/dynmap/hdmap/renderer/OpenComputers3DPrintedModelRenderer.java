package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OpenComputers3DPrintedModelRenderer  extends CustomRenderer {

    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    private RenderPatch[] fullBlock;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, fullBlockPatchList);
        fullBlock = list.toArray(new RenderPatch[fullBlockPatchList.length]);

        return true;
    }
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        Object objState = mapDataCtx.getBlockTileEntityField("state");
        int state = GWM_Util.objectToInt(objState, 0);

        Object tmp = mapDataCtx.getBlockTileEntityField("data");
        if(tmp instanceof HashMap) {
            HashMap fields = (HashMap<String, Object>) tmp;

            ArrayList parts = state == 0 ? (ArrayList) fields.get("stateOff") : (ArrayList)fields.get("stateOn");


            ArrayList<RenderPatch[]> boxList = new ArrayList<>();
            ArrayList<Integer> tintList = new ArrayList<>();
            ArrayList<Integer> textureList = new ArrayList<>();

            for(Object o : parts){
                if(o instanceof HashMap) {
                    HashMap<String, Object> map = (HashMap<String, Object>) o;

                    Object objBounds = map.get("bounds");
                    if(objBounds instanceof byte[]){
                        byte[] bounds = (byte[])objBounds;
                        boxList.add(getBoxSingleTextureInt(mapDataCtx.getPatchFactory(), bounds[0], bounds[3], bounds[1], bounds[4], bounds[2], bounds[5], 6 + boxList.size(), false));
                    }

                    int color = GWM_Util.objectToInt(map.get("tint"), 0xFFFFFF);
                    color |= 0xFF000000;
                    tintList.add(color);

                    Object objTexture = map.get("texture");
                    if(objTexture instanceof String){
                        String texture = (String)objTexture;
                        int attemptedLookup = GWM_Util.tryGetTextureIdByName(texture);
                        textureList.add(attemptedLookup);
                    }
                }
            }

            int yaw = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("oc:yaw"), 0);
            int pitch = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("oc:pitch"), 0);

            if(boxList.size() > 0){
                RenderPatch[] patches = new RenderPatch[6*boxList.size()];
                int[] tints = new int[tintList.size()];
                int[] actualTextures = new int[textureList.size()];
                for(int i = 0; i < boxList.size(); i++){
                    for(int j = 0; j < 6; j++){
                        RenderPatch[] renderPatches = boxList.get(i);

                        switch (yaw){
                            case 2:
                                renderPatches = getRotatedSet(mapDataCtx.getPatchFactory(), renderPatches, 0, 180, 0);
                                break;
                            case 4:
                                renderPatches = getRotatedSet(mapDataCtx.getPatchFactory(), renderPatches, 0, 90, 0);
                                break;
                            case 5:
                                renderPatches = getRotatedSet(mapDataCtx.getPatchFactory(), renderPatches, 0, 270, 0);
                                break;
                        }

                        patches[i*6+j] = renderPatches[j];
                    }
                    tints[i] = tintList.get(i);
                    actualTextures[i] = textureList.get(i);
                }
                return new OpenComputers3DPrintedBlockRenderData(mapDataCtx, patches,  tints, actualTextures);
            }

        }
        return super.getRenderData(mapDataCtx);
    }

    static String[] nbtFieldsNeeded = {"state", "data", "oc:pitch", "oc:yaw"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class OpenComputers3DPrintedBlockRenderData extends CustomRendererData implements CustomTextureMapper{

        private final int[] tints;

        SimpleColorMultiplier[] multipliers;
        private final int[] actualTextures;
        TexturePack.HDTextureMap map;

        public OpenComputers3DPrintedBlockRenderData(MapDataContext ctx, RenderPatch[] mesh, int[] tints, int[] actualTextures) {
            super(mesh, null, null);
            this.tints = tints;
            multipliers = new SimpleColorMultiplier[tints.length];
            this.actualTextures = actualTextures;
            map = TexturePack.HDTextureMap.getMap(ctx.getBlockTypeID(), ctx.getBlockData(), 0);

            for(int i = 0; i < tints.length; i++) {
                multipliers[i] = new SimpleColorMultiplier(tints[i]);
                if(actualTextures[i] < 0){
                    actualTextures[i] = map.getIndexForFace(6);
                }
                if(actualTextures[i] < TexturePack.COLORMOD_MULT_INTERNAL)
                    actualTextures[i] += TexturePack.COLORMOD_MULT_INTERNAL * TexturePack.COLORMOD_MULTTONED;
            }

        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return this;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(patchId >= 6) {
                return new int[]{actualTextures[patchId - 6]};
            }
            return new int[]{map.getIndexForFace(patchId)};
        }

        @Override
        public CustomColorMultiplier getCustomColorMultiplier(int patchId) {
            if(patchId >= 6)
                return multipliers[patchId - 6];

            return super.getCustomColorMultiplier(patchId);
        }

        class SimpleColorMultiplier extends CustomColorMultiplier {
            public SimpleColorMultiplier(int c){
                color = c;
            }
            int color;
            @Override
            public int getColorMultiplier(MapDataContext mapDataCtx) {
                return color;
            }
        }
    }
}
