package org.dynmap.hdmap.renderer;

import net.minecraft.client.renderer.entity.Render;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ForestryStairBlockRenderer extends StairBlockRenderer{

    String nbtKey = "WT";
    int max = 29;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        boolean status = super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
        if (custparm.get("key") != null)
            nbtKey = custparm.get("key");
        if (custparm.get("max") != null)
            max = Integer.parseInt(custparm.get("max"));

        return status;
    }
    String[] nbtFieldsNeeded;


    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        RenderPatch[] model = super.getRenderPatchList(ctx);
        RenderPatch[] ret = new RenderPatch[model.length];

        int woodType = GWM_Util.objectToInt(ctx.getBlockTileEntityField(nbtKey), 0);

        if(woodType < 0 || woodType >= max)
            woodType = 0;

        for(int i = 0; i < model.length; i++){
            ret[i] = ctx.getPatchFactory().getRotatedPatch(model[i],0,0,0, woodType);
        }

        return ret;
    }

    @Override
    public String[] getTileEntityFieldsNeeded() {
        if (nbtFieldsNeeded == null)
            nbtFieldsNeeded = new String[]{nbtKey};
        return nbtFieldsNeeded;
    }
}
