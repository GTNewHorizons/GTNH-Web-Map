package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.forestry.BinnieSupport;
import org.dynmap.renderer.*;

import java.util.Map;

public class BinnieCeramicsRenderer extends CustomRenderer {
    RenderPatch[] fullBlock;

    RenderPatch[][][] rotations = new RenderPatch[6][4][];

    boolean isBrick, isBasic;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        fullBlock = CustomRenderer.getBoxFull(rpf, new int[]{0,1,2,3,4,5});

        rotations[1][0] = combineMultiple(
                rpf.getPatch(0,0,1,1,0,1,0,0,0,0,1,0,1, RenderPatchFactory.SideVisible.BOTTOM, 0),
                rpf.getPatch(0,1,1,1,1,1,0,1,0,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 1),
                getSidePatch(rpf,2,0,2),
                getSidePatch(rpf,3,0,3),
                getSidePatch(rpf,4,0,4),
                getSidePatch(rpf,5,0,5)
        );
        for (int i = 1; i < 4; i++)
            rotations[1][i] = getRotatedSet(rpf, rotations[1][0], 0, 90 * i, 0);

        for (int i = 0; i < 4; i++){
            rotations[0][i] = getRotatedSet(rpf, rotations[1][i], 180, 0, 0);
            rotations[2][i] = getRotatedSet(rpf, rotations[1][i], 90, 180, 0);
            rotations[3][i] = getRotatedSet(rpf, rotations[1][i], 270, 0, 0);
            rotations[4][i] = getRotatedSet(rpf, rotations[1][i], 0, 90, 270);
            rotations[5][i] = getRotatedSet(rpf, rotations[1][i], 0, 270, 90);
        }

        String t = custparm.get("type");

        if (t != null) {
            if (t.equals("brick")) {
                isBrick = true;
            }
            else if(t.equals("basic")){
                isBasic = true;
            }
        }

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        int meta = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("meta"), 0);

        int top = (meta >> 28) & 0xF;
        int rot = (meta >> 26) & 0x3;

        if(top >= 6)
            top = 1;
        if(rot >= 4)
            rot = 0;

        if(isBasic)
            return new MyCustomRenderData(fullBlock, mapDataCtx, isBrick, isBasic, meta);

        return new MyCustomRenderData(rotations[top][rot], mapDataCtx, isBrick, isBasic, meta);
    }
    static String[] nbtFieldsNeeded = {"meta"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    static class MyCustomRenderData extends CustomRendererData implements CustomTextureMapper {
        private final MapDataContext mapDataCtx;
        private final boolean isBasic;
        int colorA;
        int colorB;

        int[][] layers;
        public MyCustomRenderData(RenderPatch[] mesh, MapDataContext mapDataCtx, boolean isBrick, boolean isBasic, int meta) {
            super(mesh, null, null);
            this.mapDataCtx = mapDataCtx;
            this.isBasic = isBasic;


            colorA = meta & 0xFF;
            colorB = (meta >> 8) & 0xFF;
            if(colorA >= BinnieSupport.flowerColorMultipliers.length)
                colorA = 0;
            if(colorB >= BinnieSupport.flowerColorMultipliers.length)
                colorB = 0;

            if(isBrick){
                int design = (meta >> 16) & 0xFF;
                layers = BinnieSupport.ceramicBrickTextures.get(design);
            }
            else {
                int design = (meta >> 16) & 0xFF;
                layers = BinnieSupport.ceramicPatternTextures.get(design);
            }


        }

        @Override
        public CustomColorMultiplier getCustomColorMultiplier(int patchId, int layer) {
            if(layer == 0)
                return BinnieSupport.flowerColorMultipliers[colorA];

            if(layer == 1)
                return BinnieSupport.flowerColorMultipliers[colorB];

            return null;
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            if(isBasic)
                return null;

            return this;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(layers == null)
                return null;
            if(patchId < layers.length)
                return layers[patchId];
            return layers[0];
        }
    }
}
