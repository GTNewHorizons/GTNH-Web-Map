package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.forestry.ForestrySupport;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ForestryLeavesRenderer extends CustomRenderer {
    private RenderPatch[] fullBlock;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        fullBlock = getFullBlock(rpf, 0);
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        String name = "treeOak";

        Object containedTree = mapDataCtx.getBlockTileEntityField("ContainedTree");
        if(containedTree instanceof HashMap){
            HashMap hmContained = (HashMap) containedTree;

            Object genome = hmContained.get("Genome");
            if(genome instanceof HashMap){
                HashMap hmGenome = (HashMap) genome;
                Object chromo = hmGenome.get("Chromosomes");
                if(chromo instanceof ArrayList){
                    ArrayList alChromo = (ArrayList) chromo;
                    if(alChromo.size() > 0){
                        Object slot0 = alChromo.get(0);
                        if(slot0 instanceof HashMap){
                            HashMap hmSlot0 = (HashMap) slot0;
                            Object uid0 = hmSlot0.get("UID0");
                            if(uid0 instanceof String)
                                name = (String)uid0;
                        }
                    }

                }
            }
        }


        ForestrySupport.LeavesEntry leaves = ForestrySupport.getLeavesEntryForTreeName(name);
        return new CustomRendererData(getRenderPatchList(mapDataCtx), leaves, leaves);
    }

    static String[] nbtFieldsNeeded = {"ContainedTree"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
