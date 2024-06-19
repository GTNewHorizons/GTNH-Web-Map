package org.dynmap.modsupport;

import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.MapDataContext;

public class SimpleColorMultiplier extends CustomColorMultiplier {
    public SimpleColorMultiplier(int c){
        color = c;
    }
    int color;
    @Override
    public int getColorMultiplier(MapDataContext mapDataCtx) {
        return color;
    }
}
