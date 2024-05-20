package org.dynmap.modsupport.forestry;

import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;

import java.util.HashMap;

public class ForestrySupport {
    public static class LeavesEntry extends CustomColorMultiplier implements CustomTextureMapper {

        int[] stack;
        int colorMultiplier;

        public LeavesEntry(int leavesTex, int color) {
            stack = new int[]{leavesTex};
            colorMultiplier = color;
        }

        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return stack;
        }

        @Override
        public int getColorMultiplier(MapDataContext mapDataCtx) {
            return colorMultiplier;
        }
    }

    static HashMap<String, LeavesEntry> leavesPerTreeName = new HashMap<>();

    public static void registerTreeType(String treeName, int saplingTex, int leavesTex, int color){
        LeavesEntry leaves = new LeavesEntry(leavesTex, color);

        leavesPerTreeName.put(treeName, leaves);
        leavesPerTreeName.put("forestry."+treeName, leaves);
    }

    public static LeavesEntry getLeavesEntryForTreeName(String treeName){
        return leavesPerTreeName.get(treeName);
    }
}
