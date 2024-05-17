package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ForestryWoodEtcRenderer extends CustomRenderer {

    String nbtKey = "WT";
    int max = 29;

    public enum BlockType {
        Wood,
        Plank,
        Slab
    }

    BlockType type = BlockType.Wood;

    RenderPatch[][][] modelByTypeAndMeta;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        if(custparm.get("key") != null)
            nbtKey = custparm.get("key");
        if(custparm.get("max") != null)
            max = Integer.parseInt(custparm.get("max"));

        if(custparm.get("type") != null){
            type = BlockType.valueOf(custparm.get("type"));
        }

        switch (type){

            case Wood:
            {
                modelByTypeAndMeta = new RenderPatch[max][3][];
                for(int i = 0; i < max; i++){
                    int idx = i*2;
                    modelByTypeAndMeta[i][0] = CustomRenderer.getBoxFull(rpf, new int[]{idx+1, idx+1, idx, idx, idx, idx});
                    modelByTypeAndMeta[i][1] = CustomRenderer.getBoxFull(rpf, new int[]{idx, idx, idx+1, idx+1, idx, idx});
                    modelByTypeAndMeta[i][2] = CustomRenderer.getBoxFull(rpf, new int[]{idx, idx, idx, idx, idx+1, idx+1});
                }
            }
            break;
            case Plank:
                modelByTypeAndMeta = new RenderPatch[max][1][];
                for(int i = 0; i < max; i++){
                    modelByTypeAndMeta[i][0] = CustomRenderer.getBoxFull(rpf, new int[]{i, i, i, i, i, i});
                }
                break;
            case Slab:
                modelByTypeAndMeta = new RenderPatch[max][2][];
                for(int i = 0; i < max; i++){
                    modelByTypeAndMeta[i][0] = CustomRenderer.getBoxSingleTextureInt(rpf, 0,16,0,8,0,16,i, false);
                    modelByTypeAndMeta[i][1] = CustomRenderer.getBoxSingleTextureInt(rpf, 0,16,8,16,0,16,i, false);
                }
                break;
        }


        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        int woodType = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField(nbtKey),0);
        if(woodType < 0 || woodType >= max)
            woodType = 0;

        int meta = mapDataCtx.getBlockData();

        switch (type){
            case Wood:
                meta = ((meta & 0x0C) >> 2) % 3;
                break;
            case Plank:
                meta = 0;
                break;
            case Slab:
                meta = ((meta & 8) >> 3) % 2;
                break;
        }

        return modelByTypeAndMeta[woodType][meta];
    }

    String[] nbtFieldsNeeded;

    @Override
    public String[] getTileEntityFieldsNeeded() {
        if (nbtFieldsNeeded == null)
            nbtFieldsNeeded = new String[]{nbtKey};
        return nbtFieldsNeeded;
    }
}
