package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.botania.BotaniaSupport;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class BotaniaFlowerRenderer extends CustomRenderer {

    RenderPatch[] model;
    boolean plain;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        String blockType = custparm.get("type");

        plain = custparm.get("plain") != null && custparm.get("plain").equals("true");

        if(blockType != null){
            if (blockType.equals("floating")) {
                double flowerMinXZ = 4 / 16.0;
                double flowerMaxXZ = 1 - flowerMinXZ;
                double flowerMinY = 5 / 16.0;
                double flowerMaxY = flowerMinY + 8 / 16.0;

                model = combineMultiple(
                        getBoxMultiTextureInt(rpf, 3, 13, 3, 5, 3, 13, 0),
                        combineMultiple(
                                rpf.getPatch(flowerMinXZ, flowerMinY, flowerMinXZ, flowerMaxXZ, flowerMinY, flowerMaxXZ, flowerMinXZ, flowerMaxY, flowerMinXZ, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 6),
                                rpf.getPatch(flowerMinXZ, flowerMinY, flowerMaxXZ, flowerMaxXZ, flowerMinY, flowerMinXZ, flowerMinXZ, flowerMaxY, flowerMaxXZ, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 6)
                        )
                );

            }
        }
        if(model == null) {
            model = combineMultiple(
                    rpf.getPatch(0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 6),
                    rpf.getPatch(0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 6)
            );
        }


        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return model;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        if(plain)
            return new CustomRendererData(model, null, null);

        Object objName = mapDataCtx.getBlockTileEntityField("subTileName");

        int tex = -1;
        if(objName instanceof String)
            tex = BotaniaSupport.getTextureForFlower((String)objName);

        return new CustomRendererData(model, null, tex != -1 ? new TextureSelector(tex) : null);
    }
    static String[] nbtFieldsNeeded = {"subTileName"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
    static class TextureSelector implements CustomTextureMapper {

        private final int[] tex;

        public TextureSelector(int tex) {

            this.tex = new int[]{ tex };
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            if(patchId >= 6)
                return tex;
            return null;
        }
    }
}
