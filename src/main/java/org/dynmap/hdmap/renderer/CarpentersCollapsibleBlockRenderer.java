package org.dynmap.hdmap.renderer;

import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;

public class CarpentersCollapsibleBlockRenderer extends CarpentersBlocksRenderer {
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {

        RenderPatchFactory rpf = mapDataCtx.getPatchFactory();

        Object objMetaData = mapDataCtx.getBlockTileEntityField("cbMetadata");
        int metaData = GWM_Util.objectToInt(objMetaData, 0);

        int base = metaData & 7;

        int se = (metaData >> 3) & 31; // SE
        int ne = (metaData >> 8) & 31; // NE
        int sw = (metaData >> 13) & 31; // SW
        int nw = (metaData >> 18) & 31; // NW

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        if(base == 1){
            // Bottom plate
            list.add(rpf.getPatch(0,0,0,1,0,0,0,0,1,0,1,0,1, RenderPatchFactory.SideVisible.TOP, 0));

            addSide(list, rpf, 180, sw, se);
            addSide(list, rpf, 90, se, ne);
            addSide(list, rpf, 0, ne, nw);
            addSide(list, rpf, 270, nw, sw);

            list.add(rpf.getTriangleAutoTexCoords(0, sw / 16.0, 1, 1, se/16.0, 1, 0, nw / 16.0, 0, 1, RenderPatchFactory.SideVisible.TOP, 0));
            list.add(rpf.getTriangleAutoTexCoords(1, se / 16.0, 1, 0, nw/16.0, 0, 1, ne / 16.0, 0, 1, RenderPatchFactory.SideVisible.BOTTOM, 0));

            return list.toArray(new RenderPatch[list.size()]);
        } else if(base == 0){
            // Top plate
            list.add(rpf.getPatch(0,1,0,1,1,0,0,1,1,0,1,0,1, RenderPatchFactory.SideVisible.BOTTOM, 0));

            addSideDown(list, rpf, 180, sw, se);
            addSideDown(list, rpf, 90, se, ne);
            addSideDown(list, rpf, 0, ne, nw);
            addSideDown(list, rpf, 270, nw, sw);

            list.add(rpf.getTriangleAutoTexCoords(0, 1-sw / 16.0, 1, 1, 1-se/16.0, 1, 0, 1-nw / 16.0, 0, 1, RenderPatchFactory.SideVisible.BOTTOM, 0));
            list.add(rpf.getTriangleAutoTexCoords(1, 1-se / 16.0, 1, 0, 1-nw/16.0, 0, 1, 1-ne / 16.0, 0, 1, RenderPatchFactory.SideVisible.TOP, 0));

            return list.toArray(new RenderPatch[list.size()]);
        }

        return fullBlock;
    }
    void addSide(ArrayList<RenderPatch> list, RenderPatchFactory rpf, int rot, int a, int b){

        if(a == 0 && b == 0)
            return;

        if(a == b && a > 0) {
            list.add(rpf.getRotatedPatch(rpf.getQuadAutoTexCoords(0, 0, 0, 1, 0, 0, 0, a / 16.0, 0, RenderPatchFactory.SideVisible.BOTTOM, 0), 0, rot, 0, 0));
        } else {
            double min = (a < b ? a : b) / 16.0;
            double max = (a < b ? b : a) / 16.0;

            if(min > 0)
                list.add(rpf.getRotatedPatch(rpf.getQuadAutoTexCoords(0, 0, 0, 1, 0, 0, 0, min, 0, RenderPatchFactory.SideVisible.BOTTOM, 0), 0, rot, 0, 0));

            if(a < b)
                list.add(rpf.getRotatedPatch(rpf.getTriangleAutoTexCoords(0, min, 0, 1, min, 0, 0, max, 0, 1, RenderPatchFactory.SideVisible.BOTTOM, 0), 0, rot, 0, 0));
            else
                list.add(rpf.getRotatedPatch(rpf.getTriangleAutoTexCoords(0, min, 0, 1, max, 0, 1, min, 0, 1, RenderPatchFactory.SideVisible.TOP, 0), 0, rot, 0, 0));
        }
    }
    void addSideDown(ArrayList<RenderPatch> list, RenderPatchFactory rpf, int rot, int a, int b){

        if(a == 0 && b == 0)
            return;

        if(a == b && a > 0) {
            list.add(rpf.getRotatedPatch(rpf.getQuadAutoTexCoords(0, 1, 0, 1, 1, 0, 0, 1 - a / 16.0, 0, RenderPatchFactory.SideVisible.TOP, 0), 0, rot, 0, 0));
        } else {
            double min = (a < b ? a : b) / 16.0;
            double max = (a < b ? b : a) / 16.0;

            if(min > 0)
                list.add(rpf.getRotatedPatch(rpf.getQuadAutoTexCoords(0, 1, 0, 1, 1, 0, 0, 1-min, 0, RenderPatchFactory.SideVisible.TOP, 0), 0, rot, 0, 0));

            if(a < b)
                list.add(rpf.getRotatedPatch(rpf.getTriangleAutoTexCoords(0, 1-min, 0, 1, 1-min, 0, 0, 1-max, 0, 1, RenderPatchFactory.SideVisible.TOP, 0), 0, rot, 0, 0));
            else
                list.add(rpf.getRotatedPatch(rpf.getTriangleAutoTexCoords(0, 1-min, 0, 1, 1-max, 0, 1, 1-min, 0, 1, RenderPatchFactory.SideVisible.BOTTOM, 0), 0, rot, 0, 0));
        }
    }
}
