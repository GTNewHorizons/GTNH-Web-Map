package org.dynmap.hdmap.renderer;

import gcewing.architecture.*;
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
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchlist);
        basicBox = list.toArray(new RenderPatch[patchlist.length]);

        renderPatchesPerShape = new RenderPatch[256][6][4][];

        for(Shape s : Shape.values()){
            renderPatchesPerShape[s.id][0][0] = getRenderPatchForShape(rpf, s.id);
        }

        return true;
    }

    RenderPatch[] getRenderPatchForShape(RenderPatchFactory rpf, int id){
        Shape s = Shape.forId(id);
        if(s.id == id) {

            switch (s) {
                case RoofTile:
                    return makeRoofPatches(rpf);
                case RoofOuterCorner:
                    return makeRoofOuterCornerPatches(rpf);
                case RoofInnerCorner:
                    return makeRoofInnerCornerPatches(rpf);
                case RoofRidge:
                    break;
                case RoofSmartRidge:
                    break;
                case RoofValley:
                    break;
                case RoofSmartValley:
                    break;
                case RoofOverhang:
                    return makePatchesFromModel(rpf, "roof_overhang");
                case RoofOverhangOuterCorner:
                    return makePatchesFromModel(rpf, "roof_overhang_outer_corner");
                case RoofOverhangInnerCorner:
                    return makePatchesFromModel(rpf, "roof_overhang_inner_corner");
                case Cylinder:
                    return makePatchesFromModel(rpf, "cylinder_full_r8h16");
                case CylinderHalf:
                    return makePatchesFromModel(rpf, "cylinder_half_r8h16");
                case CylinderQuarter:
                    return makePatchesFromModel(rpf, "cylinder_quarter_r8h16");
                case CylinderLargeQuarter:
                    return makePatchesFromModel(rpf, "cylinder_quarter_r16h16");
                case AnticylinderLargeQuarter:
                    return makePatchesFromModel(rpf, "round_inner_corner");
                case Pillar:
                    return makePatchesFromModel(rpf, "cylinder_r6h16");
                case Post:
                    return makePatchesFromModel(rpf, "cylinder_r4h16");
                case Pole:
                    return makePatchesFromModel(rpf, "cylinder_r2h16");
                case BevelledOuterCorner:
                    return makePatchesFromModel(rpf, "bevelled_outer_corner");
                case BevelledInnerCorner:
                    return makePatchesFromModel(rpf, "bevelled_inner_corner");
                case PillarBase:
                    return makePatchesFromModel(rpf, "pillar_base");
                case DoricCapital:
                    return makePatchesFromModel(rpf, "doric_capital");
                case IonicCapital:
                    return makePatchesFromModel(rpf, "ionic_capital");
                case CorinthianCapital:
                    return makePatchesFromModel(rpf, "corinthian_capital");
                case DoricTriglyph:
                    return makePatchesFromModel(rpf, "doric_triglyph");
                case DoricTriglyphCorner:
                    return makePatchesFromModel(rpf, "doric_triglyph_corner");
                case DoricMetope:
                    return makePatchesFromModel(rpf, "doric_metope");
                case Architrave:
                    return makePatchesFromModel(rpf, "architrave");
                case ArchitraveCorner:
                    return makePatchesFromModel(rpf, "architrave_corner");
                case WindowFrame:
                    break;
                case WindowCorner:
                    break;
                case WindowMullion:
                    break;
                case SphereFull:
                    return makePatchesFromModel(rpf, "sphere_full_r8");
                case SphereHalf:
                    return makePatchesFromModel(rpf, "sphere_half_r8");
                case SphereQuarter:
                    return makePatchesFromModel(rpf, "sphere_quarter_r8");
                case SphereEighth:
                    return makePatchesFromModel(rpf, "sphere_eighth_r8");
                case SphereEighthLarge:
                    return makePatchesFromModel(rpf, "sphere_eighth_r16");
                case SphereEighthLargeRev:
                    return makePatchesFromModel(rpf, "sphere_eighth_r16_rev");
                case RoofOverhangGableLH:
                    return makePatchesFromModel(rpf, "roof_overhang_gable_lh");
                case RoofOverhangGableRH:
                    return makePatchesFromModel(rpf, "roof_overhang_gable_rh");
                case RoofOverhangGableEndLH:
                    return makePatchesFromModel(rpf, "roof_overhang_gable_end_lh");
                case RoofOverhangGableEndRH:
                    return makePatchesFromModel(rpf, "roof_overhang_gable_end_rh");
                case RoofOverhangRidge:
                    return makePatchesFromModel(rpf, "roof_overhang_gable_ridge");
                case RoofOverhangValley:
                    return makePatchesFromModel(rpf, "roof_overhang_gable_valley");
                case CorniceLH:
                    return makePatchesFromModel(rpf, "cornice_lh");
                case CorniceRH:
                    return makePatchesFromModel(rpf, "cornice_rh");
                case CorniceEndLH:
                    return makePatchesFromModel(rpf, "cornice_end_lh");
                case CorniceEndRH:
                    return makePatchesFromModel(rpf, "cornice_end_rh");
                case CorniceRidge:
                    return makePatchesFromModel(rpf, "cornice_ridge");
                case CorniceValley:
                    return makePatchesFromModel(rpf, "cornice_valley");
                case CorniceBottom:
                    return makePatchesFromModel(rpf, "cornice_bottom");
                case CladdingSheet:
                    break;
                case ArchD1:
                    return makePatchesFromModel(rpf, "arch_d1");
                case ArchD2:
                    return makePatchesFromModel(rpf, "arch_d2");
                case ArchD3A:
                    return makePatchesFromModel(rpf, "arch_d3a");
                case ArchD3B:
                    return makePatchesFromModel(rpf, "arch_d3b");
                case ArchD3C:
                    return makePatchesFromModel(rpf, "arch_d3c");
                case ArchD4A:
                    return makePatchesFromModel(rpf, "arch_d4a");
                case ArchD4B:
                    return makePatchesFromModel(rpf, "arch_d4b");
                case ArchD4C:
                    return makePatchesFromModel(rpf, "arch_d4c");
                case BanisterPlainBottom:
                    return makePatchesFromModel(rpf, "balustrade_stair_plain_bottom");
                case BanisterPlain:
                    return makePatchesFromModel(rpf, "balustrade_stair_plain");
                case BanisterPlainTop:
                    return makePatchesFromModel(rpf, "balustrade_stair_plain_top");
                case BalustradeFancy:
                    return makePatchesFromModel(rpf, "balustrade_fancy");
                case BalustradeFancyCorner:
                    return makePatchesFromModel(rpf, "balustrade_fancy_corner");
                case BalustradeFancyWithNewel:
                    return makePatchesFromModel(rpf, "balustrade_fancy_with_newel");
                case BalustradeFancyNewel:
                    return makePatchesFromModel(rpf, "balustrade_fancy_newel");
                case BalustradePlain:
                    return makePatchesFromModel(rpf, "balustrade_plain");
                case BalustradePlainOuterCorner:
                    return makePatchesFromModel(rpf, "balustrade_plain_outer_corner");
                case BalustradePlainWithNewel:
                    return makePatchesFromModel(rpf, "balustrade_plain_with_newel");
                case BanisterPlainEnd:
                    return makePatchesFromModel(rpf, "balustrade_stair_plain_end");
                case BanisterFancyNewelTall:
                    return makePatchesFromModel(rpf, "balustrade_fancy_newel_tall");
                case BalustradePlainInnerCorner:
                    return makePatchesFromModel(rpf, "balustrade_plain_inner_corner");
                case BalustradePlainEnd:
                    return makePatchesFromModel(rpf, "balustrade_plain_end");
                case BanisterFancyBottom:
                    return makePatchesFromModel(rpf, "balustrade_stair_fancy_bottom");
                case BanisterFancy:
                    return makePatchesFromModel(rpf, "balustrade_stair_fancy");
                case BanisterFancyTop:
                    return makePatchesFromModel(rpf, "balustrade_stair_fancy_top");
                case BanisterFancyEnd:
                    return makePatchesFromModel(rpf, "balustrade_stair_fancy_end");
                case BanisterPlainInnerCorner:
                    return makePatchesFromModel(rpf, "balustrade_stair_plain_inner_corner");
                case Slab:
                    return makeSlab(rpf);
                case Stairs:
                    return makeStairs(rpf);
                case StairsOuterCorner:
                    return makePatchesFromModel(rpf, "stairs_outer_corner");
                case StairsInnerCorner:
                    return makePatchesFromModel(rpf, "stairs_inner_corner");

            }
        }

        switch (id)
        {
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
    private RenderPatch[] makeRoofInnerCornerPatches(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        // slopes
        list.add(rpf.getPatch(0,1,1,1,1, 1, 0,0,0,1,  RenderPatchFactory.SideVisible.TOP,0 ));
        list.add(rpf.getPatch(1,1,1,1,1, 0, 0,0,0,1,  RenderPatchFactory.SideVisible.TOP,0 ));

        // left side
        list.add(rpf.getPatch(0,0, 1, 0,0,0,0,1,1,1, RenderPatchFactory.SideVisible.BOTTOM, 0));

        // front side
        list.add(rpf.getPatch(1,0, 0,0,0,0,1,1,0, 1, RenderPatchFactory.SideVisible.TOP, 0));

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

    private RenderPatch[] makeRoofOuterCornerPatches(RenderPatchFactory rpf) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        // slopes
        list.add(rpf.getPatch(0,0,1,0,0,0,1,1, 1, 1,  RenderPatchFactory.SideVisible.BOTTOM,0 ));
        list.add(rpf.getPatch(1,0, 0, 0,0,0,1,1,1,1,  RenderPatchFactory.SideVisible.TOP,0 ));

        // right
        list.add(rpf.getPatch(1,0,1,1,0, 0, 1,1,1,1,  RenderPatchFactory.SideVisible.TOP,0 ));

        // back
        list.add(rpf.getPatch(1,0,1,0,0, 1, 1,1,1,1,  RenderPatchFactory.SideVisible.BOTTOM,0 ));

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
                                    arr[i] = rpf.getRotatedPatch(arr[i], 180, 0, 0, 0);
                                    break;
                                case NORTH:
                                    zrot = 360 - 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 270, 0, 0, 0);
                                    break;
                                case SOUTH:
                                    zrot = 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 90, 0, 180, 0);
                                    break;
                                case WEST:
                                    xrot = 360 - 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 0, 270, 90, 0);
                                    break;
                                case EAST:
                                    xrot = 90 * turn;
                                    arr[i] = rpf.getRotatedPatch(arr[i], 0, 90, 270, 0);
                                    break;
                                case UNKNOWN:
                                    break;
                            }

                            arr[i] = rpf.getRotatedPatch(arr[i], xrot, yrot, zrot, 0);
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
        list.add(rpf.getPatch(0,1,1,1,1, 1, 0,0,0,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // left side
        list.add(rpf.getPatch(0,0, 1, 0,0,0,0,1,1,1, RenderPatchFactory.SideVisible.BOTTOM, 0));

        // right side
        list.add(rpf.getPatch(1,0, 1,1,0,0, 1,1,1,1, RenderPatchFactory.SideVisible.TOP, 0));

        // back
        list.add(rpf.getPatch(1,1,1,0,1, 1, 1,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // bottom
        list.add(rpf.getPatch(0,0,0,1,0, 0, 0,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));
        return list.toArray(new RenderPatch[list.size()]);
    }
    private static RenderPatch[] makeSimpleSlopePatches(RenderPatchFactory rpf, double minY, double maxY) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        // slope
        list.add(rpf.getPatch(0, maxY,1,1, maxY, 1, 0, minY,0,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        if(minY > 0) {
            // front
            list.add(rpf.getPatch(0, minY, 0, 1, minY, 0, 0, 0, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP, 0));
        }

        // left side
        list.add(rpf.getPatch(0,minY, 1, 0,minY,0,0,maxY,1,1, RenderPatchFactory.SideVisible.BOTTOM, 0));
        if(minY > 0) {
            list.add(rpf.getPatch(0, 0, 1, 0, 0, 0, 0, minY, 1, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTTOM, 0));
        }

        // right side
        list.add(rpf.getPatch(1,minY, 1,1,minY,0, 1,maxY,1,1, RenderPatchFactory.SideVisible.TOP, 0));
        if(minY > 0) {
            list.add(rpf.getPatch(1, 0, 0, 1, 0, 1, 1, minY, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTTOM, 0));
        }

        // back
        list.add(rpf.getPatch(1, maxY,1,0, maxY, 1, 1,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));

        // bottom
        list.add(rpf.getPatch(0,0,0,1,0, 0, 0,0,1,0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP,0 ));
        return list.toArray(new RenderPatch[list.size()]);
    }
    private static RenderPatch[] makePatchesFromModel(RenderPatchFactory rpf, String modelName){
        ArchitectureCraftModel mod = ArchitectureCraftModel.getModel("shape/" + modelName + ".smeg");
        if(mod == null)
            return new RenderPatch[0];

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        for(ArchitectureCraftModel.Face f : mod.faces){

            for(int[] t : f.triangles){
                double[] a = f.vertices[t[0]], b = f.vertices[t[1]], c = f.vertices[t[2]];

                list.add(rpf.getPatch(a[0]+0.5, a[1]+0.5, a[2]+0.5, b[0]+0.5, b[1]+0.5, b[2]+0.5, c[0]+0.5, c[1]+0.5, c[2]+0.5, 1, RenderPatchFactory.SideVisible.TOP, 0));
            }
        }

        return list.toArray(new RenderPatch[list.size()]);
    }


    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new ArchitectureCraftTextureLookupThing(mapDataCtx));
    }

    static String[] nbtFieldsNeeded = {"turn", "Shape", "side", "BaseName", "BaseData"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class ArchitectureCraftTextureLookupThing implements CustomTextureMapper {

        private MapDataContext mapDataCtx;
        int[][] textures = new int[6][0];

        public ArchitectureCraftTextureLookupThing(MapDataContext mapDataCtx) {

            this.mapDataCtx = mapDataCtx;

            Object blockNameObj = mapDataCtx.getBlockTileEntityField("BaseName");
            Object blockDataObj = mapDataCtx.getBlockTileEntityField("BaseData");

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
            return textures[patchId];
        }
    }
}
