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
    RenderPatch[][] models;
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

                models = new RenderPatch[8][];

                models[0] = getRotatedSet(rpf, model, 3, 10,0);
                models[1] = getRotatedSet(rpf, model, -3, 8,1);
                models[2] = getRotatedSet(rpf, model, 3, 15,0);
                models[3] = getRotatedSet(rpf, model, 2, -14,3);
                models[4] = getRotatedSet(rpf, model, 0, 7,0);
                models[5] = getRotatedSet(rpf, model, 1, -9,-2);
                models[6] = getRotatedSet(rpf, model, -2, 9,0);
                models[7] = getRotatedSet(rpf, model, 3, 12,-2);
            }
        }
        if(model == null) {
            double off = (Math.sqrt(2)-1) / 2;
            double high = 1.0 - off;
            double low = off;
            model = combineMultiple(
                    rpf.getPatch(low, 0, low, high, 0, high, low, 1, low, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 6),
                    rpf.getPatch(low, 0, high, high, 0, low, low, 1, high, 0, 1, 0, 1, RenderPatchFactory.SideVisible.BOTH, 6)
            );
        }


        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        if(models != null){
            int x = mapDataCtx.getX();
            int y = mapDataCtx.getY();
            int z = mapDataCtx.getZ();

            int posHashThing = x * x + y * x + x * z + z * -z + y * z + y * -y;
            return models[posHashThing & 7];
        }
        return model;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        if(plain)
            return new CustomRendererData(getRenderPatchList(mapDataCtx), null, null);

        Object objName = mapDataCtx.getBlockTileEntityField("subTileName");

        int tex = -1;
        if(objName instanceof String)
            tex = BotaniaSupport.getTextureForFlower((String)objName);

        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, tex != -1 ? new TextureSelector(tex) : null);
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
