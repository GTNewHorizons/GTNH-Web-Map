package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EnderIOConduitRenderer extends PipeRendererBase {
    RenderPatch[][] smallPipes;
    double smallPipeRadius = 1.5 / 16;
    private RenderPatch[] fullBlock;
    int[] allZeroPatchIds = new int[]{0,0,0,0,0,0 };

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        smallPipes = generateSingleSize(rpf, smallPipeRadius, smallPipeRadius + 1.0/16, 0, 0);

        fullBlock = getFullBlock(rpf, 0);

        return true;
    }

    @Override
    public CustomRendererData getRenderData(MapDataContext mapDataCtx) {

        Object conduits = mapDataCtx.getBlockTileEntityField("conduits");
        Object facadeId = mapDataCtx.getBlockTileEntityField("facadeId");
        Object facadeMeta = mapDataCtx.getBlockTileEntityField("facadeMeta");

        if (facadeId instanceof String && facadeMeta instanceof Integer && !"null".equals((String) facadeId)) {
            return new CustomRendererData(fullBlock, null, new FacadeTextureLookupThing((String) facadeId, ((Integer) facadeMeta).intValue()));
        }

        int version = 0;
        ArrayList<RenderPatch> list = new ArrayList<>();
        try {
            if (conduits instanceof ArrayList) {
                ArrayList alConduits = (ArrayList) conduits;
                for (Object tmp : alConduits) {
                    HashMap<String, Object> map = (HashMap<String, Object>) tmp;
                    Object conduit = map.get("conduit");

                    if (conduit instanceof HashMap) {
                        HashMap<String, Object> hmConduit = (HashMap<String, Object>) conduit;

                        int[] connections = (int[]) hmConduit.get("connections");
                        int[] extConnections = (int[]) hmConduit.get("externalConnections");

                        for (int i : connections)
                            version |= (1 << i);

                        for (int i : extConnections) {
                            version |= (1 << i);
                            ForgeDirection dir = ForgeDirection.getOrientation(i);

                            switch (dir){

                                case DOWN:
                                    CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0.15, 0.85, 0, 0.05, 0.15, 0.85, allZeroPatchIds);
                                    break;
                                case UP:
                                    CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0.15, 0.85, 0.95, 1, 0.15, 0.85, allZeroPatchIds);
                                    break;
                                case NORTH:
                                    CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0.15, 0.85, 0.15, 0.85, 0, 0.05, allZeroPatchIds);
                                    break;
                                case SOUTH:
                                    CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0.15, 0.85, 0.15, 0.85, 0.95, 1, allZeroPatchIds);
                                    break;
                                case WEST:
                                    CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0, 0.05, 0.15, 0.85, 0.15, 0.85, allZeroPatchIds);
                                    break;
                                case EAST:
                                    CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0.95, 1, 0.15, 0.85, 0.15, 0.85, allZeroPatchIds);
                                    break;
                                case UNKNOWN:
                                    break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.toString();
        }

        if(!list.isEmpty()){
            for(RenderPatch rp : smallPipes[version])
                list.add(rp);

            return new CustomRendererData(list.toArray(new RenderPatch[list.size()]), null, null);
        }

        return new CustomRendererData(smallPipes[version], null, null);
    }

    static String[] nbtFieldsNeeded = {"conduits", "facadeId", "facadeMeta"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }

    class FacadeTextureLookupThing implements CustomTextureMapper {

        int[] textures = new int[0];

        public FacadeTextureLookupThing(String blockName, int meta) {

            int blockId = GWM_Util.blockNameToId(blockName);
            TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(blockId, meta, 0);
            textures = new int[]{map.getIndexForFace(0), map.getIndexForFace(1), map.getIndexForFace(2), map.getIndexForFace(3), map.getIndexForFace(4), map.getIndexForFace(5)};

        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return new int[]{textures[patchId]};
        }
    }
}
