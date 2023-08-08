package org.dynmap.hdmap.renderer;

import gregtech.api.enums.Dyes;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.implementations.*;
import gregtech.common.tileentities.machines.multi.GT_MetaTileEntity_PrimitiveBlastFurnace;
import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.gregtech.GregTechSupport;
import org.dynmap.renderer.*;

import gregtech.api.GregTech_API;

import java.util.ArrayList;
import java.util.Map;

public class GregTechMachineRenderer extends PipeRendererBase {

    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    private RenderPatch[] fullBlock;
    RenderPatch[][] pipes125;
    RenderPatch[][] pipes250;
    RenderPatch[][] pipes375;
    RenderPatch[][] pipes500;
    RenderPatch[][] pipes625;
    RenderPatch[][] pipes750;
    RenderPatch[][] pipes875;

    static int[] dyeColors = new int[16];

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

        for(Dyes d : Dyes.VALUES){
            short[] rgba = d.getRGBA();
            dyeColors[d.ordinal()] = (rgba[0] << 16) | (rgba[1] << 8) | rgba[2];
        }

        return true;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        int version = 0;
        Object typeIdObj = mapDataCtx.getBlockTileEntityField("mID");

        if(typeIdObj instanceof Integer) {
            int typeId = ((Integer) typeIdObj).intValue();
            IMetaTileEntity tmp = GregTech_API.METATILEENTITIES[typeId];
            GregTechSupport.MetaTileEntityEntry entry = GregTechSupport.INSTANCE.getMTEEntre(typeId);
            int mColor = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("mColor"),0);

