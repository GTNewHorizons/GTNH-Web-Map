package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.modsupport.appliedenergistics2.AE2Support;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MECableRenderer extends PipeRendererBase {
    RenderPatch[][] smallPipes, mediumPipes, largePipes;
    double smallPipeRadius = 2.0 / 16;
    double mediumPipeRadius = 3.0 / 16;
    double largePipeRadius = 5.0 / 16;


    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        smallPipes = generateSingleSize(rpf, smallPipeRadius, smallPipeRadius, 0, 0);
        mediumPipes = generateSingleSize(rpf, mediumPipeRadius-1.0/16, mediumPipeRadius, 0, 0);
        largePipes = generateSingleSize(rpf, largePipeRadius-1.0/16, largePipeRadius, 0, 0);


        mediumPipes[ForgeDirection.UP.flag|ForgeDirection.DOWN.flag] = CustomRenderer.getBoxSingleTexture(rpf, 0.5-mediumPipeRadius, 0.5+mediumPipeRadius, 0, 1, 0.5-mediumPipeRadius, 0.5+mediumPipeRadius, 0, false);
        mediumPipes[ForgeDirection.NORTH.flag|ForgeDirection.SOUTH.flag] = CustomRenderer.getBoxSingleTexture(rpf, 0.5-mediumPipeRadius, 0.5+mediumPipeRadius, 0.5-mediumPipeRadius, 0.5+mediumPipeRadius, 0, 1, 0, true);
        mediumPipes[ForgeDirection.EAST.flag|ForgeDirection.WEST.flag] = CustomRenderer.getBoxSingleTexture(rpf, 0, 1, 0.5-mediumPipeRadius, 0.5+mediumPipeRadius, 0.5-mediumPipeRadius, 0.5+mediumPipeRadius, 0, false);

        largePipes[ForgeDirection.UP.flag|ForgeDirection.DOWN.flag] = CustomRenderer.getBoxSingleTexture(rpf, 0.5-largePipeRadius, 0.5+largePipeRadius, 0, 1, 0.5-largePipeRadius, 0.5+largePipeRadius, 0, false);
        largePipes[ForgeDirection.NORTH.flag|ForgeDirection.SOUTH.flag] = CustomRenderer.getBoxSingleTexture(rpf, 0.5-largePipeRadius, 0.5+largePipeRadius, 0.5-largePipeRadius, 0.5+largePipeRadius, 0, 1, 0, true);
        largePipes[ForgeDirection.EAST.flag|ForgeDirection.WEST.flag] = CustomRenderer.getBoxSingleTexture(rpf, 0, 1, 0.5-largePipeRadius, 0.5+largePipeRadius, 0.5-largePipeRadius, 0.5+largePipeRadius, 0, false);

        return true;
    }


    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int version = 0;
        Object thisDef = mapDataCtx.getBlockTileEntityField("def:6");
        short thisDamage = 0;

        if (thisDef instanceof HashMap) {
            HashMap<String, Object> map = (HashMap<String, Object>) thisDef;
            Object dmgObj = map.get("Damage");
            if (dmgObj instanceof Short)
                thisDamage = ((Short) dmgObj).shortValue();
        }
        thisDef.toString();
        //HashSet<String, O>
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {

            Object def = mapDataCtx.getBlockTileEntityField("def:" + dir.ordinal());
            Object extra = mapDataCtx.getBlockTileEntityField("extra:" + dir.ordinal());


            if (extra == null && def == null) {
                int id = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);

                if (id == mapDataCtx.getBlockTypeID()) {
                    ForgeDirection opposite = dir.getOpposite();
                    Object otherExtra = mapDataCtx.getBlockTileEntityFieldAt("extra:" + opposite.ordinal(), dir.offsetX, dir.offsetY, dir.offsetZ);

                    Object otherDef = mapDataCtx.getBlockTileEntityFieldAt("def:6", dir.offsetX, dir.offsetY, dir.offsetZ);
                    short otherDamage = 0;
                    if (otherDef instanceof HashMap) {
                        HashMap<String, Object> map = (HashMap<String, Object>) otherDef;
                        Object dmgObj = map.get("Damage");
                        if (dmgObj instanceof Short)
                            otherDamage = ((Short) dmgObj).shortValue();
                    }

                    if (otherExtra == null && ((thisDamage % 20) == 16 || (otherDamage % 20) == 16 || (otherDamage % 20) == (thisDamage % 20))) {
                        version |= dir.flag;
                    } else {
                        thisDef.toString();
                    }

                }

            } else {
                double max = 0.85;
                double min = 0.15;

                double pipePositive = 0.5 + smallPipeRadius;
                double pipeNegative = 0.5 - smallPipeRadius;


                switch (dir) {
                    case DOWN:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, min, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, 0, min, min, max, new int[]{1, 2, 3, 4, 5, 6});
                        break;
                    case UP:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, pipePositive, max, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, max, 1, min, max, new int[]{2, 1, 3, 4, 5, 6});
                        break;
                    case NORTH:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, pipeNegative, pipePositive, min, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, min, max, 0, min, new int[]{3, 4, 5, 6, 1, 2});
                        break;
                    case SOUTH:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, pipeNegative, pipePositive, pipePositive, max, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, min, max, max, 1, new int[]{3, 4, 5, 6, 2, 1});
                        break;
                    case WEST:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, pipePositive, pipeNegative, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0, min, min, max, min, max, new int[]{3, 4, 1, 2, 5, 6});
                        break;
                    case EAST:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipePositive, max, pipeNegative, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, max, 1, min, max, min, max, new int[]{3, 4, 2, 1, 5, 6});
                        break;
                    case UNKNOWN:
                        break;
                }

            }
        }
        int textureVersion = 0;

        if(version == (ForgeDirection.UP.flag|ForgeDirection.DOWN.flag)) {
            textureVersion = 2;
        } else if(version == (ForgeDirection.NORTH.flag|ForgeDirection.SOUTH.flag) ) {
            textureVersion = 3;
        } else if(version == (ForgeDirection.EAST.flag|ForgeDirection.WEST.flag)) {
            textureVersion = 3;
        }

        TextureSelector texSel = new TextureSelector(thisDamage, textureVersion);

        if (!list.isEmpty()) {
            for (RenderPatch rp : smallPipes[version]) {
                list.add(rp);
            }
            return new CustomRendererData(list.toArray(new RenderPatch[list.size()]), null, texSel);
        }

        switch (thisDamage / 20) {
            case 0:
                return new CustomRendererData(smallPipes[version], null, texSel);
            case 1:
                return new CustomRendererData(mediumPipes[version], null, texSel);
            case 2:
            case 3:
            case 27:
            case 28:
                return new CustomRendererData(largePipes[version], null, texSel);
        }

        return new CustomRendererData(smallPipes[version], null, texSel);
    }


    static String[] nbtFieldsNeeded = {"def:0", "def:1", "def:2", "def:3", "def:4", "def:5", "def:6",
            "extra:0", "extra:1", "extra:2", "extra:3", "extra:4", "extra:5", "extra:6"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class TextureSelector implements CustomTextureMapper {

        private final short thisDamage;
        int textureId =0;

        public TextureSelector(short thisDamage, int textureId) {

            this.thisDamage = thisDamage;
            this.textureId = textureId;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if (patchId > 0)
                return null;

            if (thisDamage >= 0 && AE2Support.cableTypes[thisDamage] != null) {
                switch (textureId){
                    case 2:
                        return new int[]{AE2Support.cableTypes[thisDamage].textureId2};
                    case 3:
                        return new int[]{AE2Support.cableTypes[thisDamage].textureId3};
                }
                return new int[]{AE2Support.cableTypes[thisDamage].textureId};
            }

            return null;
        }
    }

}
