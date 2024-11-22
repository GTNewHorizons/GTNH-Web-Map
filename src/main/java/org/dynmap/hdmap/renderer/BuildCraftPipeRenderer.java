package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class BuildCraftPipeRenderer extends PipeRendererBase {
    RenderPatch[][] pipes;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        pipes = generateSingleSize(rpf, 4.0/16, 4.0/16, 0, 0);

        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int open = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("inputOpen"), 63);
        int version = 0;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            if((open & dir.ordinal()) != 0)
            {
                int id = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);

                if(id == mapDataCtx.getBlockTypeID())
                    version |= dir.flag;
            }
        }

        return pipes[version];
    }

    static String[] nbtFieldsNeeded = {"inputOpen", "pipeId"};

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        int pipeId = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("pipeId"), -1);

        int[] layers = null;
        if(pipeId > 0) {
            int tex = TexturePack.getTextureIdFromTextureMap("PIPES", pipeId);
            if(tex > 0)
                layers = new int[]{tex};
        }

        return new RenderData(getRenderPatchList(mapDataCtx), layers);
    }

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    static class RenderData extends CustomRendererData implements CustomTextureMapper{

        private final int[] layers;

        public RenderData(RenderPatch[] mesh, int[] layers) {
            super(mesh, null, null);
            this.layers = layers;
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return this;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return layers;
        }
    }
}
