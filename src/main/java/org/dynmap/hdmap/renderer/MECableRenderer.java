package org.dynmap.hdmap.renderer;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class MECableRenderer extends PipeRendererBase {
    RenderPatch[][] smallPipes;
    double pipeRadius = 1.0/16;
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {
        if (!super.initializeRenderer(rpf, blkid, blockdatamask, custparm))
            return false;

        smallPipes = generateSingleSize(rpf, pipeRadius, pipeRadius, 0, 0);
        return true;
    }


    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        int version = 0;

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {

            Object def = mapDataCtx.getBlockTileEntityField("def:" + dir.ordinal());
            Object extra = mapDataCtx.getBlockTileEntityField("extra:" + dir.ordinal());

            if (extra == null && def == null) {
                int id = mapDataCtx.getBlockTypeIDAt(dir.offsetX, dir.offsetY, dir.offsetZ);
                ForgeDirection opposite = dir.getOpposite();
                Object otherExtra = mapDataCtx.getBlockTileEntityFieldAt("extra:" + opposite.ordinal(), dir.offsetX, dir.offsetY, dir.offsetZ);
                if (id == mapDataCtx.getBlockTypeID() && otherExtra == null)
                    version |= dir.flag;
            } else {
                double max = 0.85;
                double min = 0.15;

                double pipePositive = 0.5 + pipeRadius;
                double pipeNegative = 0.5 - pipeRadius;


                switch (dir) {
                    case DOWN:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, min, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, 0, min, min, max, new int[]{1, 2, 3, 4, 5, 6});
                        break;
                    case UP:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, pipePositive, max, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, max, 1, min, max, new int[]{2, 1, 3, 4, 5, 6});
                        break;
                    case NORTH:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, pipeNegative, pipePositive, min, pipePositive, new int[]{0, 0, 0, 0, 0, 0});

                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, min, max, 0, min, new int[]{3, 4, 5, 6, 1, 2});
                        break;
                    case SOUTH:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipeNegative, pipePositive, pipeNegative, pipePositive, pipePositive, max, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, max, min, max, max, 1, new int[]{3, 4, 5, 6, 2, 1});
                        break;
                    case WEST:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, min, pipePositive, pipeNegative, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, 0, min, min, max, min, max, new int[]{3, 4, 1, 2, 5, 6});
                        break;
                    case EAST:
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, pipePositive, max, pipeNegative, pipePositive, pipeNegative, pipePositive, new int[]{0, 0, 0, 0, 0, 0});
                        CustomRenderer.addBox(mapDataCtx.getPatchFactory(), list, max, 1, min, max, min, max, new int[]{3, 4, 2, 1, 5, 6});
                        break;
                    case UNKNOWN:
                        break;
                }

            }
        }
        if (!list.isEmpty()) {
            for (RenderPatch rp : smallPipes[version]) {
                list.add(rp);
            }
            return list.toArray(new RenderPatch[list.size()]);
        }

        return smallPipes[version];
    }


    static String[] nbtFieldsNeeded = {"def:0", "def:1", "def:2", "def:3", "def:4", "def:5", "def:6",
            "extra:0", "extra:1", "extra:2", "extra:3", "extra:4", "extra:5", "extra:6"};

    @Override
    public String[] getTileEntityFieldsNeeded() {
        return nbtFieldsNeeded;
    }
}
