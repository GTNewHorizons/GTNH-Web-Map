package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class ThaumcraftStoneDeviceRenderer extends CustomRenderer {

    RenderPatch[] fullBlock, pedestal, matrix;
    RenderPatch[][] infusionBottom = new RenderPatch[4][], infusionTop = new RenderPatch[4][];

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;


        fullBlock = getFullBlock(rpf, -1);
        pedestal = getPedestalModel(rpf);
        matrix = getMatrixModel(rpf);

        for(int i = 0; i < 4; i++){
            infusionBottom[i] = getInfusionBottomModel(rpf, i);
            infusionTop[i] = getInfusionTopModel(rpf, i);
        }
        return true;
    }

    private RenderPatch[] getMatrixModel(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};

            for(int x =0; x < 2; x++){
                for(int y =0; y < 2; y++)
                    for(int z = 0; z < 2; z++)
                        addBox(rpf, list, 0.55*x, 0.55*x+0.45, 0.55*y, 0.55*y+0.45, 0.55*z, 0.55*z+0.45, patchTextureIds);
            }

        }

        return list.toArray(new RenderPatch[list.size()]);
    }

    private RenderPatch[] getInfusionTopModel(RenderPatchFactory rpf, int orientation) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};

            CustomRenderer.addBox(rpf, list, 0.7, 0.97, 0.75, 0.95, 0.7, 0.97, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.5, 0.92, 0.5, 0.75, 0.5, 0.92, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.35, 0.88, 0.25, 0.5, 0.35, 0.88, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.25, 0.85, 0.0, 0.25, 0.25, 0.85, patchTextureIds);
        }

        return getRotatedPatches(rpf, orientation, list);
    }


    private RenderPatch[] getInfusionBottomModel(RenderPatchFactory rpf, int orientation) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        {
            int patchTextureIds[] = {0, 0, 0, 0, 0, 0};

            CustomRenderer.addBox(rpf, list, 0.15, 0.85, 0.25, 1, 0.15, 0.85, patchTextureIds);

            CustomRenderer.addBox(rpf, list, 0, 1, 0.0, 0.25, 0, 1, patchTextureIds);
        }

        return getRotatedPatches(rpf, orientation, list);
    }

    private static RenderPatch[] getRotatedPatches(RenderPatchFactory rpf, int orientation, ArrayList<RenderPatch> list) {
        RenderPatch[] renderPatches = list.toArray(new RenderPatch[list.size()]);
        if(orientation != 0){
            for(int i = 0; i < renderPatches.length; i++){
                renderPatches[i] = rpf.getRotatedPatch(renderPatches[i], 0, orientation*90, 0, renderPatches[i].getTextureIndex());
            }
        }
        return renderPatches;
    }
    private RenderPatch[] getPedestalModel(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        {
            int patchTextureIds[] = {0, 0, 1, 1, 1, 1};
            CustomRenderer.addBox(rpf, list, 0.125, 0.875, 0.75, 1, 0.125, 0.875, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0.25, 0.75, 0.25, 0.75, 0.25, 0.75, patchTextureIds);
            CustomRenderer.addBox(rpf, list, 0, 1, 0.0, 0.25, 0, 1, patchTextureIds);
        }

        return list.toArray(new RenderPatch[list.size()]);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int data = mapDataCtx.getBlockData();

        switch (data) {
            case 0:  // Alchemical furnace
                return fullBlock;

            case 1:  // Pedestal
                return pedestal;

            case 2:  // Matrix
                return matrix;

            case 3:  // Inf pillar bottom?
            case 4:  // Inf pillar top?
            {
                Object objOrientation = mapDataCtx.getBlockTileEntityField("orientation");
                int orientation = 0;
                if(objOrientation instanceof Byte) {
                    orientation = ((Byte) objOrientation).byteValue() % 4;

                    switch (orientation){
                        case 0:
                            orientation = 1;
                            break;
                        case 1:
                            orientation = 2;
                            break;
                        case 2:
                            orientation = 0;
                            break;
                        case 3:
                            orientation = 3;
                            break;
                    }
                }else{
                    if(mapDataCtx.getBlockDataAt(1,1,1) == 2)
                        orientation = 0;
                    else if(mapDataCtx.getBlockDataAt(-1,1,1) == 2)
                        orientation = 1;
                    else if(mapDataCtx.getBlockDataAt(-1,1,-1) == 2)
                        orientation = 2;
                    else if(mapDataCtx.getBlockDataAt(1,1,-1) == 2)
                        orientation = 3;

                }

                if(data == 3)
                    return infusionBottom[orientation];

                return infusionTop[orientation];
            }
            case 5:  // Wand charge ped
            case 6:  //
                break;
            case 7:  //
            case 8:  // Compound Charge Focus
            case 9:  // Node stabilizer
            case 10: // Adv Node Stabilizer
            case 11: // Node Transducer
            case 12: // Spa
            case 13: // Focal man
            case 14: // Flux scrubber
            case 15: //
                break;
        }

        return fullBlock;
    }

    static String[] nbtFieldsNeeded = {"orientation"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}