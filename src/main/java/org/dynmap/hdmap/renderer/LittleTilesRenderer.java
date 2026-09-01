package org.dynmap.hdmap.renderer;

import org.dynmap.Log;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.SimpleColorMultiplier;
import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.utils.BlockOffset;
import org.dynmap.utils.TriangleMesh;
import org.dynmap.utils.objloader.ObjLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LittleTilesRenderer extends CustomRenderer {
    private static final int fullBlockPatchList[] = { 0, 1, 4, 5, 2, 3 };
    static final int MaxTiles = 4096;
    static final TriangleMesh[][] meshes =  new TriangleMesh[10][];

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        meshes[1] = loadMesh("slope.obj");
        //meshes[2] = loadMesh("pillar.obj");
        meshes[3] = loadMesh("slope_concave.obj");
        meshes[4] = loadMesh("slope_convex.obj");
        meshes[5] = loadMesh("slope_triangle.obj");
        meshes[6] = loadMesh("slope_triangle_corner.obj");
        meshes[7] = loadMesh("slope_outer.obj");
        meshes[8] = loadMesh("slope_inner.obj");
        meshes[9] = loadMesh("slope_triangle_alternate.obj");

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    TriangleMesh[] loadMesh(String name){
        try {
            InputStreamReader reader = new InputStreamReader(LittleTilesRenderer.class.getResourceAsStream("/assets/littletiles/models/" + name));
            Map<String, TriangleMesh> result = ObjLoader.load(reader, null);
            TriangleMesh base = result.get("default/default");

            if(base != null){
                TriangleMesh[] ret = new TriangleMesh[48];
                ret[0] = base.clone();
                base.translate(-.5, -.5, -.5);
                for(int i = 1; i < 48; i++){
                    TriangleMesh tmp = base.clone();
                    rotateMesh(tmp, i);
                    tmp.translate(.5,.5,.5);
                    ret[i] = tmp;
                }
                return ret;
            }

            return null;
        }
        catch (IOException ex){
            Log.warning("Failed to load LittleTiles model " + name);
        }
        return null;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return new RenderPatch[0];
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        int tilesCount = GWM_Util.objectToInt(mapDataCtx.getBlockTileEntityField("tilesCount"), 0);
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int[][] textures = new int[6*tilesCount][1];
        CustomColorMultiplier[] multipliers = new CustomColorMultiplier[tilesCount];
        for(int i = 0; i < tilesCount; i++){

            HashMap<String, Object> subTile = (HashMap)mapDataCtx.getBlockTileEntityField("t" + i);

            String blockId = (String)subTile.get("block");
            int meta = GWM_Util.objectToInt(subTile.get("meta"),0);
            int blkid = GWM_Util.blockNameToId(blockId);

            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blkid, meta, 0);

            int col = map.getColorMult();
            int colFromBlock = GWM_Util.objectToInt(subTile.get("color"), -1);

            if((col & 0xFFFFFF) != 0xFFFFFF){
                if((colFromBlock & 0xFFFFFF) != 0xFFFFFF){
                    int ra = (col & 0xFF0000) >> 16;
                    int rb = (colFromBlock & 0xFF0000) >> 16;
                    int ga = (col & 0xFF00) >> 8;
                    int gb = (colFromBlock & 0xFF00) >> 8;
                    int ba = (col & 0xFF);
                    int bb = (colFromBlock & 0xFF);

                    int combR = (ra*rb) >> 8;
                    int combG = (ga*gb) >> 8;
                    int combB = (ba*bb) >> 8;

                    multipliers[i] = new SimpleColorMultiplier((combR << 16) | (combG << 8) | combB);
                }
                else {
                    multipliers[i] = new SimpleColorMultiplier(col);
                }
            }
            else if((colFromBlock & 0xFFFFFF) != 0xFFFFFF){
                multipliers[i] = new SimpleColorMultiplier(colFromBlock);
            }

            int[] subTilePatchIds = new int[6];
            for(int j = 0; j < 6; j++){
                subTilePatchIds[j] = fullBlockPatchList[j] + 6*i;
                textures[subTilePatchIds[j]][0] = map.getIndexForFace(fullBlockPatchList[j]);
            }

            int cutoutType = GWM_Util.objectToInt(subTile.get("cutoutType"), 0);
            switch(cutoutType){
                case 1:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                {
                    int orientation = GWM_Util.objectToInt(subTile.get("cutoutOrientation"), 0);
                    if(meshes[cutoutType] != null && meshes[cutoutType][orientation] != null){
                        TriangleMesh mesh = meshes[cutoutType][orientation].clone();
                        int posX =  GWM_Util.objectToInt(subTile.get("cutoutPosX"), 0);
                        int posY =  GWM_Util.objectToInt(subTile.get("cutoutPosY"), 0);
                        int posZ =  GWM_Util.objectToInt(subTile.get("cutoutPosZ"), 0);
                        int sizeX =  GWM_Util.objectToInt(subTile.get("cutoutSizeX"), 0);
                        int sizeY =  GWM_Util.objectToInt(subTile.get("cutoutSizeY"), 0);
                        int sizeZ =  GWM_Util.objectToInt(subTile.get("cutoutSizeZ"), 0);
                        int cVecX = GWM_Util.objectToInt(subTile.get("cVecx"), 0);
                        int cVecY = GWM_Util.objectToInt(subTile.get("cVecy"), 0);
                        int cVecZ = GWM_Util.objectToInt(subTile.get("cVecz"), 0);
                        mesh.scale(sizeX/16.0, sizeY/16.0, sizeZ/16.0);
                        mesh.translate((posX+cVecX) / 16.0,(posY+cVecY)/16.0,(posZ+cVecZ)/16.0);
                        RenderPatch[] gwmMesh = mesh.exportSingleBlockSpaceToRenderPatches(new BlockOffset(0,0,0), mapDataCtx.getPatchFactory(), true);
                        list.addAll(Arrays.asList(gwmMesh));
                    }
                    break;
                }

                default:
                {
                    int numSub = GWM_Util.objectToInt(subTile.get("bSize"),0);
                    for(int b = 0; b < numSub; b++){
                        int minX = GWM_Util.objectToInt(subTile.get("bBox"+b+"minX"),0);
                        int maxX = GWM_Util.objectToInt(subTile.get("bBox"+b+"maxX"),0);
                        int minY = GWM_Util.objectToInt(subTile.get("bBox"+b+"minY"),0);
                        int maxY = GWM_Util.objectToInt(subTile.get("bBox"+b+"maxY"),0);
                        int minZ = GWM_Util.objectToInt(subTile.get("bBox"+b+"minZ"),0);
                        int maxZ = GWM_Util.objectToInt(subTile.get("bBox"+b+"maxZ"),0);

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, minX / 16.0, maxX/ 16.0, minY/ 16.0, maxY/ 16.0,minZ/ 16.0, maxZ/ 16.0, subTilePatchIds);
                    }
                    break;
                }
            }
        }
        RenderPatch[] model = list.toArray(new RenderPatch[list.size()]);

        return new CustomData(model, textures, multipliers);
    }

    void rotateMesh(TriangleMesh mesh, int orientation){
        if(orientation >= 24){
            mesh.scale(-1,1,1);
        }
        switch(orientation % 24){
            case 1:
                mesh.rotateZ(270);
                break;
            case 2:
                mesh.rotateZ(180);
                break;
            case 3:
                mesh.rotateZ(90);
                break;
            case 4:
                mesh.rotateY(270);
                break;
            case 5:
                mesh.rotateZ(270);
                mesh.rotateY(270);
                break;
            case 6:
                mesh.rotateZ(180);
                mesh.rotateY(270);
                break;
            case 7:
                mesh.rotateZ(90);
                mesh.rotateY(270);
                break;
            case 8:
                mesh.rotateY(180);
                break;
            case 9:
                mesh.rotateZ(270);
                mesh.rotateY(180);
                break;
            case 10:
                mesh.rotateZ(180);
                mesh.rotateY(180);
                break;
            case 11:
                mesh.rotateZ(90);
                mesh.rotateY(180);
                break;
            case 12:
                mesh.rotateY(90);
                break;
            case 13:
                mesh.rotateZ(270);
                mesh.rotateY(90);
                break;
            case 14:
                mesh.rotateZ(180);
                mesh.rotateY(90);
                break;
            case 15:
                mesh.rotateZ(90);
                mesh.rotateY(90);
                break;
            case 16:
                mesh.rotateX(270);
                break;
            case 17:
                mesh.rotateZ(270);
                mesh.rotateX(270);
                break;
            case 18:
                mesh.rotateZ(180);
                mesh.rotateX(270);
                break;
            case 19:
                mesh.rotateZ(90);
                mesh.rotateX(270);
                break;
            case 20:
                mesh.rotateY(180);
                mesh.rotateX(270);
                break;
            case 21:
                mesh.rotateZ(270);
                mesh.rotateY(180);
                mesh.rotateX(270);
                break;
            case 22:
                mesh.rotateZ(180);
                mesh.rotateY(180);
                mesh.rotateX(270);
                break;
            case 23:
                mesh.rotateZ(90);
                mesh.rotateY(180);
                mesh.rotateX(270);
                break;
        }
    }

    static String[] nbtFieldsNeeded = null;

    @Override
    public String[] getTileEntityFieldsNeeded() {
        if(nbtFieldsNeeded == null){
            nbtFieldsNeeded = new String[MaxTiles + 1];
            nbtFieldsNeeded[0] = "tilesCount";
            for(int i = 0; i < MaxTiles; i++)
                nbtFieldsNeeded[i+1] = "t" + i;
        }
        return nbtFieldsNeeded;
    }

    static class CustomData extends CustomRendererData implements CustomTextureMapper     {

        private final int[][] textures;
        private final CustomColorMultiplier[] multipliers;

        public CustomData(RenderPatch[] mesh, int[][] textures, CustomColorMultiplier[] multipliers) {
            super(mesh, null, null);
            this.textures = textures;
            this.multipliers = multipliers;
        }

        @Override
        public CustomTextureMapper getCustomTextureMapper() {
            return this;
        }

        @Override
        public CustomColorMultiplier getCustomColorMultiplier(int patchId, int layer) {
            return multipliers[patchId / 6];
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return textures[patchId];
        }
    }
}
