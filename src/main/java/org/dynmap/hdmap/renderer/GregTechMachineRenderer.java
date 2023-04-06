package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class GregTechMachineRenderer extends PipeRendererBase {
    int[] xOff = {0, 0, -1, 1, 0, 0};
    int[] yOff = {-1, 1, 0, 0, 0, 0};
    int[] zOff = {0, 0, 0, 0, -1, 1};
    private static final int patchlist[] = { 1, 4, 2, 5, 0, 3 };
    private RenderPatch[] fullBlock;
    RenderPatch[][] pipesTiny;
    RenderPatch[][] pipesSmall;
    RenderPatch[][] pipesNormal;
    RenderPatch[][] pipesLarge;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;
        pipesTiny = generateSingleSize(rpf, 1.0 / 16, 0, 0, 0);
        pipesSmall = generateSingleSize(rpf, 1.0 / 8, 0, 0, 0);
        pipesNormal = generateSingleSize(rpf, 1.0 / 4, 0, 0, 0);
        pipesLarge = generateSingleSize(rpf, 0.4, 0, 0, 0);

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, patchlist);
        fullBlock = list.toArray(new RenderPatch[patchlist.length]);

        return true;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        Object type = mapDataCtx.getBlockTileEntityField("id");
        int version = 0;
        if (type instanceof String) {
            String strType = (String) type;

            if (strType.equals("BaseMetaPipeEntity")) {
                Object connections = mapDataCtx.getBlockTileEntityField("mConnections");

                if(connections instanceof Byte)
                {
                    version = ((Byte)connections).byteValue();
                }

                return pipesSmall[version];
            }
        }

        return fullBlock;
    }

    static String[] nbtFieldsNeeded = {"id", "mID", "mColor", "mConnections"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
