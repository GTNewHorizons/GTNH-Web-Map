package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class ThaumcraftTableRenderer extends CustomRenderer {

    RenderPatch[] otherTable;
    RenderPatch[] table;
    RenderPatch[] craftingTable;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;


        otherTable = getFullBlock(rpf, 3); // 3 seems to be the top texture of table
        table = makeTable(rpf);
        craftingTable = makeCraftingTable(rpf);

        return true;
    }
    RenderPatch[] makeTable(RenderPatchFactory rpf){
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Top
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};
            CustomRenderer.addBox(rpf, list, 0, 1, 0.5, 1, 0, 1, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.25, 0.75, 0.25, 0.5, 0.25, 0.75, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.2, 0.8, 0.0, 0.25, 0, 1, patchTextureIds);
        }

        return list.toArray(new RenderPatch[list.size()]);
    }

    RenderPatch[] makeCraftingTable(RenderPatchFactory rpf){

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // Top
        {
            int patchTextureIds[] = {0, 1, 2, 2, 2, 2};
            CustomRenderer.addBox(rpf, list, 0, 1, 0.5, 1, 0, 1, patchTextureIds);
        }

        // legs
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};
            CustomRenderer.addBox(rpf, list, 0, 0.2, 0.2, 0.5, 0, 0.2, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.8, 1, 0.2, 0.5, 0, 0.2, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0, 0.2, 0.2, 0.5, 0.8, 1, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.8, 1, 0.2, 0.5, 0.8, 1, patchTextureIds);
        }

        // Bottom
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};
            CustomRenderer.addBox(rpf, list, 0, 1, 0, 0.2, 0, 1, patchTextureIds);
        }

        return list.toArray(new RenderPatch[list.size()]);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int data = mapDataCtx.getBlockData();

        switch (data){
            case 0: // Regular table
            case 2: // Research table
            case 5:
            case 7:

                return table;

            case 15: // Crafting table
                return craftingTable;
        }


        return table;
    }
}
