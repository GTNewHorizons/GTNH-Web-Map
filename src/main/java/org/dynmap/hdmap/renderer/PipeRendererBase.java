package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class PipeRendererBase extends CustomRenderer {


    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        return true;
    }


    protected RenderPatch[][] generateSingleSize(RenderPatchFactory rpf, double radius, double midBoxRadius, int txPipe, int txMidBox)
    {
        RenderPatch[][] meshes = new RenderPatch[64][];
        int[] pipeTextures = new int[]{txPipe, txPipe, txPipe, txPipe, txPipe, txPipe};
        int[] midBoxTextures = new int[]{txMidBox, txMidBox, txMidBox, txMidBox, txMidBox, txMidBox};

        for(int i = 0; i < 64; i++)
        {
            ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
            generateSingleModel(rpf, radius, midBoxRadius, pipeTextures, midBoxTextures, i, list);
            meshes[i] = new RenderPatch[list.size()];
            list.toArray(meshes[i]);
        }

        return meshes;
    }

    private static void generateSingleModel(RenderPatchFactory rpf, double radius, double midBoxRadius, int[] pipeTextures, int[] midBoxTextures, int i, ArrayList<RenderPatch> list) {
        double midOff = midBoxRadius >= radius ? midBoxRadius : radius;
        if(midBoxRadius >= radius || i == 0) {
            CustomRenderer.addBox(rpf, list, 0.5 - midBoxRadius, 0.5 + midBoxRadius, 0.5 - midBoxRadius, 0.5 + midBoxRadius, 0.5 - midBoxRadius, 0.5 + midBoxRadius, midBoxTextures);
        }
        if((i & 1) == 1) {
            CustomRenderer.addBox(rpf, list, 0.5 - radius, 0.5 + radius, 0, 0.5 + midOff, 0.5 - radius, 0.5 + radius, pipeTextures);
        }
        if((i &2) == 2) {
            CustomRenderer.addBox(rpf, list, 0.5 - radius, 0.5 + radius, 0.5 - midOff, 1, 0.5 - radius, 0.5 + radius, pipeTextures);
        }
        if((i &4) == 4) {
            CustomRenderer.addBox(rpf, list, 0.5 - radius, 0.5 + radius, 0.5 - radius, 0.5 + radius, 0, 0.5 + midOff, pipeTextures);
        }
        if((i &8) == 8) {
            CustomRenderer.addBox(rpf, list, 0.5 - radius, 0.5 + radius, 0.5 - radius, 0.5 + radius, 0.5 - midOff, 1, pipeTextures);
        }
        if((i &16) == 16) {
            CustomRenderer.addBox(rpf, list, 0, 0.5 + midOff, 0.5 - radius, 0.5 + radius, 0.5 - radius, 0.5 + radius, pipeTextures);
        }
        if((i &32) == 32) {
            CustomRenderer.addBox(rpf, list, 0.5 - midOff, 1, 0.5 - radius, 0.5 + radius, 0.5 - radius, 0.5 + radius, pipeTextures);
        }
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return new RenderPatch[0];
    }
}
