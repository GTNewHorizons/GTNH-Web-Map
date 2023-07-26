package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class ProjectRedLampRenderer extends CustomRenderer {

    RenderPatch[] light, dark;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        dark = CustomRenderer.getBoxSingleTexture(rpf, 0,1,0,1,0,1,0,false);
        light = CustomRenderer.getBoxSingleTexture(rpf, 0,1,0,1,0,1,6,false);
        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        Object inv = mapDataCtx.getBlockTileEntityField("inv");
        Object pow = mapDataCtx.getBlockTileEntityField("pow");

        boolean isInverted = (inv instanceof Byte) && 1 == (Byte)inv;
        boolean isPowered = (pow instanceof Byte) && 1 == (Byte)pow;

        if((isInverted && !isPowered) || (!isInverted && isPowered))
            return light;
        return dark;
    }

    static String[] nbtFieldsNeeded = {"inv", "pow"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
