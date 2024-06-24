package org.dynmap.utils;

import java.util.HashMap;
import java.util.Map;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class PatchDefinitionFactory implements RenderPatchFactory {
    private HashMap<PatchDefinition,PatchDefinition> patches = new HashMap<PatchDefinition,PatchDefinition>();
    private Object lock = new Object();
    private PatchDefinition lookup = new PatchDefinition();
    private Map<String, PatchDefinition> namemap = null;

    public PatchDefinitionFactory() {
        
    }

    public void setPatchNameMape(Map<String, PatchDefinition> nmap) {
        namemap = nmap;
    }
    
    @Override
    public RenderPatch getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, SideVisible sidevis,
            int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, umin, umax, vmin, vmax, 100.0, sidevis, textureids);
    }

    @Override
    public RenderPatch getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv,
            double uplusvmax, SideVisible sidevis, int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, 0.0, 1.0, 0.0, 1.0, uplusvmax, sidevis, textureids);
    }

    public PatchDefinition getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, double uplusvmax, SideVisible sidevis,
            int textureids) {
        synchronized(lock) {
            lookup.update(x0, y0, z0, xu, yu, zu, xv, yv, zv, umin,
                    umax, vmin, vmax, uplusvmax, sidevis, textureids);
            if(lookup.validate() == false)
                return null;
            PatchDefinition pd2 = patches.get(lookup);  /* See if in cache already */
            if(pd2 == null) {
                PatchDefinition pd = new PatchDefinition(lookup);
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }

    }
    @Override
    public RenderPatch getRotatedPatch(RenderPatch patch, int xrot, int yrot,
            int zrot, int textureindex) {
        return getPatch((PatchDefinition)patch, xrot, yrot, zrot, textureindex);
    }
    
    public PatchDefinition getPatch(PatchDefinition patch, int xrot, int yrot,
            int zrot, int textureindex) {
        PatchDefinition pd = new PatchDefinition(patch, xrot, yrot, zrot, textureindex);
        if(pd.validate() == false)
            return null;
        synchronized(lock) {
            PatchDefinition pd2 = patches.get(pd);  /* See if in cache already */
            if(pd2 == null) {
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }
    }

    @Override
    public RenderPatch getRotatedPatchAutoTexCoords(RenderPatch patch, int xrot, int yrot, int zrot, int textureindex) {
        return getPatchAutoTexCoords((PatchDefinition)patch, xrot, yrot, zrot, textureindex);
    }

    public PatchDefinition getPatchAutoTexCoords(PatchDefinition patch, int xrot, int yrot, int zrot, int textureindex) {
        PatchDefinition pd = new PatchDefinition(patch, xrot, yrot, zrot, textureindex);
        if(pd.validate() == false)
            return null;

        autoTexCoords(pd);

        return pd;
    }
    /**
     * Get named patch with given attributes.  Name can encode rotation and patch index info
     * "name" - simple name
     * "name@rot" - name with rotation around Y
     * "name@x/y/z" - name with rotation around x, then y, then z axis
     * "name#patch" - name with explicit patch index
     * 
     * @param name - name of patch (must be defined in same config file as custom renderer): supports name@yrot, name@xrot/yrot/zrot
     * @param textureidx - texture index to be used for patch, if not provided in name encoding (#patchid suffix)
     * @return patch requested
     */
    @Override
    public RenderPatch getNamedPatch(final String name, int textureidx) {
        return getPatchByName(name, textureidx);
    }
    public PatchDefinition getPatchByName(final String name, int textureidx) {
        PatchDefinition pd = null;
        if(namemap != null) {
            pd = namemap.get(name);
            if((pd != null) && (textureidx != pd.textureindex)) {
                pd = null;
            }
            if(pd == null) {
                String patchid = name;
                int txt_idx = -1;
                int off = patchid.lastIndexOf('#');
                if(off > 0) {
                    try {
                        txt_idx = Integer.valueOf(patchid.substring(off+1));
                    } catch (NumberFormatException nfx) {
                        return null;
                    }
                    patchid = patchid.substring(0,  off);
                }
                int rotx = 0, roty = 0, rotz = 0;
                /* See if ID@rotation */
                off = patchid.indexOf('@');
                if(off > 0) {
                    String[] rv = patchid.substring(off+1).split("/");
                    if(rv.length == 1) {
                        roty = Integer.parseInt(rv[0]);
                    }
                    else if(rv.length == 2) {
                        rotx = Integer.parseInt(rv[0]);
                        roty = Integer.parseInt(rv[1]);
                    }
                    else if(rv.length == 3) {
                        rotx = Integer.parseInt(rv[0]);
                        roty = Integer.parseInt(rv[1]);
                        rotz = Integer.parseInt(rv[2]);
                    }
                    patchid = patchid.substring(0, off);
                }
                pd = namemap.get(patchid);
                if(pd == null) {
                    return null;
                }
                else if(txt_idx >= 0) { /* If set texture index */
                    pd = getPatch(pd, rotx,  roty,  rotz, txt_idx);
                }
                else {
                    pd = getPatch(pd, rotx,  roty,  rotz, textureidx);
                }
                if(pd != null) {
                    namemap.put(name, pd);
                }
            }
        }
        return pd;
    }

    @Override
    public int getTextureIndexFromMap(String id, int key) {
        return TexturePack.getTextureIndexFromTextureMap(id, key);
    }

    @Override
    public int getTextureCountFromMap(String id) {
        return TexturePack.getTextureMapLength(id);
    }

    @Override
    public RenderPatch getTriangleExplTexCoords(double x0, double y0, double z0, double tu0, double tv0, double x1, double y1, double z1, double tu1, double tv1, double x2, double y2, double z2, double tu2, double tv2, SideVisible sidevis, int textureidx) {
        PatchDefinition ret = new PatchDefinition();
        ret.update(x0, y0, z0, x1, y1, z1, x2, y2, z2,0,1,0,1,1, sidevis, textureidx);
        ret.explicitTexCoords = new double[]{tu0, tv0, tu1, tv1, tu2, tv2};
        return ret;
    }

    @Override
    public RenderPatch getPatchExplTexCoords(double x0, double y0, double z0, double tu0, double tv0, double x1, double y1, double z1, double tu1, double tv1, double x2, double y2, double z2, double tu2, double tv2, double uplusvmax, SideVisible sidevis, int textureidx) {
        PatchDefinition ret = new PatchDefinition();
        ret.update(x0, y0, z0, x1, y1, z1, x2, y2, z2,0,1,0,1,uplusvmax, sidevis, textureidx);
        ret.explicitTexCoords = new double[]{tu0, tv0, tu1, tv1, tu2, tv2};
        return ret;
    }

    @Override
    public RenderPatch getTriangleAutoTexCoords(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, SideVisible sidevis, int textureidx) {
        return getTriangleAutoTexCoords(x0, y0, z0, x1, y1, z1, x2, y2, z2, 1, sidevis, textureidx);
    }
    @Override
    public RenderPatch getTriangleAutoTexCoords(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double uplusvmax, SideVisible sidevis, int textureidx) {
        PatchDefinition ret = new PatchDefinition();
        ret.update(x0, y0, z0, x1, y1, z1, x2, y2, z2,0,1,0,1,uplusvmax,sidevis, textureidx);

        autoTexCoords(ret);
        return ret;
    }

    @Override
    public RenderPatch getQuadAutoTexCoords(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, SideVisible sidevis, int textureidx){
        PatchDefinition ret = new PatchDefinition();
        ret.update(x0, y0, z0, x1, y1, z1, x2, y2, z2,0,1,0,1,100,sidevis, textureidx);

        // TODO: This can probably be optimized to move points and use umin/umax and vmin/vmax instead of explicit texcoords
        autoTexCoords(ret);

        return ret;
    }

    private static void autoTexCoords(PatchDefinition ret) {
        switch(ret.step){
            case X_PLUS:
                ret.explicitTexCoords = new double[]{1 - ret.z0, ret.y0, 1 - ret.zu, ret.yu, 1 - ret.zv, ret.yv};
                break;
            case Y_PLUS:
                ret.explicitTexCoords = new double[]{ret.x0, 1 - ret.z0, ret.xu, 1 - ret.zu, ret.xv, 1 - ret.zv};
                break;
            case Z_PLUS:
                ret.explicitTexCoords = new double[]{1 - ret.x0, ret.y0, 1 - ret.xu, ret.yu, 1 - ret.xv, ret.yv};
                break;
            case X_MINUS:
                ret.explicitTexCoords = new double[]{ret.z0, ret.y0, ret.zu, ret.yu, ret.zv, ret.yv};
                break;
            case Y_MINUS:
                ret.explicitTexCoords = new double[]{ret.x0, ret.z0, ret.xu, ret.zu, ret.xv, ret.zv};
                break;
            case Z_MINUS:
                ret.explicitTexCoords = new double[]{ret.x0, ret.y0, ret.xu, ret.yu, ret.xv, ret.yv};
                break;
        }
    }
}
