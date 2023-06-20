package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.Map;

public class EnderStorageChestRenderer  extends CustomRenderer {
    private RenderPatch[] fullBlock;
    private static final int patchlist[] = { 0, 1, 4, 5, 2, 3 };
    private static final int patchlist2[] = { 10,10,10,10,10,10 };
    private static final int patchlist3[] = {  11,11,11,11,11,11 };
    private static final int patchlist4[] = {  12,12,12,12,12,12 };

    int[][] textures;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0.0625, 0.9375, 0, 0.875, 0.0625, 0.9375, patchlist);

        CustomRenderer.addBox(rpf, list, 4.0/16, 6.0/16, 0.875, 0.9, 6.0/16, 10.0/16, patchlist2);
        CustomRenderer.addBox(rpf, list, 7.0/16, 9.0/16, 0.875, 0.9, 6.0/16, 10.0/16, patchlist3);
        CustomRenderer.addBox(rpf, list, 11.0/16, 13.0/16, 0.875, 0.9, 6.0/16, 10.0/16, patchlist4);

        fullBlock = list.toArray(new RenderPatch[list.size()]);
        return true;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        if(textures == null){
            textures = new int[16][1];
            int woolId = GWM_Util.blockNameToId("minecraft:wool");
            for(int i = 0 ; i < 16; i++)
            {
                TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(woolId, i, 0);
                textures[i] = new int[]{map.getIndexForFace(0)};
            }
        }

        int freq = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("freq"), 0);
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new TextureMapper(freq, textures));
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int rot = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("rot"), 0) * 90;


        if(rot != 0)
            return getRotatedSet(mapDataCtx.getPatchFactory(), fullBlock, 0, rot, 0);

        return fullBlock;
    }

    static String[] nbtFieldsNeeded = {"rot", "freq"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }


    class TextureMapper implements org.dynmap.renderer.CustomTextureMapper{

        private final int freq;
        private final int[][] textures;

        public TextureMapper(int freq, int[][] textures) {

            this.freq = freq;
            this.textures = textures;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            switch (patchId){
                case 10:
                    return textures[(freq & 0xF00) >> 8];
                case 11:
                    return textures[(freq & 0xF0) >> 4];
                case 12:
                    return textures[(freq & 0xF)];
            }
            return null;
        }
    }


}
