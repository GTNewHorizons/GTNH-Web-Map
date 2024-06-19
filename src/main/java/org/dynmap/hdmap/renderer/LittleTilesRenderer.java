package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.SimpleColorMultiplier;
import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

import java.util.ArrayList;
import java.util.HashMap;

public class LittleTilesRenderer extends CustomRenderer {
    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    static final int MaxTiles = 4096;

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return new RenderPatch[0];
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        int tilesCount = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("tilesCount"), 0);
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int[][] textures = new int[6*tilesCount][1];
        CustomColorMultiplier[] multipliers = new CustomColorMultiplier[tilesCount];
        for(int i = 0; i < tilesCount; i++){

            HashMap<String, Object> subTile = (HashMap)mapDataCtx.getBlockTileEntityField("t" + i);

            String blockId = (String)subTile.get("block");
            int meta = GWM_Util.objectToInt(subTile.get("meta"),0);
            int blkid = GWM_Util.blockNameToId(blockId);

            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blkid, meta, 0);

            int col = map.getColorMult();
            int colFromBlock = GWM_Util.objectToInt(subTile.get("color"), -1);

            if((col & 0xFFFFFF) != 0xFFFFFF){
                if((colFromBlock & 0xFFFFFF) != 0xFFFFFF){
                    int ra = (col & 0xFF0000) >> 16;
                    int rb = (colFromBlock & 0xFF0000) >> 16;
                    int ga = (col & 0xFF00) >> 8;
                    int gb = (colFromBlock & 0xFF00) >> 8;
                    int ba = (col & 0xFF);
                    int bb = (colFromBlock & 0xFF);

                    int combR = (ra*rb) >> 8;
                    int combG = (ga*gb) >> 8;
                    int combB = (ba*bb) >> 8;

                    multipliers[i] = new SimpleColorMultiplier((combR << 16) | (combG << 8) | combB);
                }
                else {
                    multipliers[i] = new SimpleColorMultiplier(col);
                }
            }
            else if((colFromBlock & 0xFFFFFF) != 0xFFFFFF){
                multipliers[i] = new SimpleColorMultiplier(colFromBlock);
            }

            int[] subTilePatchIds = new int[6];
            for(int j = 0; j < 6; j++){
                subTilePatchIds[j] = fullBlockPatchList[j] + 6*i;
                textures[subTilePatchIds[j]][0] = map.getIndexForFace(fullBlockPatchList[j]);
            }

            int numSub = GWM_Util.objectToInt(subTile.get("bSize"),0);
            for(int b = 0; b < numSub; b++){
                int minX = GWM_Util.objectToInt(subTile.get("bBox"+b+"minX"),0);
                int maxX = GWM_Util.objectToInt(subTile.get("bBox"+b+"maxX"),0);
                int minY = GWM_Util.objectToInt(subTile.get("bBox"+b+"minY"),0);
                int maxY = GWM_Util.objectToInt(subTile.get("bBox"+b+"maxY"),0);
                int minZ = GWM_Util.objectToInt(subTile.get("bBox"+b+"minZ"),0);
                int maxZ = GWM_Util.objectToInt(subTile.get("bBox"+b+"maxZ"),0);

                CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, minX / 16.0, maxX/ 16.0, minY/ 16.0, maxY/ 16.0,minZ/ 16.0, maxZ/ 16.0, subTilePatchIds);
            }

        }
        RenderPatch[] model = list.toArray(new RenderPatch[list.size()]);

        return new CustomData(model, textures, multipliers);
    }

    static String[] nbtFieldsNeeded = null;

    @Override
    public String[] getTileEntityFieldsNeeded() {
        if(nbtFieldsNeeded == null){
            nbtFieldsNeeded = new String[MaxTiles + 1];
            nbtFieldsNeeded[0] = "tilesCount";
            for(int i = 0; i < MaxTiles; i++)
                nbtFieldsNeeded[i+1] = "t" + i;
        }
        return nbtFieldsNeeded;
    }

    static class CustomData extends CustomRendererData implements CustomTextureMapper     {

        private final int[][] textures;
        private final CustomColorMultiplier[] multipliers;

        public CustomData(RenderPatch[] mesh, int[][] textures, CustomColorMultiplier[] multipliers) {
            super(mesh, null, null);
            this.textures = textures;
            this.multipliers = multipliers;
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return this;
        }

        @Override
        public CustomColorMultiplier getCustomColorMultiplier(int patchId, int layer) {
            return multipliers[patchId / 6];
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return textures[patchId];
        }
    }
}
