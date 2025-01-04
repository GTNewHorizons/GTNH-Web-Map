package org.dynmap.hdmap.renderer.multipart;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.CustomRendererData;
import org.dynmap.renderer.CustomTextureMapper;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

import java.util.HashMap;

public class ProjectRedSurfaceWireRenderer implements MultiPartRenderer {
    int onTex, offTex;
    SimpleTexturer on, off;

    RenderPatch[][][] variants = new RenderPatch[6][][];
    @Override
    public CustomRendererData getRenderData(MapDataContext ctx, HashMap<String, Object> partData) {
        int connMap = GWM_Util.objectToInt(partData.get("connMap"), 0xFFF);

        int version = ((connMap & 0xF00) >> 8) | ((connMap & 0xF0) >> 4) | ((connMap & 0xF));

        int signal = GWM_Util.objectToInt(partData.get("signal"), 0);
        int side = GWM_Util.objectToInt(partData.get("side"), 0);

        if(side < 0)
            side = 0;
        if(side >= 6)
            side = 0;

        return new CustomRendererData(variants[side][version], null, signal == 0 ? off : on);
    }

    @Override
    public void initialize(RenderPatchFactory rpf, HashMap<String, Integer> filetoidx, HashMap<String, String> data) {
        onTex = TexturePack.parseTextureIndex(filetoidx, data.get("on"));
        offTex = TexturePack.parseTextureIndex(filetoidx, data.get("off"));

        on = new SimpleTexturer(onTex);
        off = new SimpleTexturer(offTex);

        initModels(rpf);
    }

    void initModels(RenderPatchFactory rpf){
        RenderPatch[][] downVariants = new RenderPatch[16][];

        int wlow = 7, whigh = 9, start = 4, end = 12;

        RenderPatch[] a = CustomRenderer.getBoxSingleTextureInt(rpf, wlow, whigh,0,2, wlow, 16,0,false);
        RenderPatch[] b = CustomRenderer.getBoxSingleTextureInt(rpf, 0, whigh,0,2, wlow, whigh,0,false);
        RenderPatch[] c = CustomRenderer.getBoxSingleTextureInt(rpf, wlow, whigh,0,2, 0, whigh,0,false);
        RenderPatch[] d = CustomRenderer.getBoxSingleTextureInt(rpf, wlow, 16,0,2, wlow, whigh,0,false);

        downVariants[0] = CustomRenderer.getBoxSingleTextureInt(rpf, start, end,0,2, wlow, whigh,0,false);
        downVariants[1] = CustomRenderer.getBoxSingleTextureInt(rpf, wlow, whigh,0,2, start, 16,0,false);
        downVariants[2] = CustomRenderer.getBoxSingleTextureInt(rpf, 0, end,0,2, wlow, whigh,0,false);
        downVariants[4] = CustomRenderer.getBoxSingleTextureInt(rpf, wlow, whigh,0,2, 0, end,0,false);
        downVariants[8] = CustomRenderer.getBoxSingleTextureInt(rpf, start, 16,0,2, wlow, whigh,0,false);

        downVariants[5] = CustomRenderer.getBoxSingleTextureInt(rpf, wlow, whigh,0,2, 0, 16,0,false);
        downVariants[10] = CustomRenderer.getBoxSingleTextureInt(rpf, 0, 16,0,2, wlow, whigh,0,false);

        downVariants[3] = CustomRenderer.combineMultiple(a, b);
        downVariants[6] = CustomRenderer.combineMultiple(b, c);
        downVariants[9] = CustomRenderer.combineMultiple(a, d);
        downVariants[12] = CustomRenderer.combineMultiple(c, d);

        downVariants[7] = CustomRenderer.combineMultiple(downVariants[5], b);
        downVariants[11] = CustomRenderer.combineMultiple(downVariants[10], a);
        downVariants[13] = CustomRenderer.combineMultiple(downVariants[5], d);
        downVariants[14] = CustomRenderer.combineMultiple(downVariants[10], c);

        downVariants[15] = CustomRenderer.combineMultiple(downVariants[5], downVariants[10]);

        variants[0] = downVariants;
        for(int s = 1; s < 6; s++){
            variants[s] = new RenderPatch[16][];
            ForgeDirection dir = ForgeDirection.getOrientation(s);

            for(int i =0 ; i < 16; i++){
                switch (dir){
                    case DOWN:
                        break;
                    case UP:
                        variants[s][i] = CustomRenderer.getRotatedSet(rpf, CustomRenderer.getRotatedSet(rpf, downVariants[i], 180, 0, 0),0,180,0);
                        break;
                    case NORTH:
                        variants[s][i] = CustomRenderer.getRotatedSet(rpf, CustomRenderer.getRotatedSet(rpf, downVariants[i], 270, 0, 0), 0, 0, 180);
                        break;
                    case SOUTH:
                        variants[s][i] =CustomRenderer.getRotatedSet(rpf, CustomRenderer.getRotatedSet(rpf, downVariants[i], 90, 0, 0), 0, 0, 0);
                        break;
                    case WEST:
                        variants[s][i] = CustomRenderer.getRotatedSet(rpf,CustomRenderer.getRotatedSet(rpf, downVariants[i], 0, 0, 90), 90,0,0);
                        break;
                    case EAST:
                        variants[s][i] = CustomRenderer.getRotatedSet(rpf,CustomRenderer.getRotatedSet(rpf, downVariants[i], 0, 0, 270), 90,0,0);
                        break;
                    case UNKNOWN:
                        break;
                }
            }
        }
    }

    class SimpleTexturer implements CustomTextureMapper {
        public SimpleTexturer(int tex){
            layers = new int[]{tex};
        }
        int[] layers;
        @Override
        public int[] getTextureLayersForPatchId(int patchId) {
            return layers;
        }
    }
}
