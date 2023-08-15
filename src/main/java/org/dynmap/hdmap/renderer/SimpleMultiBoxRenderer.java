package org.dynmap.hdmap.renderer;

import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.ArrayList;
import java.util.Map;

public class SimpleMultiBoxRenderer extends CustomRenderer {
    RenderPatch[] model;
    private static final int patchlist[] = { 0, 1, 4, 5, 2, 3 };
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String, String> custparm) {

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        for(int i = 0; i < 1000; i++){
            String data = custparm.get("box"+i);

            if(data == null)
                break;

            String[] parts = data.split(":");

            if(parts.length != 5) {
                Log.severe("Error initializing SimpleMultiBoxBlockRenderer for block " + blkid);
                break;
            }

            boolean intMode = false;

            if(parts[0].equals("i"))
                intMode = true;

            double xmin=0, xmax=1, ymin=0, ymax=1, zmin=0, zmax=1;

            String[] xparts = parts[1].split("-");
            String[] yparts = parts[2].split("-");
            String[] zparts = parts[3].split("-");

            if(intMode){
                xmin = Integer.valueOf(xparts[0]) / 16.0;
                xmax = Integer.valueOf(xparts[1]) / 16.0;
                ymin = Integer.valueOf(yparts[0]) / 16.0;
                ymax = Integer.valueOf(yparts[1]) / 16.0;
                zmin = Integer.valueOf(zparts[0]) / 16.0;
                zmax = Integer.valueOf(zparts[1]) / 16.0;
            } else {
                xmin = Double.valueOf(xparts[0]);
                xmax = Double.valueOf(xparts[1]);
                ymin = Double.valueOf(yparts[0]);
                ymax = Double.valueOf(yparts[1]);
                zmin = Double.valueOf(zparts[0]);
                zmax = Double.valueOf(zparts[1]);
            }
            int[] patchIds = new int[6];
            if(parts[4].startsWith("b")){
                int off = Integer.parseInt(parts[4].substring(1));
                for(int j =0; j < 6; j++) {
                    patchIds[j] = patchlist[j] + off;
                }
            } else {
                int fixed = Integer.parseInt(parts[4]);
                for(int j =0; j < 6; j++) {
                    patchIds[j] = fixed;
                }
            }
            addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchIds);
        }

        model = list.toArray(new RenderPatch[list.size()]);

        return super.initializeRenderer(rpf, blkid, blockdatamask, custparm);
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx) {
        return model;
    }
}
