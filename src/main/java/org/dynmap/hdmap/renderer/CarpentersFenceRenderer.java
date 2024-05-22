package org.dynmap.hdmap.renderer;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class CarpentersFenceRenderer extends FenceGateBase {

    RenderPatch[][][] postVersions, wallVersions;
    private static final int SIDE_XP = 0x1;
    private static final int SIDE_XN = 0x2;
    private static final int SIDE_X = SIDE_XN | SIDE_XP;
    private static final int SIDE_ZP = 0x4;
    private static final int SIDE_ZN = 0x8;
    private static final int SIDE_Z = SIDE_ZN | SIDE_ZP;
    private static final int SIDE_YP = 0x10;
    private static int[][] sides = {
            { 1, 0, 0, SIDE_XP },
            { -1, 0, 0, SIDE_XN },
            { 0, 0, 1, SIDE_ZP },
            { 0, 0, -1, SIDE_ZN }
    };

    int thisBlockId;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        thisBlockId = blkid;

        postVersions = new RenderPatch[8][][];
        postVersions[0] = makeFencePostVersions(rpf);
        postVersions[6] = makeWallPostVersions(rpf);

        wallVersions = new RenderPatch[8][][];
        wallVersions[0] = makeFenceWallVersions(rpf);
        wallVersions[6] = makeWallWallVersions(rpf);

        for(int i = 1; i < postVersions.length; i++) {
            if(postVersions[i] == null)
                postVersions[i] = postVersions[0];

            if(wallVersions[i] == null)
                wallVersions[i] = wallVersions[0];
        }

        link_ids.set(blkid);

        for(int i = 0; true; i++) {
            String lid = custparm.get("link" + i);
            if(lid == null) break;
            link_ids.set(GWM_Util.blockNameToId(lid));
        }

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    private RenderPatch[][] makeWallWallVersions(RenderPatchFactory rpf) {
        RenderPatch[][] ret = new RenderPatch[4][];

        ret[0] = getBoxSingleTextureInt(rpf, 0, 16, 0, 13, 5, 11, 0, false);
        ret[1] = getBoxSingleTextureInt(rpf, 5, 11, 0, 13, 0, 16, 0, false);
        ret[2] = getBoxSingleTextureInt(rpf, 0, 16, 0, 16, 5, 11, 0, false);
        ret[3] = getBoxSingleTextureInt(rpf, 5, 11, 0, 16, 0, 16, 0, false);
        return ret;
    }

    private RenderPatch[][] makeFenceWallVersions(RenderPatchFactory rpf) {
        RenderPatch[][] ret = new RenderPatch[2][];

        ret[0] = combineMultiple(
                getBoxSingleTextureInt(rpf, 0,16,12,15,7,9,0, false),
                getBoxSingleTextureInt(rpf, 0,16,6,9,7,9,0, false)
        );
        ret[1] = combineMultiple(
                getBoxSingleTextureInt(rpf, 7,9,12,15,0,16,0, false),
                getBoxSingleTextureInt(rpf, 7,9,6,9,0,16,0, false)
        );
        return ret;
    }

    private RenderPatch[][] makeWallPostVersions(RenderPatchFactory rpf) {
        RenderPatch[][] ret = new RenderPatch[32][];
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int[] patchIds = {0, 0, 0, 0, 0, 0};
        for(int i = 0; i < 32; i++){
            addBoxInt(rpf, list, 4,12,0,16,4,12,patchIds);
            int ymax = (i & 16) == 0 ? 13 : 16;
            if((i & SIDE_XN) == SIDE_XN){
                addBoxInt(rpf, list, 0,4,0,ymax,5,11, patchIds);
            }
            if((i & SIDE_XP) == SIDE_XP){
                addBoxInt(rpf, list, 12,16,0,ymax,5,11, patchIds);
            }
            if((i & SIDE_ZN) == SIDE_ZN){
                addBoxInt(rpf, list, 5,11, 0,ymax, 0,4,patchIds);
            }
            if((i & SIDE_ZP) == SIDE_ZP){
                addBoxInt(rpf, list, 5,11,0,ymax,12,16, patchIds);
            }
            ret[i] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
        }
        return ret;
    }

    private RenderPatch[][] makeFencePostVersions(RenderPatchFactory rpf) {
        RenderPatch[][] ret = new RenderPatch[16][];
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int[] patchIds = {0, 0, 0, 0, 0, 0};
        for(int i = 0; i < 16; i++){
            addBoxInt(rpf, list, 6,10,0,16,6,10,patchIds);

            if((i & SIDE_XN) == SIDE_XN){
                addBoxInt(rpf, list, 0,6,12,15,7,9, patchIds);
                addBoxInt(rpf, list, 0,6,6,9,7,9, patchIds);
            }
            if((i & SIDE_XP) == SIDE_XP){
                addBoxInt(rpf, list, 10,16,12,15,7,9, patchIds);
                addBoxInt(rpf, list, 10,16,6,9,7,9, patchIds);
            }
            if((i & SIDE_ZN) == SIDE_ZN){
                addBoxInt(rpf, list, 7,9,12,15, 0,6,patchIds);
                addBoxInt(rpf, list, 7,9,6,9,0,6, patchIds);
            }
            if((i & SIDE_ZP) == SIDE_ZP){
                addBoxInt(rpf, list, 7,9,12,15,10,16, patchIds);
                addBoxInt(rpf, list, 7,9,6,9,10,16, patchIds);
            }
            ret[i] = list.toArray(new RenderPatch[list.size()]);
            list.clear();
        }
        return ret;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {

        int meta = GWM_Util.objectToInt(ctx.getBlockTileEntityField("cbMetadata"), 0);

        if(meta < 0 || meta >= postVersions.length)
            meta = 6;

        boolean check_yplus = meta == 6;
        boolean allConnectsAreThisBlock = true;

        /* Build connection map - check each axis */
        int connect = 0;
        for(int i = 0; i < sides.length; i++) {
            int id = ctx.getBlockTypeIDAt(sides[i][0], sides[i][1], sides[i][2]);
            if (id == 0) continue;
            if (id == thisBlockId) {
                connect |= sides[i][3];
            } else if(link_ids.get(id) || (TexturePack.HDTextureMap.getTransparency(id) == TexturePack.BlockTransparency.OPAQUE)) {
                connect |= sides[i][3];
                allConnectsAreThisBlock = false;
            }
        }
        if(check_yplus) {
            if(ctx.getBlockTypeIDAt(0, 1, 0) > 0) {
                connect |= SIDE_YP;
            }
        }

        if(allConnectsAreThisBlock && wallVersions[meta] != null && ((connect & 0xF) == 3 || (connect & 0xF) == 12)){
            switch (connect){
                case 3:
                    if(wallVersions[meta][0] != null)
                        return wallVersions[meta][0];
                    break;
                case 12:
                    if(wallVersions[meta][1] != null)
                        return wallVersions[meta][1];
                    break;
                case 19:
                    if(wallVersions[meta][2] != null)
                        return wallVersions[meta][2];
                    break;
                case 28:
                    if(wallVersions[meta][3] != null)
                        return wallVersions[meta][3];
                    break;
            }
        }

        return postVersions[meta][connect];
    }


    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new CarpentersBlocksRenderer.TextureSelector(mapDataCtx));
    }
    static String[] nbtFieldsNeeded = {"cbMetadata", "cbAttrList"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