            if (entry != null) {
                if (tmp instanceof MetaPipeEntity && !(tmp instanceof GT_MetaPipeEntity_Frame)) {
                    Object connections = mapDataCtx.getBlockTileEntityField("mConnections");
                    RenderPatch[][] patchSetToUse = pipes375;

                    BasicMachineTextureAndColor pipeTexturesAndColorsThing = new BasicMachineTextureAndColor(typeId, mapDataCtx, false, -1, -1, mColor);
                    int thickness = entry.thickness;

                    switch (thickness) {
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
                    if (connections instanceof Byte) {
                        version = ((Byte) connections).byteValue();
                    }

                    return new CustomRendererData(patchSetToUse[version], pipeTexturesAndColorsThing, pipeTexturesAndColorsThing);
                } else if (tmp instanceof GT_MetaTileEntity_TieredMachineBlock || tmp instanceof GT_MetaPipeEntity_Frame || tmp instanceof GT_MetaTileEntity_MultiBlockBase || tmp instanceof GT_MetaTileEntity_PrimitiveBlastFurnace) {
                    Object tmpActive = mapDataCtx.getBlockTileEntityField("mActive");
                    Object tmpFacing = mapDataCtx.getBlockTileEntityField("mFacing");
                    Object tmpMainFacing = mapDataCtx.getBlockTileEntityField("mMainFacing");
                    boolean active = tmpActive != null && ((Byte) tmpActive).byteValue() != 0;
                    int facing = tmpFacing instanceof Short ? ((Short) tmpFacing).shortValue() : ForgeDirection.NORTH.ordinal();
                    int mainFacing = tmpMainFacing instanceof Integer ? ((Integer) tmpMainFacing).intValue() : ForgeDirection.EAST.ordinal();

                    BasicMachineTextureAndColor bmtac = new BasicMachineTextureAndColor(typeId, mapDataCtx, active, facing, mainFacing, mColor);
                    return new CustomRendererData(fullBlock, bmtac, bmtac);
                }
            }
        }
        return new CustomRendererData(fullBlock, null, null);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return super.getRenderPatchList(mapDataCtx);
    }
    class BasicMachineTextureAndColor extends CustomColorMultiplier implements CustomTextureMapper
    {
        int[][] texturesToUse = new int[6][1];
        int colorMul = 0xFFFFFF;
        GregTechSupport.IconSet baseSet;
        int baseTex, baseTex2;
        public BasicMachineTextureAndColor(int typeId, MapDataContext mapDataCtx, boolean active, int outputFacing, int mainFacing, int mColor){
            GregTechSupport.MetaTileEntityEntry entry = GregTechSupport.INSTANCE.getMTEEntre(typeId);

            if(entry != null){
                colorMul = entry.colorMul;
                int front = mainFacing;
                baseSet = entry.baseTextureSet;
                GregTechSupport.IconSet iconSet = entry.icons;
                baseTex = entry.baseTexture;
                baseTex2 = entry.baseTexture2;
                if(active){
                    iconSet = entry.activeIcons == null ? entry.icons : entry.activeIcons;
                }

                if(entry.isHatch){
                    tryStealBaseTexFromNeighbor(mapDataCtx, outputFacing);
                }

                if(mColor > 0 && mColor <= 16){
                    colorMul = dyeColors[mColor -1];
                }

                for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                    if (baseSet != null) {
                        if (iconSet != null) {
                            switch (dir) {
                                case DOWN:
                                    if (outputFacing == dir.ordinal() && iconSet.output != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.bottom, iconSet.output};
                                    } else if (iconSet.bottom != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.bottom, iconSet.bottom};
                                    } else {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.bottom};
                                    }
                                    break;
                                case UP:
                                    if (outputFacing == dir.ordinal() && iconSet.output != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.top, iconSet.output};
                                    } else if (iconSet.top != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.top, iconSet.top};
                                    } else {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.top};
                                    }

                                    break;
                                case NORTH:
                                case SOUTH:
                                case WEST:
                                case EAST:
                                    if (outputFacing == dir.ordinal() && iconSet.output != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.side, iconSet.output};
                                    } else if (front == dir.ordinal() && iconSet.front != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.side, iconSet.front};
                                    } else if (iconSet.side != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.side, iconSet.side};
                                    } else {
                                        texturesToUse[dir.ordinal()] = new int[]{baseSet.side};
                                    }
                                    break;
                                case UNKNOWN:
                                    break;
                            }
                        } else {
                            switch (dir) {
                                case DOWN:
                                    texturesToUse[dir.ordinal()] = new int[]{baseSet.bottom};
                                    break;
                                case UP:
                                    texturesToUse[dir.ordinal()] = new int[]{baseSet.top};
                                    break;
                                case NORTH:
                                case SOUTH:
                                case WEST:
                                case EAST:
                                    texturesToUse[dir.ordinal()] = new int[]{baseSet.side};
                                    break;
                                case UNKNOWN:
                                    break;
                            }
                        }
                    } else if(baseTex != -1) {
                        if(iconSet != null) {
                            switch (dir) {
                                case DOWN:
                                    if (outputFacing == dir.ordinal() && iconSet.output != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.output};
                                    } else if (iconSet.bottom != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.bottom};
                                    } else {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex};
                                    }
                                    break;
                                case UP:
                                    if (outputFacing == dir.ordinal() && iconSet.output != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.output};
                                    } else if (iconSet.top != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.top};
                                    } else {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex};
                                    }
                                    break;
                                case NORTH:
                                case SOUTH:
                                case WEST:
                                case EAST:
                                    if (outputFacing == dir.ordinal() && iconSet.output != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.output};
                                    } else if (front == dir.ordinal() && iconSet.front != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.front};
                                    } else if (iconSet.side != -1) {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex, iconSet.side};
                                    } else {
                                        texturesToUse[dir.ordinal()] = new int[]{baseTex};
                                    }
                                    break;
                                case UNKNOWN:
                                    break;
                            }
                        } else {
                            if(baseTex2 != -1)
                                texturesToUse[dir.ordinal()] = new int[]{baseTex, baseTex2};
                            else
                                texturesToUse[dir.ordinal()] = new int[]{baseTex};
                        }
                    }
                }
            } else {
                for (int i = 0; i < 6; i++)
                    texturesToUse[i] = new int[]{ -1 };
            }
        }

        private void tryStealBaseTexFromNeighbor(MapDataContext mapDataCtx, int outputFacing) {
            ForgeDirection output = ForgeDirection.getOrientation(outputFacing);
            for (ForgeDirection searchDir : ForgeDirection.VALID_DIRECTIONS){
                if(searchDir == output)
                    continue;

                int blockAt = mapDataCtx.getBlockTypeIDAt(searchDir.offsetX, searchDir.offsetY, searchDir.offsetZ);
                if(blockAt <= 1 || blockAt >= 65536)
                    continue;

                if(GregTechSupport.INSTANCE.validHatchBaseBlocks[blockAt] != null){
                    int blockDataAt = mapDataCtx.getBlockDataAt(searchDir.offsetX, searchDir.offsetY, searchDir.offsetZ);
                    int tex = GregTechSupport.INSTANCE.validHatchBaseBlocks[blockAt][blockDataAt];
                    if(tex > 64) {
                        baseSet = null;
                        baseTex = tex;
                        break;
                    }
                }
                else if(blockAt == mapDataCtx.getBlockTypeID()){ // Another hatch or GT block
                    Object objId = mapDataCtx.getBlockTileEntityFieldAt("mID",searchDir.offsetX, searchDir.offsetY, searchDir.offsetZ);
                    if(objId instanceof Integer){
                        int otherId = ((Integer)objId).intValue();
                        if(otherId >= 0 && otherId < 65536){
                            GregTechSupport.MetaTileEntityEntry otherMtee = GregTechSupport.INSTANCE.getMTEEntre(otherId);
                            if(otherMtee !=null && otherMtee.baseTexture != -1 && otherMtee.type == GregTechSupport.MteType.Controller){
                                baseSet = null;
                                baseTex = otherMtee.baseTexture;
                                break;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public int getColorMultiplier(MapDataContext mapDataCtx) {
            return colorMul;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return texturesToUse[patchId];
        }
    }


    static String[] nbtFieldsNeeded = {"id", "mID", "mColor", "mConnections", "mFacing", "mActive", "mMainFacing"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
