package org.dynmap.hdmap.renderer;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.Map;

public class CocoaBeanRenderer extends CustomRenderer {
    RenderPatch[][] patches = new RenderPatch[16][];

    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        patches[0] = makeBox(rpf, 4, 5);
        patches[4] = makeBox(rpf, 6, 7);
        patches[8] = patches[12] = makeBox(rpf, 7, 8);

        for(int i = 1; i < 4; i++){
            patches[i] = getRotatedSet(rpf, patches[0], 0, 90*i, 0);
            patches[4+i] = getRotatedSet(rpf, patches[4], 0, 90*i, 0);
            patches[8+i] = getRotatedSet(rpf, patches[8], 0, 90*i, 0);

            patches[12+i] = patches[8+i];
        }

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    RenderPatch[] makeBox(RenderPatchFactory rpf, double w, double h){

        double xmin = i2d(8 - w / 2);
        double xmax = i2d(8 + w / 2);
        double zmin = i2d(1);
        double zmax = i2d(1 + w);
        double ymin = i2d(12 - h);
        double ymax = i2d(12);

        double sideLeft = i2d(15 - w);
        double sideRight = i2d(15);
        double sideTop = i2d(12);
        double sideBottom = i2d(12-h);

        double topBottomTop = i2d(16);
        double topBottomBottom = i2d(16-w);
        double topBottomLeft = i2d(0);
        double topBottomRight = i2d(w);

        RenderPatch[] patches = new RenderPatch[]{
                // bottom
                rpf.getPatchExplTexCoords(
                        xmin, ymin, zmin, topBottomLeft, topBottomBottom,
                        xmax, ymin, zmin, topBottomRight, topBottomBottom,
                        xmin, ymin, zmin, topBottomLeft, topBottomTop,
                        100, RenderPatchFactory.SideVisible.TOP, 0),

                // top
                rpf.getPatchExplTexCoords(
                        xmin, ymax, zmax, topBottomLeft, topBottomBottom,
                        xmax, ymax, zmax, topBottomRight, topBottomBottom,
                        xmin, ymax, zmin, topBottomLeft, topBottomTop,
                        100, RenderPatchFactory.SideVisible.TOP, 0),

                // south
                rpf.getPatchExplTexCoords(
                        xmin, ymin, zmax, sideLeft, sideBottom,
                        xmax, ymin, zmax, sideRight, sideBottom,
                        xmin, ymax, zmax, sideLeft, sideTop,
                        100, RenderPatchFactory.SideVisible.TOP, 0),

                // east
                rpf.getPatchExplTexCoords(
                        xmax, ymin, zmax, sideLeft, sideBottom,
                        xmax, ymin, zmin, sideRight, sideBottom,
                        xmax, ymax, zmax, sideLeft, sideTop,
                        100, RenderPatchFactory.SideVisible.TOP, 0),

                // north
                rpf.getPatchExplTexCoords(
                        xmax, ymin, zmin, sideLeft, sideBottom,
                        xmin, ymin, zmin, sideRight, sideBottom,
                        xmax, ymax, zmin, sideLeft, sideTop,
                        100, RenderPatchFactory.SideVisible.TOP, 0),

                // west
                rpf.getPatchExplTexCoords(
                        xmin, ymin, zmin, sideLeft, sideBottom,
                        xmin, ymin, zmax, sideRight, sideBottom,
                        xmin, ymax, zmin, sideLeft, sideTop,
                        100, RenderPatchFactory.SideVisible.TOP, 0),

                // stalk
                rpf.getPatchExplTexCoords(
                        i2d(8), i2d(12), i2d(4), i2d(12), i2d(12),
                        i2d(8), i2d(12), i2d(0), i2d(16), i2d(12),
                        i2d(8), i2d(16), i2d(4), i2d(12), i2d(16),
                        100, RenderPatchFactory.SideVisible.BOTH, 0)
        };

        return getRotatedSet(rpf, patches, 0, 180, 0);
    }
    double i2d(double i){
        return i / 16.0;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return patches[mapDataCtx.getBlockData() & 0xF];
    }
}
