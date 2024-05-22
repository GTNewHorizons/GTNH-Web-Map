package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CarpentersBlocksRenderer extends FenceGateBase {
    protected RenderPatch[] fullBlock;
    // Patch index ordering, corresponding to BlockStep ordinal order
    private static final int patchlist[] = { 0, 1, 4, 5, 2, 3 };

    RenderPatch[][] shapes = new RenderPatch[256][];
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchlist);
        fullBlock = list.toArray(new RenderPatch[patchlist.length]);

        String type = custparm.get("type");
        if(type == null)
            type = "";

        if(type.equals("slope")){
            initSlopeShapes(rpf);
        } else if(type.equals("stairs")){
            initStairsShapes(rpf);
        } else if(type.equals("block")){
            initBlockShapes(rpf);
        } else if(type.equals("plate")){
            initPlateShapes(rpf);
        } else if(type.equals("hatch")){
            initHatchShapes(rpf);
        } else if(type.equals("garage")){
            initGarageShapes(rpf);
        } else if(type.equals("door")){
            initDoorShapes(rpf);
        } else if(type.equals("gate")){
            initGateShapes(rpf);
            link_ids.set(blkid);
        }

        return true;
    }

    private void initGateShapes(RenderPatchFactory rpf) {
        shapes[0] = combineMultiple(
                CustomRenderer.getBoxSingleTextureInt(rpf, 0,2,5, 16, 7,9, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 14,16,5, 16, 7,9, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 2,14,12, 15, 7,9, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 2,14,6, 9, 7,9, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 6,10,9, 12, 7,9, 0, false)
                );
        shapes[32] = getRotatedSet(rpf, shapes[0],0,90, 0);

        shapes[64] = combineMultiple(
                CustomRenderer.getBoxSingleTextureInt(rpf, 0,2,5, 16, 7,9, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 14,16,5, 16, 7,9, 0, false),

                CustomRenderer.getBoxSingleTextureInt(rpf, 0,2,12, 15, 9,15, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 0,2,6, 9, 9,15, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 0,2,9, 12, 13,15, 0, false),

                CustomRenderer.getBoxSingleTextureInt(rpf, 14,16,12, 15, 9,15, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 14,16,6, 9, 9,15, 0, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 14,16,9, 12, 13,15, 0, false)
        );

        shapes[80] = getRotatedSet(rpf, shapes[64],0,180,0);
        shapes[96] = getRotatedSet(rpf, shapes[64],0,270,0);
        shapes[112] = getRotatedSet(rpf, shapes[64],0,90,0);

        shapes[6] = getBoxSingleTextureInt(rpf,0,16,0,13,7,9,0,false);
        shapes[38] = getBoxSingleTextureInt(rpf,7,9,0,13,0,16,0,false);

        shapes[70] = combineMultiple(
                getBoxSingleTextureInt(rpf,0,2,0,13,8,16,0,false),
                getBoxSingleTextureInt(rpf,14,16,0,13,8,16,0,false)
        );
        shapes[86] = getRotatedSet(rpf, shapes[70],0,180,0);
        shapes[102] = getRotatedSet(rpf, shapes[70],0,270,0);
        shapes[118] = getRotatedSet(rpf, shapes[70],0,90,0);

        for(int i = 1; i < 6; i++){
            for(int j = 0; j < 128; j += 16)
                if(shapes[j + i] == null)
                    shapes[j + i] = shapes[j];
        }

    }

    private void initBlockShapes(RenderPatchFactory rpf) {
        shapes[0] = fullBlock;
        shapes[1] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 0.5, 0.0, 1.0, 0.0, 1.0,0, false);
        shapes[2] = CustomRenderer.getBoxSingleTexture(rpf, 0.5, 1.0, 0.0, 1.0, 0.0, 1.0,0, false);
        shapes[3] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.0, 0.5, 0.0, 1.0,0, false);
        shapes[4] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.5, 1.0, 0.0, 1.0,0, false);
        shapes[5] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.0, 1.0, 0.0, 0.5,0, false);
        shapes[6] = CustomRenderer.getBoxSingleTexture(rpf, 0.0, 1.0, 0.0, 1.0, 0.5, 1.0,0, false);
    }
    private void initPlateShapes(RenderPatchFactory rpf) {
        shapes[0] = CustomRenderer.getBoxSingleTextureInt(rpf, 1, 15, 15, 16, 1, 15, 0, false);
        shapes[1] = CustomRenderer.getBoxSingleTextureInt(rpf, 1, 15, 0, 1, 1, 15, 0, false);
        shapes[2] = CustomRenderer.getBoxSingleTextureInt(rpf, 1, 15, 1, 15, 15, 16, 0, false);
        shapes[3] = CustomRenderer.getBoxSingleTextureInt(rpf, 1, 15, 1, 15, 0, 1, 0, false);
        shapes[4] = CustomRenderer.getBoxSingleTextureInt(rpf, 15, 16, 1, 15, 1, 15, 0, false);
        shapes[5] = CustomRenderer.getBoxSingleTextureInt(rpf, 0, 1, 1, 15, 1, 15, 0, false);

        for (int i = 0; i < 6; i++) {
            shapes[32 + i] = shapes[64 + i] = shapes[96 + i] = shapes[i];
        }
    }
    private void initHatchShapes(RenderPatchFactory rpf) {
        shapes[0] = getBoxSingleTextureInt(rpf, 0,16,0,2,0,16,0,false);
        shapes[32] = getRotatedSet(rpf, shapes[0], 0, 180, 0);
        shapes[64] = getRotatedSet(rpf, shapes[0], 0, 270, 0);
        shapes[96] = getRotatedSet(rpf, shapes[0], 0, 90, 0);

        shapes[16] = getBoxSingleTextureInt(rpf, 0,16,0,16,14,16,0,false);
        shapes[48] = getRotatedSet(rpf, shapes[16], 0, 180, 0);
        shapes[80] = getRotatedSet(rpf, shapes[16], 0, 270, 0);
        shapes[112] = getRotatedSet(rpf, shapes[16], 0, 90, 0);

        shapes[8] = getBoxSingleTextureInt(rpf, 0,16,14,16,0,16,0,false);
        shapes[40] = getRotatedSet(rpf, shapes[8], 0, 180, 0);
        shapes[72] = getRotatedSet(rpf, shapes[8], 0, 270, 0);
        shapes[104] = getRotatedSet(rpf, shapes[8], 0, 90, 0);

        shapes[24] = getBoxSingleTextureInt(rpf, 0,16,0,16,14,16,0,false);
        shapes[56] = getRotatedSet(rpf, shapes[24], 0, 180, 0);
        shapes[88] = getRotatedSet(rpf, shapes[24], 0, 270, 0);
        shapes[120] = getRotatedSet(rpf, shapes[24], 0, 90, 0);

        for(int i = 1; i < 128; i++){
            if(shapes[i] == null && shapes[i-1] != null){
                shapes[i] = shapes[i-1];
            }
        }
    }
    private void initGarageShapes(RenderPatchFactory rpf) {
        shapes[0] = fullBlock;
        shapes[32] = getBoxSingleTextureInt(rpf, 0,16,0,16,12,14,0,false);
        shapes[48] = getRotatedSet(rpf, shapes[32], 0, 180, 0);
        shapes[64] = getRotatedSet(rpf, shapes[32], 0, 270, 0);
        shapes[80] = getRotatedSet(rpf, shapes[32], 0, 90, 0);

        shapes[160] = getBoxSingleTextureInt(rpf, 0,16,8,16,14,16,0,false);
        shapes[176] = getRotatedSet(rpf, shapes[160], 0, 180, 0);
        shapes[192] = getRotatedSet(rpf, shapes[160], 0, 270, 0);
        shapes[208] = getRotatedSet(rpf, shapes[160], 0, 90, 0);

        for(int i = 1; i < 128; i++){
            if(shapes[i] == null && shapes[i-1] != null){
                shapes[i] = shapes[i-1];
            }
        }
    }

    private void initDoorShapes(RenderPatchFactory rpf) {
        shapes[0] = getBoxSingleTextureInt(rpf, 0,3,0,16,0,16,0,false);
        shapes[16] = getRotatedSet(rpf, shapes[0], 0, 90, 0);
        shapes[32] = getRotatedSet(rpf, shapes[0], 0, 180, 0);
        shapes[48] = getRotatedSet(rpf, shapes[0], 0, 270, 0);

        shapes[128] = getBoxSingleTextureInt(rpf, 0,3,0,16,0,16,0,false);
        shapes[144] = getRotatedSet(rpf, shapes[128], 0, 90, 0);
        shapes[160] = getRotatedSet(rpf, shapes[128], 0, 180, 0);
        shapes[176] = getRotatedSet(rpf, shapes[128], 0, 270, 0);

        shapes[8] = getBoxSingleTextureInt(rpf, 0,3,0,16,0,16,0,false);
        shapes[24] = getRotatedSet(rpf, shapes[8], 0, 90, 0);
        shapes[40] = getRotatedSet(rpf, shapes[8], 0, 180, 0);
        shapes[56] = getRotatedSet(rpf, shapes[8], 0, 270, 0);

        shapes[136] = getBoxSingleTextureInt(rpf, 0,3,0,16,0,16,0,false);
        shapes[152] = getRotatedSet(rpf, shapes[136], 0, 90, 0);
        shapes[168] = getRotatedSet(rpf, shapes[136], 0, 180, 0);
        shapes[184] = getRotatedSet(rpf, shapes[136], 0, 270, 0);

        for(int i = 255 - 64; i>=0; i--){
            if(shapes[i] != null && shapes[i + 64] == null){
                if((i & 8) != 0){
                    shapes[i + 64] = getRotatedSet(rpf, shapes[i], 0, 270, 0);
                } else {
                    shapes[i + 64] = getRotatedSet(rpf, shapes[i], 0, 90, 0);
                }
            }
        }

        for(int i = 1; i < 256; i++){
            if(shapes[i] == null && shapes[i-1] != null){
                shapes[i] = shapes[i-1];
            }
        }
    }

    private void initStairsShapes(RenderPatchFactory rpf) {
        shapes[8] = ArchitectureCraftShapeRenderer.makeStairs(rpf);
        shapes[9] =  getRotatedSet(rpf, shapes[8], 0, 180, 0);
        shapes[10] = getRotatedSet(rpf, shapes[8], 0, 270, 0);
        shapes[11] = getRotatedSet(rpf, shapes[8], 0, 90, 0);

        shapes[16] = combineMultiple(getBoxSingleTextureInt(rpf,0,16,0,8,0,16,0,false), getBoxSingleTextureInt(rpf,0,8,8,16,0,16,0,false), getBoxSingleTextureInt(rpf,8,16,8,16,0,8,0,false));
        shapes[17] = getRotatedSet(rpf, shapes[16], 0, 180, 0);
        shapes[18] = getRotatedSet(rpf, shapes[16], 0, 270, 0);
        shapes[19] = getRotatedSet(rpf, shapes[16], 0, 90, 0);

        shapes[24] = combineMultiple(getBoxSingleTextureInt(rpf,0,16,0,8,0,16,0,false), getBoxSingleTextureInt(rpf,0,8,8,16,0,8,0,false));
        shapes[25] = getRotatedSet(rpf, shapes[24], 0, 180, 0);
        shapes[26] = getRotatedSet(rpf, shapes[24], 0, 270, 0);
        shapes[27] = getRotatedSet(rpf, shapes[24], 0, 90, 0);
    }

    private void initSlopeShapes(RenderPatchFactory rpf) {
        RenderPatch[] halfHeightRoofPartToSlope = combineMultiple(
                rpf.getTriangleAutoTexCoords(0, 0, 0, 0.5, 0.5, 0, 1, 0, 0, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1, 0, 0, 0.5, 0.5, 0, 0.5, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(0, 0, 0, 0.5, 0.5, 0.5, 0.5, 0.5, 0, 1, RenderPatchFactory.SideVisible.TOP, 0)
        );
        RenderPatch[] halfHeightRoofPartSlopeToStraight = combineMultiple(
                rpf.getTriangleAutoTexCoords(1, 0, 1, 0.5, 0.5, 0.5, 0, 0, 1, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(0, 0, 0.5, 0, 0, 1, 0.5, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1, 0, 1, 1, 0, 0.5, 0.5, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0)
        );
        RenderPatch[] halfHeightRoofPartStraight = combineMultiple(
                rpf.getTriangleAutoTexCoords(0, 0, 0, 0.5, 0.5, 0, 1, 0, 0, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getQuadAutoTexCoords(0, 0, 0, 0, 0, 0.5, 0.5, 0.5, 0,  RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getQuadAutoTexCoords(1, 0, 0.5, 1, 0, 0, 0.5, 0.5, 0.5,  RenderPatchFactory.SideVisible.TOP, 0)
        );
        RenderPatch[] bottom = new RenderPatch[]{rpf.getPatch(0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP, 0)};


        shapes[0] = getRotatedSet(rpf, ArchitectureCraftShapeRenderer.makeRoofPatches(rpf), 0, 180, 90);
        shapes[4] = getRotatedSet(rpf, ArchitectureCraftShapeRenderer.makeRoofPatches(rpf), 0, 0, 180);
        shapes[8] = ArchitectureCraftShapeRenderer.makeRoofPatches(rpf);
        shapes[12] = getRotatedSet(rpf, getRotatedSet(rpf, ArchitectureCraftShapeRenderer.makeRoofInnerCornerPatches(rpf), 0, 0, 180), 0, 180, 0);
        shapes[16] = getRotatedSet(rpf, ArchitectureCraftShapeRenderer.makeRoofInnerCornerPatches(rpf), 0, 90, 0);
        shapes[20] = getRotatedSet(rpf,getRotatedSet(rpf, ArchitectureCraftShapeRenderer.makeRoofOuterCornerPatches(rpf), 0, 0, 180), 0, 180, 0);
        shapes[24] = getRotatedSet(rpf, ArchitectureCraftShapeRenderer.makeRoofOuterCornerPatches(rpf), 0, 90, 0);
        shapes[32] = combineMultiple(
                rpf.getPatch(0,0,1,0,0,0,1,0,1,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getPatch(0,0,0,0,0,1,0,1,0,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getPatch(1,0,0,0,0,0,1,1,0,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),

                rpf.getTriangleAutoTexCoords(0,0,1,1,0,1,0,1,1,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1,0,1,1,0,0,1,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(0,1,0,0,1,1,1,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1,0,1,1,1,0,0,1,1,1, RenderPatchFactory.SideVisible.TOP, 0)
        );

        shapes[40] = combineMultiple(
                rpf.getTriangleAutoTexCoords(0,0,1,0,0,0,1,0,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(0,0,0,0,0,1,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1,0,0,0,0,0,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(0,0,1,1,0,0,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0)
        );

        shapes[28] = getRotatedSet(rpf, getRotatedSet(rpf, shapes[32], 0, 0, 180), 0, 270, 0);
        shapes[36] = getRotatedSet(rpf, getRotatedSet(rpf, shapes[40], 0, 0, 180), 0, 270, 0);

        for(int i =0 ; i < 44; i+=4){
            if(shapes[i] != null){
                shapes[i+1] = getRotatedSet(rpf, shapes[i], 0, 180, 0);
                shapes[i+2] = getRotatedSet(rpf, shapes[i], 0, 270, 0);
                shapes[i+3] = getRotatedSet(rpf, shapes[i], 0, 90, 0);
            }
        }

        shapes[44] = getRotatedSet(rpf, getSpikeTop(rpf), 180, 0, 0);
        shapes[45] = getSpikeTop(rpf);
        shapes[46] = combineMultiple(
                bottom,
                halfHeightRoofPartStraight,
                halfHeightRoofPartSlopeToStraight
        );
        shapes[47] = getRotatedSet(rpf, shapes[46], 0, 180, 0);
        shapes[48] = getRotatedSet(rpf, shapes[46], 0, 270, 0);
        shapes[49] = getRotatedSet(rpf, shapes[46], 0, 90, 0);

        shapes[50] = combineMultiple(
                bottom[0],
                rpf.getTriangleAutoTexCoords(0, 0, 0, 0.5, 0.5, 0, 1, 0, 0, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1, 0, 1, 0.5, 0.5, 1, 0, 0, 1, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getPatch(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0.5, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getPatch(1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 0.5, RenderPatchFactory.SideVisible.TOP, 0)
        );
        shapes[51] = getRotatedSet(rpf, shapes[50], 0, 90, 0);

        shapes[52] = combineMultiple(
                bottom[0],
                rpf.getTriangleAutoTexCoords(0, 0, 1, 1, 0, 1, 0.5, 0.5, 1, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1, 0, 1, 1, 0, 0, 1, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0),

                rpf.getPatch(1, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0.5, 0, 0.5, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getPatch(0, 0, 0, 0, 0, 1, 1, 1, 0, 0.5, 1, 0, 0.5, RenderPatchFactory.SideVisible.TOP, 0),

                rpf.getTriangleAutoTexCoords(1, 0, 1, 0.5, 0.5, 0.5, 0.5, 0.5, 1, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(1, 0, 1, 1, 0.5, 0.5, 0.5, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0),

                rpf.getTriangleAutoTexCoords(0.5, 0, 0, 0, 0, 0, 0.5, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0),
                rpf.getTriangleAutoTexCoords(0, 0, 0, 0, 0, 0.5, 0.5, 0.5, 0.5, 1, RenderPatchFactory.SideVisible.TOP, 0)
        );
        shapes[53] = getRotatedSet(rpf, shapes[52], 0, 180, 0);
        shapes[54] = getRotatedSet(rpf, shapes[52], 0, 270, 0);
        shapes[55] = getRotatedSet(rpf, shapes[52], 0, 90, 0);

        shapes[56] = combineMultiple(bottom, halfHeightRoofPartToSlope, shapes[51]);
        shapes[57] = getRotatedSet(rpf, shapes[56], 0, 180, 0);
        shapes[58] = getRotatedSet(rpf, shapes[56], 0, 270, 0);
        shapes[59] = getRotatedSet(rpf, shapes[56], 0, 90, 0);

        shapes[60] = combineMultiple(bottom, halfHeightRoofPartToSlope, getRotatedSet(rpf, halfHeightRoofPartToSlope,0,90,0), getRotatedSet(rpf, halfHeightRoofPartToSlope,0,180,0), getRotatedSet(rpf, halfHeightRoofPartToSlope,0,270,0));

        shapes[61] = combineMultiple(shapes[8], halfHeightRoofPartToSlope);
        shapes[62] = getRotatedSet(rpf, shapes[61], 0, 180, 0);
        shapes[63] = getRotatedSet(rpf, shapes[61], 0, 270, 0);
        shapes[64] = getRotatedSet(rpf, shapes[61], 0, 90, 0);
    }

    private RenderPatch[] getSpikeTop(RenderPatchFactory rpf) {
        RenderPatch[] ret = new RenderPatch[5];

        ret[0] = rpf.getPatch(0,0,0, 1,0,0,0,0,1,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0);

        ret[1] = rpf.getTriangleAutoTexCoords(0,0,0, 0.5,0.5,0.5,1,0,0, 1, RenderPatchFactory.SideVisible.TOP, 0);
        ret[2] = rpf.getTriangleAutoTexCoords(1,0,0, 0.5,0.5,0.5,1,0,1, 1, RenderPatchFactory.SideVisible.TOP, 0);
        ret[3] = rpf.getTriangleAutoTexCoords(1,0,1, 0.5,0.5,0.5,0,0,1, 1, RenderPatchFactory.SideVisible.TOP, 0);
        ret[4] = rpf.getTriangleAutoTexCoords(0,0,1, 0.5,0.5,0.5,0,0,0, 1, RenderPatchFactory.SideVisible.TOP, 0);

        return ret;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        Object objMetaData = mapDataCtx.getBlockTileEntityField("cbMetadata");
        int metaData = 0;

        if(objMetaData instanceof Integer)
            metaData = ((Integer)objMetaData).intValue();
        else if(objMetaData instanceof Short)
            metaData = ((Short)objMetaData).intValue();
        else
            return fullBlock;

        if(metaData >= 256)
            metaData %= 256;

        if(metaData >= 0 && metaData < shapes.length && shapes[metaData] != null)
            return shapes[metaData];

        return fullBlock;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new TextureSelector(mapDataCtx));
    }

    public static class TextureSelector implements CustomTextureMapper {

        TexturePack.HDTextureMap map;
        public TextureSelector(MapDataContext mapDataCtx) {
            Object objAttrList = mapDataCtx.getBlockTileEntityField("cbAttrList");

            if(objAttrList instanceof ArrayList){
                ArrayList attrList = (ArrayList) objAttrList;

                for (Object attrSet : attrList) {
                    if (attrSet instanceof HashMap) {
                        HashMap<String, Object> attrs = (HashMap<String, Object>) attrSet;

                        Object strId = attrs.get("cbUniqueId");
                        int id;
                        if(strId instanceof String){
                            id = GWM_Util.blockNameToId((String)strId);
                        }
                        else {
                            id = GWM_Util.objectToInt(attrs.get("id"), 0);
                        }

                        int data = GWM_Util.objectToInt(attrs.get("Damage"), 0);

                        map = TexturePack.HDTextureMap.getMap(id, data, 0);

                        if (map != null)
                            break;
                    }
                }
            }
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(map != null)
                return new int[]{map.getIndexForFace(patchId % 6)};
            return new int[0];
        }
    }

    static String[] nbtFieldsNeeded = {"cbMetadata", "cbAttrList"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
