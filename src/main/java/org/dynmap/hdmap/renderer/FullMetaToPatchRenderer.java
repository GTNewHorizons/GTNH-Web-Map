package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;

public class FullMetaToPatchRenderer extends CustomRenderer {
    static CustomRendererData[] cache = new CustomRendererData[65536];
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return new RenderPatch[0];
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        int fullMeta = mapDataCtx.getBlockDataFull();
        if(cache[fullMeta] == null){
            cache[fullMeta] = new CustomRendererData(getBoxSingleTextureInt(mapDataCtx.getPatchFactory(), 0, 16,0,16,0,16, fullMeta, false),null, null);
        }
        return cache[fullMeta];
    }
}
