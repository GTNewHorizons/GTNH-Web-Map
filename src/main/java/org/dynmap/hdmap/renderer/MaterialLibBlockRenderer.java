package org.dynmap.hdmap.renderer;

import org.dynmap.Log;
import org.dynmap.modsupport.materiallib.MaterialLibSupport;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Draws one MaterialLib block shape as a full cube, taking its tile and tint from the material its metadata
/// indexes (see [MaterialLibSupport]). The `mlshape` key of the `customblock:` row names the shape.
///
/// The metadata is read through [MapDataContext#getBlockDataFull] because a material index runs past the four
/// bits the masked block data keeps.
public class MaterialLibBlockRenderer extends CustomRenderer {
    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };

    private final ConcurrentHashMap<Integer, CustomRendererData> renderDataCache = new ConcurrentHashMap<>();

    private RenderPatch[] fullBlock;
    private String shapeName;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, fullBlockPatchList);
        fullBlock = list.toArray(new RenderPatch[fullBlockPatchList.length]);
        shapeName = custparm.get("mlshape");

        return shapeName != null && super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        return renderDataCache.computeIfAbsent(mapDataCtx.getBlockDataFull(), this::buildRenderData);
    }

    /// Render data drawing the material's tile, or the block's own texture map where the renderdata declared no
    /// art for this shape. A lookup failure is cached like any other outcome, so it is reported once per index.
    private CustomRendererData buildRenderData(int materialIndex) {
        MaterialLibSupport.MaterialArt art;

        try {
            art = MaterialLibSupport.INSTANCE.resolve(shapeName, materialIndex);
        } catch (RuntimeException failed) {
            Log.severe("Could not resolve the material at index " + materialIndex + " for MaterialLib shape " + shapeName, failed);
            art = null;
        }

        if(art == null)
            return new CustomRendererData(fullBlock, null, null);

        int[] layers = new int[] { art.tile };

        return new CustomRendererData(fullBlock, art.multiplier, patchId -> layers);
    }
}
