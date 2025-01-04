package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ChiselPositionBasedRenderer extends CustomRenderer {

    static RenderPatch[][] versions2x2, versions3x3;
    int num = 2;

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if(versions2x2 == null)
            versions2x2 = initVersion(rpf,2);
        if(versions3x3 == null)
            versions3x3 = initVersion(rpf, 3);

        if(custparm.containsKey("is3x3"))
            num = 3;

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        int x = mapDataCtx.getX() % num;
        int y = mapDataCtx.getY() % num;
        int z = mapDataCtx.getZ() % num;

        if(x < 0)
            x += num;
        if(y < 0)
            y += num;
        if(z < 0)
            z += num;

        int version = x * num * num + y * num + z;

        if(num == 2)
            return versions2x2[version];

        return versions3x3[version];
    }

    RenderPatch[][] initVersion(RenderPatchFactory rpf, int num){
        RenderPatch[][] ret = new RenderPatch[num*num*num][];
        int[] textures = new int[6];

        for (int x = 0; x < num; x++) {
            for (int y = 0; y < num; y++) {
                for (int z = 0; z < num; z++) {
                    textures[0] = (num-z-1) * num + x;
                    textures[1] = z * num + x;
                    textures[2] = (num-y-1) * num + z;
                    textures[3] = (num-y-1) * num + (num-z-1);
                    textures[4] = (num-y-1) * num + (num-x-1);
                    textures[5] = (num-y-1) * num + x;

                    ret[x*num*num + y*num + z] = getBoxFull(rpf, textures);
                }
            }
        }
        return ret;
    }
}
