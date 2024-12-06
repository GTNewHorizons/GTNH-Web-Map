package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

public class AE2StuffWirelessRenderer extends CustomRenderer {
    CustomRendererData[] cache = new CustomRendererData[18];
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return new RenderPatch[0];
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        int color = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("Color"),0) + 1;

        if(cache[color] == null){
            cache[color] = new CustomRendererData(getBoxSingleTextureInt(mapDataCtx.getPatchFactory(), 0, 16,0,16,0,16, color, false),null, null);
        }
        return cache[color];
    }

    static String[] nbtFieldsNeeded = {"Color"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
