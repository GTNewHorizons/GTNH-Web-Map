package org.dynmap.hdmap.renderer;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Cable;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Fluid;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Item;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_BasicMachine;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import gregtech.api.GregTech_API;

import java.util.ArrayList;
import java.util.Map;

public class GregTechMachineRenderer extends PipeRendererBase {

    private static final int fullBlockPatchList[] = { 1, 4, 2, 5, 0, 3 };
    private RenderPatch[] fullBlock;
    RenderPatch[][] pipes125;
    RenderPatch[][] pipes250;
    RenderPatch[][] pipes375;
    RenderPatch[][] pipes500;
    RenderPatch[][] pipes625;
    RenderPatch[][] pipes750;
    RenderPatch[][] pipes875;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        pipes125 = generateSingleSize(rpf, 0.125 /2, 0, 0, 0);
        pipes250 = generateSingleSize(rpf, 0.250 /2, 0, 0, 0);
        pipes375 = generateSingleSize(rpf, 0.375 /2, 0, 0, 0);
        pipes500 = generateSingleSize(rpf, 0.500 /2, 0, 0, 0);
        pipes625 = generateSingleSize(rpf, 0.625 /2, 0, 0, 0);
        pipes750 = generateSingleSize(rpf, 0.750 /2, 0, 0, 0);
        pipes875 = generateSingleSize(rpf, 0.875 /2, 0, 0, 0);

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, fullBlockPatchList);
        fullBlock = list.toArray(new RenderPatch[fullBlockPatchList.length]);

        return true;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        int version = 0;
        Object typeIdObj = mapDataCtx.getBlockTileEntityField("mID");

        if(typeIdObj instanceof Integer)
        {
            int typeId = ((Integer)typeIdObj).intValue();
            IMetaTileEntity tmp = GregTech_API.METATILEENTITIES[typeId];

            if(tmp instanceof MetaPipeEntity){
                Object connections = mapDataCtx.getBlockTileEntityField("mConnections");
                RenderPatch[][] patchSetToUse = pipes375;
                MetaPipeEntity mpe = (MetaPipeEntity) tmp;

                PipeTexturesAndColors pipeTexturesAndColorsThing = new PipeTexturesAndColors(mapDataCtx, tmp);
                int thickness = (int)(mpe.getThickNess() * 1000);

                switch (thickness)
                {
                    case 125:
                        patchSetToUse = pipes125;
                        break;
                    case 250:
                        patchSetToUse = pipes250;
                        break;
                    case 375:
                        patchSetToUse = pipes375;
                        break;
                    case 500:
                        patchSetToUse = pipes500;
                        break;
                    case 625:
                        patchSetToUse = pipes625;
                        break;
                    case 750:
                        patchSetToUse = pipes750;
                        break;
                    case 875:
                        patchSetToUse = pipes875;
                        break;
                    case 1000:
                        return new CustomRendererData(fullBlock, pipeTexturesAndColorsThing, pipeTexturesAndColorsThing);
                    default:
                        patchSetToUse = pipes250;
                        break;

                }
                if(connections instanceof Byte)
                {
                    version = ((Byte)connections).byteValue();
                }

                return new CustomRendererData(patchSetToUse[version], pipeTexturesAndColorsThing, pipeTexturesAndColorsThing);
            }
            else if(tmp instanceof GT_MetaTileEntity_BasicMachine){
                BasicMachineTextureAndColor bmtac = new BasicMachineTextureAndColor(mapDataCtx, (GT_MetaTileEntity_BasicMachine)tmp);
                return new CustomRendererData(fullBlock, bmtac, bmtac);
            }
        }


        return new CustomRendererData(fullBlock, null, null);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return super.getRenderPatchList(mapDataCtx);
    }

    static String[] nbtFieldsNeeded = {"id", "mID", "mColor", "mConnections"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class PipeTexturesAndColors extends CustomColorMultiplier implements CustomTextureMapper
    {
        IMetaTileEntity entity;

        int color;
        int[] textureLayers = new int[0];

        public PipeTexturesAndColors(MapDataContext mapDataCtx, IMetaTileEntity entity) {
            this.entity = entity;
            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(mapDataCtx.getBlockTypeID(), 0, 0);
            color = 0xFFFFFF;
            if(entity instanceof GT_MetaPipeEntity_Fluid)
            {
                GT_MetaPipeEntity_Fluid mpe =(GT_MetaPipeEntity_Fluid)entity;
                short[] rgba = mpe.mMaterial.getRGBA();
                color = (rgba[0] << 16) + (rgba[1] << 8) + rgba[2];
                textureLayers = new int[] { map.getIndexForFace(21) };
            }
            else if(entity instanceof GT_MetaPipeEntity_Item)
            {
                GT_MetaPipeEntity_Item mpe =(GT_MetaPipeEntity_Item)entity;
                short[] rgba = mpe.mMaterial.getRGBA();
                color = (rgba[0] << 16) + (rgba[1] << 8) + rgba[2];
                textureLayers = new int[] { map.getIndexForFace(21) };
            }
            else if(entity instanceof GT_MetaPipeEntity_Cable)
            {
                GT_MetaPipeEntity_Cable mpe =(GT_MetaPipeEntity_Cable)entity;

                if(mpe.mInsulated) {
                    color = 0xFFFFFF;
                    textureLayers = new int[] { map.getIndexForFace(20) };
                }
                else{
                    textureLayers = new int[] { map.getIndexForFace(21) };
                }


                short[] rgba = mpe.mMaterial.getRGBA();
                color = (rgba[0] << 16) + (rgba[1] << 8) + rgba[2];
            }
        }

        @Override
        public int getColorMultiplier(MapDataContext mapDataCtx) {
            return color;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {

            return textureLayers;
        }
    }

    static int[][] basicMachineCasings;
    class BasicMachineTextureAndColor extends CustomColorMultiplier implements CustomTextureMapper
    {

        int[] texturesToUse = new int[0];
        public BasicMachineTextureAndColor(MapDataContext mapDataCtx, IMetaTileEntity entity){
            if(entity instanceof GT_MetaTileEntity_BasicMachine)
            {
                GT_MetaTileEntity_BasicMachine bm = (GT_MetaTileEntity_BasicMachine)entity;
                int a = bm.mTier;

                if(basicMachineCasings == null){
                    int casingsId = GWM_Util.blockNameToId("gregtech:gt.blockcasings");
                    basicMachineCasings = new int[16][6];
                    for(int i = 0; i < 16; i++){
                        for(int j = 0; j < 6; j++){
                            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(casingsId, i, 0);
                            basicMachineCasings[i][j] = map.getIndexForFace(j);
                        }

                    }
                }

                if(a >= 0 && a < 16)
                    texturesToUse = basicMachineCasings[a];
            }
        }
        @Override
        public int getColorMultiplier(MapDataContext mapDataCtx) {
            return 0xFFFFFF;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return new int[]{ texturesToUse[patchId] };
        }
    }
}
