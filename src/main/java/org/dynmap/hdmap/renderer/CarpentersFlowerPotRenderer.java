package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.*;

import java.util.HashMap;
import java.util.Map;

public class CarpentersFlowerPotRenderer  extends CarpentersBlocksRenderer {

    HashMap<String, RenderPatch[]> potsByDesign = new HashMap<>();
    RenderPatch[] base;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
        String[] list = {
                "",
                "black",
                "blue",
                "brown",
                "cyan",
                "gray",
                "green",
                "light_blue",
                "light_gray",
                "lime",
                "magenta",
                "orange",
                "original",
                "pink",
                "purple",
                "rainbow",
                "red",
                "white",
                "yellow"
        };

        for(int i = 0; i < list.length; i++){
            RenderPatch[] pot = getFlowerPot(rpf, i);
            if(i == 0)
                base = pot;
            else
                potsByDesign.put(list[i], pot);
        }

        return true;
    }
    private RenderPatch[] getFlowerPot(RenderPatchFactory rpf, int basePatch) {
        double soilMin = 6.0/16;
        double soilMax = 11.0/16;
        double soilY = 3.0/16;

        double flowerMin = (1 - 13.0/16.0/1.41) / 2;
        double flowerMax = 1.0 - flowerMin;

        return combineMultiple(
                CustomRenderer.getBoxSingleTextureInt(rpf, 5, 11, 0, 1, 5, 11, basePatch, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 5, 6, 1, 6, 5, 11, basePatch, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 10, 11, 1, 6, 5, 11, basePatch, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 5, 11, 1, 6, 5, 6, basePatch, false),
                CustomRenderer.getBoxSingleTextureInt(rpf, 5, 11, 1, 6, 10, 11, basePatch, false),
                combineMultiple(
                        rpf.getPatch(flowerMin, soilY, flowerMin, flowerMax, soilY, flowerMax, flowerMin, 1, flowerMin, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 22 ),
                        rpf.getPatch(flowerMin, soilY, flowerMax, flowerMax, soilY, flowerMin, flowerMin, 1, flowerMax, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 22 ),
                        rpf.getPatch(soilMin, soilY, soilMin, soilMax, soilY, soilMin, soilMin, soilY, soilMax, 0, 1, 0, 1, RenderPatchFactory.SideVisible.TOP, 23 )
                        )
        );
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        Object design = mapDataCtx.getBlockTileEntityField("cbDesign");

        RenderPatch[] ret = null;

        if(design instanceof String)
            ret = potsByDesign.get((String)design);

        if(ret == null)
            return base;

        return ret;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, new TextureSelector(mapDataCtx, 24));
    }

    static String[] nbtFieldsNeeded = {"cbDesign", "cbAttrList"};
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
