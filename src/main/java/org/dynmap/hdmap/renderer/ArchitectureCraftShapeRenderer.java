package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.Map;

public class ArchitectureCraftShapeRenderer extends CustomRenderer {
    RenderPatch[] basicBox;
    RenderPatch[][][][] renderPatchesPerShape;
    private static final int patchlist[] = { 1, 4, 2, 5, 0, 3 };
    private static final int patchlistZero[] = {0, 0, 0, 0, 0, 0};
    private static final int textureIdForOptionalSecondary = 12;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchlist);
        basicBox = list.toArray(new RenderPatch[patchlist.length]);

        renderPatchesPerShape = new RenderPatch[256][6][4][];

        for(int i = 0; i < 256; i++){
            renderPatchesPerShape[i][0][0] = getRenderPatchForShape(rpf, i);
        }

        return true;
    }

    RenderPatch[] getRenderPatchForShape(RenderPatchFactory rpf, int id){

        switch (id) {
            case 0: //RoofTile:
                return makeRoofPatches(rpf);
            case 1: //RoofOuterCorner:
                return makeRoofOuterCornerPatches(rpf);
            case 2: //RoofInnerCorner:
                return makeRoofInnerCornerPatches(rpf);
            case 3: //RoofRidge:
                break;
            case 4: //RoofSmartRidge:
                break;
            case 5: //RoofValley:
                break;
            case 6: //RoofSmartValley:
                break;
            case 7: //RoofOverhang:
                return makePatchesFromModel(rpf, "roof_overhang");
            case 8: //RoofOverhangOuterCorner:
                return makePatchesFromModel(rpf, "roof_overhang_outer_corner");
            case 9: //RoofOverhangInnerCorner:
                return makePatchesFromModel(rpf, "roof_overhang_inner_corner");
            case 10: //Cylinder:
                return makePatchesFromModel(rpf, "cylinder_full_r8h16");
            case 11: //CylinderHalf:
                return makePatchesFromModel(rpf, "cylinder_half_r8h16");
            case 12: //CylinderQuarter:
                return makePatchesFromModel(rpf, "cylinder_quarter_r8h16");
            case 13: //CylinderLargeQuarter:
                return makePatchesFromModel(rpf, "cylinder_quarter_r16h16");
            case 14: //AnticylinderLargeQuarter:
                return makePatchesFromModel(rpf, "round_inner_corner");
            case 15: //Pillar:
                return makePatchesFromModel(rpf, "cylinder_r6h16");
            case 16: //Post:
                return makePatchesFromModel(rpf, "cylinder_r4h16");
            case 17: //Pole:
                return makePatchesFromModel(rpf, "cylinder_r2h16");
            case 18: //BevelledOuterCorner:
                return makePatchesFromModel(rpf, "bevelled_outer_corner");
            case 19: //BevelledInnerCorner:
                return makePatchesFromModel(rpf, "bevelled_inner_corner");
            case 20: //PillarBase:
                return makePatchesFromModel(rpf, "pillar_base");
            case 21: //DoricCapital:
                return makePatchesFromModel(rpf, "doric_capital");
            case 22: //IonicCapital:
                return makePatchesFromModel(rpf, "ionic_capital");
            case 23: //CorinthianCapital:
                return makePatchesFromModel(rpf, "corinthian_capital");
            case 24: //DoricTriglyph:
                return makePatchesFromModel(rpf, "doric_triglyph");
            case 25: //DoricTriglyphCorner:
                return makePatchesFromModel(rpf, "doric_triglyph_corner");
            case 26: //DoricMetope:
                return makePatchesFromModel(rpf, "doric_metope");
            case 27: //Architrave:
                return makePatchesFromModel(rpf, "architrave");
            case 28: //ArchitraveCorner:
                return makePatchesFromModel(rpf, "architrave_corner");
            case 30: //WindowFrame:
                return getBoxSingleTextureInt(rpf,0,16,0,16,7,9,6, false);
            case 31: //WindowCorner:
                return combineMultiple(getBoxSingleTextureInt(rpf,6,10,0,16,6,10,0, false),getBoxSingleTextureInt(rpf,0,9,0,16,7,9,6, false), getBoxSingleTextureInt(rpf,7,9,0,16,9,16,6, false) );
            case 32: //WindowMullion:
                return combineMultiple(getBoxSingleTextureInt(rpf,6,10,0,16,6,10,0, false),getBoxSingleTextureInt(rpf,0,16,0,16,7,9,6, false));
            case 33: //SphereFull:
                return makePatchesFromModel(rpf, "sphere_full_r8");
            case 34: //SphereHalf:
                return makePatchesFromModel(rpf, "sphere_half_r8");
            case 35: //SphereQuarter:
                return makePatchesFromModel(rpf, "sphere_quarter_r8");
            case 36: //SphereEighth:
                return makePatchesFromModel(rpf, "sphere_eighth_r8");
            case 37: //SphereEighthLarge:
                return makePatchesFromModel(rpf, "sphere_eighth_r16");
            case 38: //SphereEighthLargeRev:
                return makePatchesFromModel(rpf, "sphere_eighth_r16_rev");
            case 40: //RoofOverhangGableLH:
                return makePatchesFromModel(rpf, "roof_overhang_gable_lh");
            case 41: //RoofOverhangGableRH:
                return makePatchesFromModel(rpf, "roof_overhang_gable_rh");
            case 42: //RoofOverhangGableEndLH:
                return makePatchesFromModel(rpf, "roof_overhang_gable_end_lh");
            case 43: //RoofOverhangGableEndRH:
                return makePatchesFromModel(rpf, "roof_overhang_gable_end_rh");
            case 44: //RoofOverhangRidge:
                return makePatchesFromModel(rpf, "roof_overhang_gable_ridge");
            case 45: //RoofOverhangValley:
                return makePatchesFromModel(rpf, "roof_overhang_gable_valley");
            case 50: //CorniceLH:
                return makePatchesFromModel(rpf, "cornice_lh");
            case 51: //CorniceRH:
                return makePatchesFromModel(rpf, "cornice_rh");
            case 52: //CorniceEndLH:
                return makePatchesFromModel(rpf, "cornice_end_lh");
            case 53: //CorniceEndRH:
                return makePatchesFromModel(rpf, "cornice_end_rh");
            case 54: //CorniceRidge:
                return makePatchesFromModel(rpf, "cornice_ridge");
            case 55: //CorniceValley:
                return makePatchesFromModel(rpf, "cornice_valley");
            case 56: //CorniceBottom:
                return makePatchesFromModel(rpf, "cornice_bottom");
            case 60: //CladdingSheet:
                break;
            case 61: //ArchD1:
                return makePatchesFromModel(rpf, "arch_d1");
            case 62: //ArchD2:
                return makePatchesFromModel(rpf, "arch_d2");
            case 63: //ArchD3A:
                return makePatchesFromModel(rpf, "arch_d3a");
            case 64: //ArchD3B:
                return makePatchesFromModel(rpf, "arch_d3b");
            case 65: //ArchD3C:
                return makePatchesFromModel(rpf, "arch_d3c");
            case 66: //ArchD4A:
                return makePatchesFromModel(rpf, "arch_d4a");
            case 67: //ArchD4B:
                return makePatchesFromModel(rpf, "arch_d4b");
            case 68: //ArchD4C:
                return makePatchesFromModel(rpf, "arch_d4c");
            case 70: //BanisterPlainBottom:
                return makePatchesFromModel(rpf, "balustrade_stair_plain_bottom");
            case 71: //BanisterPlain:
                return makePatchesFromModel(rpf, "balustrade_stair_plain");
            case 72: //BanisterPlainTop:
                return makePatchesFromModel(rpf, "balustrade_stair_plain_top");
            case 73: //BalustradeFancy:
                return makePatchesFromModel(rpf, "balustrade_fancy");
            case 74: //BalustradeFancyCorner:
                return makePatchesFromModel(rpf, "balustrade_fancy_corner");
            case 75: //BalustradeFancyWithNewel:
                return makePatchesFromModel(rpf, "balustrade_fancy_with_newel");
            case 76: //BalustradeFancyNewel:
                return makePatchesFromModel(rpf, "balustrade_fancy_newel");
            case 77: //BalustradePlain:
                return makePatchesFromModel(rpf, "balustrade_plain");
            case 78: //BalustradePlainOuterCorner:
                return makePatchesFromModel(rpf, "balustrade_plain_outer_corner");
            case 79: //BalustradePlainWithNewel:
                return makePatchesFromModel(rpf, "balustrade_plain_with_newel");
            case 80: //BanisterPlainEnd:
                return makePatchesFromModel(rpf, "balustrade_stair_plain_end");
            case 81: //BanisterFancyNewelTall:
                return makePatchesFromModel(rpf, "balustrade_fancy_newel_tall");
            case 82: //BalustradePlainInnerCorner:
                return makePatchesFromModel(rpf, "balustrade_plain_inner_corner");
            case 83: //BalustradePlainEnd:
                return makePatchesFromModel(rpf, "balustrade_plain_end");
            case 84: //BanisterFancyBottom:
                return makePatchesFromModel(rpf, "balustrade_stair_fancy_bottom");
            case 85: //BanisterFancy:
                return makePatchesFromModel(rpf, "balustrade_stair_fancy");
            case 86: //BanisterFancyTop:
                return makePatchesFromModel(rpf, "balustrade_stair_fancy_top");
            case 87: //BanisterFancyEnd:
                return makePatchesFromModel(rpf, "balustrade_stair_fancy_end");
            case 88: //BanisterPlainInnerCorner:
                return makePatchesFromModel(rpf, "balustrade_stair_plain_inner_corner");
            case 90: //Slab:
                return makeSlab(rpf);
            case 91: //Stairs:
                return makeStairs(rpf);
            case 92: //StairsOuterCorner:
                return makePatchesFromModel(rpf, "stairs_outer_corner");
            case 93: //StairsInnerCorner:
                return makePatchesFromModel(rpf, "stairs_inner_corner");
            case 94: // SlopeTileA1:
                return makeSimpleSlopePatches(rpf, 0.5, 1);
            case 95: // SlopeTileA2:
                return makeSimpleSlopePatches(rpf, 0, 0.5);
            case 96: // SlopeTileB1:
                return makeSimpleSlopePatches(rpf, 0.6666, 1);
            case 97: // SlopeTileB2:
                return makeSimpleSlopePatches(rpf, 0.3333, 0.6666);
            case 98: // SlopeTileB3:
                return makeSimpleSlopePatches(rpf, 0, 0.3333);
            case 99: // SlopeTileC1:
                return makeSimpleSlopePatches(rpf, 0.75, 1);
            case 100: // SlopeTileC2:
                return makeSimpleSlopePatches(rpf, 0.5, 0.75);
            case 101: // SlopeTileC3:
                return makeSimpleSlopePatches(rpf, 0.25, 0.5);
            case 102: // SlopeTileC4:
                return makeSimpleSlopePatches(rpf, 0, 0.25);
            case 103: // SlopeTileA1SE:
                return makeSimpleSlopePatches(rpf, 0.5, 1);
            case 104: // SlopeTileA2SE:
                return makeSimpleSlopePatches(rpf, 0, 0.5);
            case 105: // SlopeTileB1SE:
                return makeSimpleSlopePatches(rpf, 0.6666, 1);
            case 106: // SlopeTileB2SE:
                return makeSimpleSlopePatches(rpf, 0.3333, 0.6666);
            case 107: // SlopeTileB3SE:
                return makeSimpleSlopePatches(rpf, 0, 0.3333);
            case 108: // SlopeTileC1SE:
                return makeSimpleSlopePatches(rpf, 0.75, 1);
            case 109: // SlopeTileC2SE:
                return makeSimpleSlopePatches(rpf, 0.5, 0.75);
            case 110: // SlopeTileC3SE:
                return makeSimpleSlopePatches(rpf, 0.25, 0.5);
            case 111: // SlopeTileC4SE:
                return makeSimpleSlopePatches(rpf, 0, 0.25);
            case 112: //RoofTileSE:
                return makeRoofPatches(rpf);
            case 113: // SquareSE:
                return basicBox;
            case 114: //SlabSE:
                return makeSlab(rpf);
            case 115: //AngledRoofRidge:
                return makePatchesFromModel(rpf, "angled_roof_ridge");
            case 116: //DoubleRoofTile:
                return makePatchesFromModel(rpf, "double_roof_tile");
        }

        return null;
    }

    private RenderPatch[] makeSlab(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        addBox(rpf, list, 0,1,0,0.5,0,1, patchlist);
        return list.toArray(new RenderPatch[list.size()]);
    }
    public static RenderPatch[] makeStairs(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        addBox(rpf, list, 0,1,0,0.5,0,1, patchlist);
        addBox(rpf, list, 0,1,0.5,1,0.5,1, patchlist);
        return list.toArray(new RenderPatch[list.size()]);
    }
    public static RenderPatch[] makeRoofInnerCornerPatches(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        // slopes
        list.add(rpf.getTriangleAutoTexCoords(0,1,1,1,1, 1, 0,0,0,1,  RenderPatchFactory.SideVisible.TOP,textureIdForOptionalSecondary ));
        list.add(rpf.getTriangleAutoTexCoords(1,1,1,1,1, 0, 0,0,0,1,  RenderPatchFactory.SideVisible.TOP,textureIdForOptionalSecondary ));

        // left side
        list.add(rpf.getTriangleAutoTexCoords(0,0, 1, 0,0,0,0,1,1,1, RenderPatchFactory.SideVisible.BOTTOM, 0));

        // front side
        list.add(rpf.getTriangleAutoTexCoords(1,0, 0,0,0,0,1,1,0, 1, RenderPatchFactory.SideVisible.TOP, 0));

        // right
        list.add(rpf.getPatch(1,1,0,1,1, 1, 1,0,0,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // back
        list.add(rpf.getPatch(1,1,1,0,1, 1, 1,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // bottom
        list.add(rpf.getPatch(0,0,0,1,0, 0, 0,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        RenderPatch[] renderPatches = list.toArray(new RenderPatch[list.size()]);
        for(int i= 0;i < renderPatches.length; i++)
            renderPatches[i] = rpf.getRotatedPatch(renderPatches[i], 0, 90, 0, 0);
        return renderPatches;
    }

    public static RenderPatch[] makeRoofOuterCornerPatches(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        // slopes
        list.add(rpf.getTriangleAutoTexCoords(0,0,1,0,0,0,1,1, 1, RenderPatchFactory.SideVisible.BOTTOM,textureIdForOptionalSecondary ));
        list.add(rpf.getTriangleAutoTexCoords(1,0, 0, 0,0,0,1,1,1, RenderPatchFactory.SideVisible.TOP,textureIdForOptionalSecondary ));

        // right
        list.add(rpf.getTriangleAutoTexCoords(1,0,1,1,0, 0, 1,1,1,  RenderPatchFactory.SideVisible.TOP,0 ));

        // back
        list.add(rpf.getTriangleAutoTexCoords(1,0,1,0,0, 1, 1,1,1,  RenderPatchFactory.SideVisible.BOTTOM,0 ));

        // bottom
        list.add(rpf.getPatch(0,0,0,1,0, 0, 0,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        RenderPatch[] renderPatches = list.toArray(new RenderPatch[list.size()]);
        for(int i= 0;i < renderPatches.length; i++)
            renderPatches[i] = rpf.getRotatedPatch(renderPatches[i], 0, 90, 0, 0);
        return renderPatches;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        Object shape = mapDataCtx.getBlockTileEntityField("Shape");
        RenderPatchFactory rpf = mapDataCtx.getPatchFactory();
        if(shape instanceof Integer) {
            int actualShape = ((Integer) shape).intValue();

            Object turnObj = mapDataCtx.getBlockTileEntityField("turn");
            Object sideObj = mapDataCtx.getBlockTileEntityField("side");
            int turn = 0;
            if (turnObj instanceof Byte) {
                turn = ((Byte) turnObj).byteValue();
            }
            int side = 0;
            if (sideObj instanceof Byte) {
                side = ((Byte) sideObj).byteValue();
            }

            if (renderPatchesPerShape[actualShape][0][0] == null) {
                renderPatchesPerShape[actualShape][0][0] = getRenderPatchForShape(rpf, actualShape);
            }

            if (renderPatchesPerShape[actualShape][side][turn] == null) {
                RenderPatch[] arr = renderPatchesPerShape[actualShape][0][0];
                if (arr != null && arr.length > 0) {
                    if (turn != 0 || side != 0) {
                        arr = arr.clone();

                        int xrot = 0, yrot = 0, zrot = 0;

                        for (int i = 0; i < arr.length; i++) {
                            switch (ForgeDirection.getOrientation(side)) {
                                case DOWN:
                                    yrot = 360 - 90 * turn;
                                    break;
                                case UP:
                                    yrot = 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 180, 0, 0, arr[i].getTextureIndex());
                                    break;
                                case NORTH:
                                    zrot = 360 - 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 270, 0, 0, arr[i].getTextureIndex());
                                    break;
                                case SOUTH:
                                    zrot = 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 90, 0, 180, arr[i].getTextureIndex());
                                    break;
                                case WEST:
                                    xrot = 360 - 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 0, 270, 90, arr[i].getTextureIndex());
                                    break;
                                case EAST:
                                    xrot = 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 0, 90, 270, arr[i].getTextureIndex());
                                    break;
                                case UNKNOWN:
                                    break;
                            }

                            arr[i] = rpf.getRotatedPatch(arr[i], xrot, yrot, zrot, arr[i].getTextureIndex());
                        }
                    }

                    renderPatchesPerShape[actualShape][side][turn] = arr;

                    return arr;
                }

            } else {
                return renderPatchesPerShape[actualShape][side][turn];
            }
        }
        return basicBox;
    }

    public static RenderPatch[] makeRoofPatches(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        // slope
        list.add(rpf.getPatch(0,1,1,1,1, 1, 0,0,0,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP, textureIdForOptionalSecondary ));

        // left side
        list.add(rpf.getTriangleAutoTexCoords(0,0, 1, 0,0,0,0,1,1,1, RenderPatchFactory.SideVisible.BOTTOM, 0));

        // right side
        list.add(rpf.getTriangleAutoTexCoords(1,0, 1,1,0,0, 1,1,1,1, RenderPatchFactory.SideVisible.TOP, 0));

        // back
        list.add(rpf.getPatch(1,1,1,0,1, 1, 1,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // bottom
        list.add(rpf.getPatch(0,0,0,1,0, 0, 0,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));
        return list.toArray(new RenderPatch[list.size()]);
    }
    private static RenderPatch[] makeSimpleSlopePatches(RenderPatchFactory rpf, double minY, double maxY) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        // slope
        list.add(rpf.getPatch(0, maxY,1,1, maxY, 1, 0, minY,0,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,textureIdForOptionalSecondary ));

        if(minY > 0) {
            // front
            list.add(rpf.getPatch(0, 1, 0, 1, 1, 0, 0, 0, 0, 0, 1, 1-minY, 1, RenderPatchFactory.SideVisible.TOP, 0));
        }

        // left side
        list.add(rpf.getTriangleAutoTexCoords(0,minY, 1, 0,minY,0,0,maxY,1,1, RenderPatchFactory.SideVisible.BOTTOM, 0));
        if(minY > 0) {
            list.add(rpf.getPatch(0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0, minY, RenderPatchFactory.SideVisible.BOTTOM, 0));
        }

        // right side
        list.add(rpf.getTriangleAutoTexCoords(1,minY, 1,1,minY,0, 1,maxY,1,1, RenderPatchFactory.SideVisible.TOP, 0));
        if(minY > 0) {
            list.add(rpf.getPatch(1, 0, 0, 1, 0, 1, 1, 1, 0, 0, 1, 0, minY, RenderPatchFactory.SideVisible.BOTTOM, 0));
        }

        // back
        list.add(rpf.getPatch(1, 1,1,0, 1, 1, 1,0,1,0, 1, 1-maxY, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // bottom
        list.add(rpf.getPatch(0,0,0,1,0, 0, 0,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));
        return list.toArray(new RenderPatch[list.size()]);
    }
    private static RenderPatch[] makePatchesFromModel(RenderPatchFactory rpf, String modelName){
        ArchitectureCraftModel mod = ArchitectureCraftModel.getModel("shape/" + modelName + ".smeg");
        if(mod == null)
            return new RenderPatch[0];

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int lowestTextureNum = 100;
        for(ArchitectureCraftModel.Face f : mod.faces){
            if(f.texture < lowestTextureNum)
                lowestTextureNum = f.texture;
        }
        for(ArchitectureCraftModel.Face f : mod.faces){

            for(int[] t : f.triangles){
                double[] a = f.vertices[t[0]], b = f.vertices[t[1]], c = f.vertices[t[2]];

                list.add(rpf.getTriangleAutoTexCoords(a[0]+0.5, a[1]+0.5, a[2]+0.5, b[0]+0.5, b[1]+0.5, b[2]+0.5, c[0]+0.5, c[1]+0.5, c[2]+0.5, RenderPatchFactory.SideVisible.TOP, f.texture == lowestTextureNum ? 0 : textureIdForOptionalSecondary));
            }
        }

        return list.toArray(new RenderPatch[list.size()]);
    }


    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new ArchitectureCraftTextureLookupThing(mapDataCtx));
    }

    static String[] nbtFieldsNeeded = {"turn", "Shape", "side", "BaseName", "BaseData", "Name2", "Data2"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class ArchitectureCraftTextureLookupThing implements CustomTextureMapper {

        private MapDataContext mapDataCtx;
        int[][] textures = new int[6][0];
        int[][] textures2 = {{-1}, {-1}, {-1}, {-1}, {-1}, {-1}};
        boolean foundSecondaryTextures = false;

        public ArchitectureCraftTextureLookupThing(MapDataContext mapDataCtx) {

            this.mapDataCtx = mapDataCtx;

            Object blockNameObj = mapDataCtx.getBlockTileEntityField("BaseName");
            Object blockDataObj = mapDataCtx.getBlockTileEntityField("BaseData");

            Object blockName2Obj = mapDataCtx.getBlockTileEntityField("Name2");
            Object blockData2Obj = mapDataCtx.getBlockTileEntityField("Data2");

            if(blockName2Obj instanceof String){
                String blockName = (String)blockName2Obj;
                int data = 0;
                if(blockDataObj instanceof Integer) {
                    data = ((Integer) blockData2Obj).intValue();
                }

                int blockId = GWM_Util.blockNameToId(blockName);
                TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blockId, data, 0);
                textures2 = new int[][]{ {map.getIndexForFace(0)}, {map.getIndexForFace(1)}, {map.getIndexForFace(2)}, {map.getIndexForFace(3)}, {map.getIndexForFace(4)}, {map.getIndexForFace(5)} };

                foundSecondaryTextures = true;
            }
            if(blockNameObj instanceof String){
                String blockName = (String)blockNameObj;
                int data = 0;
                if(blockDataObj instanceof Integer) {
                    data = ((Integer) blockDataObj).intValue();
                }

                int blockId = GWM_Util.blockNameToId(blockName);

                TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blockId, data, 0);

                textures = new int[][]{ {map.getIndexForFace(0)}, {map.getIndexForFace(1)}, {map.getIndexForFace(2)}, {map.getIndexForFace(3)}, {map.getIndexForFace(4)}, {map.getIndexForFace(5)} };
            }
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(patchId >= 6 && patchId < 12)
                return textures2[patchId-6];
            if(patchId >= 12 && patchId < 18){
                if(foundSecondaryTextures){
                    return textures2[patchId-12];
                }
                patchId -= 12;
            }
            return textures[patchId];
        }
    }
}
