package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.appliedenergistics2.AE2Support;
import org.dynmap.renderer.*;
import scala.Array;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MECableRenderer extends PipeRendererBase {

    public static MECableRenderer INSTANCE = null;
    RenderPatch[][] smallPipes, mediumPipes, largePipes;
    double smallPipeRadius = 2.0 / 16;
    double mediumPipeRadius = 3.0 / 16;
    double largePipeRadius = 5.0 / 16;

    int meCableBusBlockId, multipartBlockId = -1000;

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

        meCableBusBlockId = blkid;

        INSTANCE = this;

        if(MultipartRenderer.INSTANCE != null){
            multipartBlockId = MultipartRenderer.INSTANCE.blockId;;
        }

        return true;
    }


    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        RenderPatchFactory rpf = mapDataCtx.getPatchFactory();
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int version = 0;
        Object thisDef = null;

        HashMap<String, Object> multipartRoot = null;

        if (mapDataCtx.getBlockTypeID() == meCableBusBlockId) {
            thisDef = mapDataCtx.getBlockTileEntityField("def:6");
        } else {
            Object parts = mapDataCtx.getBlockTileEntityField("parts");
            if(parts instanceof ArrayList){
                for(Object part : (ArrayList)parts){
                    if (part instanceof HashMap){
                        HashMap<String, Object> tmp = (HashMap<String, Object>) part;

                        Object objId = tmp.get("id");
                        if(objId != null && "ae2_cablebus".equals((String)objId)){
                            multipartRoot = tmp;
                            thisDef = tmp.get("def:6");
                            break;
                        }
                    }
                }
            }
        }

        short thisDamage = 0;

        if (thisDef instanceof HashMap) {
            HashMap<String, Object> map = (HashMap<String, Object>) thisDef;
            Object dmgObj = map.get("Damage");
            if (dmgObj instanceof Short)
                thisDamage = ((Short) dmgObj).shortValue();
        }

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {

            Object def;
            Object extra;

            if(multipartRoot == null) {
                def = mapDataCtx.getBlockTileEntityField("def:" + dir.ordinal());
                extra = mapDataCtx.getBlockTileEntityField("extra:" + dir.ordinal());
            } else {
                def = multipartRoot.get("def:" + dir.ordinal());
                extra = multipartRoot.get("extra:" + dir.ordinal());
            }

            if (extra == null && def == null) {
                int id = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);
                ForgeDirection opposite = dir.getOpposite();
                Object otherExtra = null;
                Object otherDef = null;

                if (id == meCableBusBlockId) {
                    otherExtra = mapDataCtx.getBlockTileEntityFieldAt("extra:" + opposite.ordinal(), dir.offsetX, dir.offsetY, dir.offsetZ);
                    otherDef = mapDataCtx.getBlockTileEntityFieldAt("def:6", dir.offsetX, dir.offsetY, dir.offsetZ);
                } else if(id == multipartBlockId){
                    Object parts = mapDataCtx.getBlockTileEntityFieldAt("parts", dir.offsetX, dir.offsetY, dir.offsetZ);
                    if(parts instanceof ArrayList){
                        for(Object part : (ArrayList)parts){
                            if (part instanceof HashMap){
                                HashMap<String, Object> tmp = (HashMap<String, Object>) part;

                                Object objId = tmp.get("id");
                                if(objId != null && "ae2_cablebus".equals((String)objId)){
                                    otherDef = tmp.get("def:6");
                                    otherExtra = tmp.get("extra:"+opposite.ordinal());
                                    break;
                                }
                            }
                        }
                    }
                }

                if(id == meCableBusBlockId || id == multipartBlockId) {
                    short otherDamage = 0;
                    if (otherDef instanceof HashMap) {
                        HashMap<String, Object> map = (HashMap<String, Object>) otherDef;
                        Object dmgObj = map.get("Damage");
                        if (dmgObj instanceof Short)
                            otherDamage = ((Short) dmgObj).shortValue();
                    }

                    if (otherExtra == null && ((thisDamage % 20) == 16 || (otherDamage % 20) == 16 || (otherDamage % 20) == (thisDamage % 20))) {
                        version |= dir.flag;
                    }
                }
            } else {
                double max = 0.85;
                double min = 0.15;

                double pipePositive = 0.5 + smallPipeRadius;
                double pipeNegative = 0.5 - smallPipeRadius;


                switch (dir) {
                    case DOWN:
                        CustomRenderer.addBox(rpf, list, pipeNegative, pipePositive, min, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(rpf, list, min, max, 0, min, min, max, new int[]{1, 2, 3, 4, 5, 6});
                        break;
                    case UP:
                        CustomRenderer.addBox(rpf, list, pipeNegative, pipePositive, pipePositive, max, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(rpf, list, min, max, max, 1, min, max, new int[]{2, 1, 3, 4, 5, 6});
                        break;
                    case NORTH:
                        CustomRenderer.addBox(rpf, list, pipeNegative, pipePositive, pipeNegative, pipePositive, min, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(rpf, list, min, max, min, max, 0, min, new int[]{3, 4, 5, 6, 1, 2});
                        break;
                    case SOUTH:
                        CustomRenderer.addBox(rpf, list, pipeNegative, pipePositive, pipeNegative, pipePositive, pipePositive, max, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(rpf, list, min, max, min, max, max, 1, new int[]{3, 4, 5, 6, 2, 1});
                        break;
                    case WEST:
                        CustomRenderer.addBox(rpf, list, min, pipePositive, pipeNegative, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(rpf, list, 0, min, min, max, min, max, new int[]{3, 4, 1, 2, 5, 6});
                        break;
                    case EAST:
                        CustomRenderer.addBox(rpf, list, pipePositive, max, pipeNegative, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(rpf, list, max, 1, min, max, min, max, new int[]{3, 4, 2, 1, 5, 6});
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

        for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS){
            Object facade;
            if(multipartRoot == null) {
                facade = mapDataCtx.getBlockTileEntityField("facade:" + dir.ordinal());
            } else {
                facade = multipartRoot.get("facade:" + dir.ordinal());
            }
            if(facade instanceof HashMap){
                HashMap<String, Object> map = (HashMap<String, Object>) facade;
                Object tag = map.get("tag");
                if(tag instanceof HashMap){
                    HashMap<String, Object> tagMap = (HashMap<String, Object>) tag;
                    Object tagX = tagMap.get("x");

                    if(tagX instanceof int[]) {
                        int[] arr = (int[]) tagX;

                        if(arr.length < 2)
                            continue;

                        TexturePack.HDTextureMap tmpMap = TexturePack.HDTextureMap.getMap(arr[0], arr[1], 0);

                        if(tmpMap == null)
                            continue;

                        int[] textures = new int[6];
                        for(int i = 0; i < 6; i++){
                            textures[i] = 10000 + dir.ordinal();
                        }
                        switch (dir) {
                            case DOWN:
                                CustomRenderer.addBox(rpf, list, 0, 1, 0, 0.1, 0,1, textures);
                                break;
                            case UP:
                                CustomRenderer.addBox(rpf, list, 0, 1, 0.9, 1, 0,1, textures);
                                break;
                            case NORTH:
                                CustomRenderer.addBox(rpf, list, 0, 1, 0,1, 0, 0.1, textures);
                                break;
                            case SOUTH:
                                CustomRenderer.addBox(rpf, list, 0, 1, 0,1, 0.9, 1, textures);
                                break;
                            case WEST:
                                CustomRenderer.addBox(rpf, list, 0, 0.1, 0, 1, 0,1, textures);
                                break;
                            case EAST:
                                CustomRenderer.addBox(rpf, list, 0.9, 1, 0, 1, 0,1, textures);
                                break;
                            case UNKNOWN:
                                break;
                        }
                        texSel.setFacade(dir, tmpMap.getIndexForFace(0));
                    }
                }
            }
        }

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
            "extra:0", "extra:1", "extra:2", "extra:3", "extra:4", "extra:5", "extra:6", "facade:0", "facade:1", "facade:2", "facade:3", "facade:4", "facade:5", "parts"};

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

        int[] facadeTextures;
        public void setFacade(ForgeDirection side, int textureId){
            if (facadeTextures == null) {
                facadeTextures = new int[]{-1, -1, -1, -1, -1, -1};
            }
            facadeTextures[side.ordinal()] = textureId;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(patchId >= 10000 && patchId <= 10005){
                return new int[]{facadeTextures[patchId-10000]};
            }else  if (patchId > 0)
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
