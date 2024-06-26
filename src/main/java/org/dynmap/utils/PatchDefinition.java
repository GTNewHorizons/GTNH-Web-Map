package org.dynmap.utils;

import org.dynmap.Log;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/* Define patch in surface-based models - origin (xyz), u-vector (xyz) v-vector (xyz), u limits and v limits */
public class PatchDefinition implements RenderPatch {
    public double x0, y0, z0;   /* Origin of patch (lower left corner of texture) */
    public double xu, yu, zu;   /* Coordinates of end of U vector (relative to origin) - corresponds to u=1.0 (lower right corner) */
    public double xv, yv, zv;   /* Coordinates of end of V vector (relative to origin) - corresponds to v=1.0 (upper left corner) */
    public double umin, umax;   /* Limits of patch - minimum and maximum u value */
    public double vmin, vmax;   /* Limits of patch - minimum and maximum v value */
    public double uplusvmax;    /* Limits of patch - max of u+v (triangle) */
    public Vector3D u, v;       /* U and V vector, relative to origin */
    public SideVisible sidevis;  /* Which side is visible */
    public int textureindex;
    public BlockStep step; /* Best approximation of orientation of surface, from top (positive determinent) */
    private int hc;
    /* Offset vector of middle of block */
    private static final Vector3D offsetCenter = new Vector3D(0.5,0.5,0.5);

    public double[] explicitTexCoords;
    
    PatchDefinition() {
        x0 = y0 = z0 = 0.0;
        xu = zu = 0.0; yu = 1.0;
        yv = zv = 0.0; xv = 1.0;
        umin = vmin = 0.0;
        umax = vmax = 1.0;
        uplusvmax = 100.0;
        u = new Vector3D();
        v = new Vector3D();
        sidevis = SideVisible.BOTH;
        textureindex = 0;
        explicitTexCoords = null;
        update();
    }
    PatchDefinition(PatchDefinition pd) {
        this.x0 = pd.x0;
        this.y0 = pd.y0;
        this.z0 = pd.z0;
        this.xu = pd.xu;
        this.yu = pd.yu;
        this.zu = pd.zu;
        this.xv = pd.xv;
        this.yv = pd.yv;
        this.zv = pd.zv;
        this.umin = pd.umin;
        this.vmin = pd.vmin;
        this.umax = pd.umax;
        this.vmax = pd.vmax;
        this.uplusvmax = pd.uplusvmax;
        this.u = new Vector3D(pd.u);
        this.v = new Vector3D(pd.v);
        this.sidevis = pd.sidevis;
        this.textureindex = pd.textureindex;
        this.step = pd.step;
        this.hc = pd.hc;
        this.explicitTexCoords = pd.explicitTexCoords;
    }
    /**
     * Construct patch, based on rotation of existing patch clockwise by N
     * 90 degree steps
     * @param orig
     * @param rotate_cnt
     */
    PatchDefinition(PatchDefinition orig, int rotatex, int rotatey, int rotatez, int textureindex) {
        Vector3D vec = new Vector3D(orig.x0, orig.y0, orig.z0);
        rotate(vec, rotatex, rotatey, rotatez); /* Rotate origin */
        x0 = vec.x; y0 = vec.y; z0 = vec.z;
        /* Rotate U */
        vec.x = orig.xu; vec.y = orig.yu; vec.z = orig.zu;
        rotate(vec, rotatex, rotatey, rotatez); /* Rotate origin */
        xu = vec.x; yu = vec.y; zu = vec.z;
        /* Rotate V */
        vec.x = orig.xv; vec.y = orig.yv; vec.z = orig.zv;
        rotate(vec, rotatex, rotatey, rotatez); /* Rotate origin */
        xv = vec.x; yv = vec.y; zv = vec.z;
        umin = orig.umin; vmin = orig.vmin;
        umax = orig.umax; vmax = orig.vmax;
        uplusvmax = orig.uplusvmax;
        sidevis = orig.sidevis;
        this.textureindex = textureindex;
        u = new Vector3D();
        v = new Vector3D();
        this.explicitTexCoords = orig.explicitTexCoords;
        update();
    }
    
    private void rotate(Vector3D vec, int xcnt, int ycnt, int zcnt) {
        vec.subtract(offsetCenter); /* Shift to center of block */
        double rot;
        double nval;

        /* Do X rotation */
        switch(xcnt){
            case 0:
                break;
            case 90:
            case -270:
                nval = vec.z;
                vec.z = -vec.y;
                vec.y = nval;
                break;
            case 180:
            case -180:
                vec.y = -vec.y;
                vec.z = -vec.z;
                break;
            case 270:
            case -90:
                nval = vec.z;
                vec.z = vec.y;
                vec.y = -nval;
                break;
            default:
                rot = Math.toRadians(xcnt);
                nval = vec.z * Math.sin(rot) + vec.y * Math.cos(rot);
                vec.z = vec.z * Math.cos(rot) - vec.y * Math.sin(rot);
                vec.y = nval;
                break;
        }

        /* Do Y rotation */
        switch(ycnt){
            case 0:
                break;
            case 90:
            case -270:
                nval = -vec.z;
                vec.z = vec.x;
                vec.x = nval;
                break;
            case 180:
            case -180:
                vec.x = -vec.x;
                vec.z = -vec.z;
                break;
            case 270:
            case -90:
                nval = vec.z;
                vec.z = -vec.x;
                vec.x = nval;
                break;
            default:
                rot = Math.toRadians(ycnt);
                nval = vec.x * Math.cos(rot) - vec.z * Math.sin(rot);
                vec.z = vec.x * Math.sin(rot) + vec.z * Math.cos(rot);
                vec.x = nval;
                break;
        }

        /* Do Z rotation */
        switch(zcnt){
            case 0:
                break;
            case 90:
            case -270:
                nval = vec.y;
                vec.y = -vec.x;
                vec.x = nval;
                break;
            case 180:
            case -180:
                vec.x = -vec.x;
                vec.y = -vec.y;
                break;
            case 270:
            case -90:
                nval = -vec.y;
                vec.y = vec.x;
                vec.x = nval;
                break;
            default:
                rot = Math.toRadians(zcnt);
                nval = vec.y * Math.sin(rot) + vec.x * Math.cos(rot);
                vec.y = vec.y * Math.cos(rot) - vec.x * Math.sin(rot);
                vec.x = nval;
                break;
        }

        vec.add(offsetCenter); /* Shoft back to corner */
    }
    public void update(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, double uplusvmax, SideVisible sidevis,
            int textureids) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.xu = xu;
        this.yu = yu;
        this.zu = zu;
        this.xv = xv;
        this.yv = yv;
        this.zv = zv;
        this.umin = umin;
        this.umax = umax;
        this.vmin = vmin;
        this.vmax = vmax;
        this.uplusvmax = uplusvmax;
        this.sidevis = sidevis;
        this.textureindex = textureids;
        update();
    }
    public void update() {
        u.x = xu - x0; u.y = yu - y0; u.z = zu - z0;
        v.x = xv - x0; v.y = yv - y0; v.z = zv - z0;
        /* Compute hash code */
        hc = (int)((Double.doubleToLongBits(x0 + xu + xv) >> 32) ^
                (Double.doubleToLongBits(y0 + yu + yv) >> 34) ^
                (Double.doubleToLongBits(z0 + yu + yv) >> 36) ^
                (Double.doubleToLongBits(umin + umax + vmin + vmax + uplusvmax) >> 38)) ^
                (sidevis.ordinal() << 8) ^ textureindex;
        /* Now compute normal of surface - U cross V */
        double crossx = (u.y*v.z) - (u.z*v.y);
        double crossy = (u.z*v.x) - (u.x*v.z);
        double crossz = (u.x*v.y) - (u.y*v.x); 
        /* Now, find the largest component of the normal (dominant direction) */
        if(Math.abs(crossx) > (Math.abs(crossy)*0.9)) { /* If X > 0.9Y */
            if(Math.abs(crossx) > Math.abs(crossz)) { /* If X > Z */
                if(crossx > 0) {
                    step = BlockStep.X_PLUS;
                }
                else {
                    step = BlockStep.X_MINUS;
                }
            }
            else { /* Else Z >= X */
                if(crossz > 0) {
                    step = BlockStep.Z_PLUS;
                }
                else {
                    step = BlockStep.Z_MINUS;
                }
            }
        }
        else { /* Else Y >= X */
            if((Math.abs(crossy)*0.9) > Math.abs(crossz)) { /* If 0.9Y > Z */
                if(crossy > 0) {
                    step = BlockStep.Y_PLUS;
                }
                else {
                    step = BlockStep.Y_MINUS;
                }
            }
            else { /* Else Z >= Y */
                if(crossz > 0) {
                    step = BlockStep.Z_PLUS;
                }
                else {
                    step = BlockStep.Z_MINUS;
                }
            }
        }        
    }
    public boolean validate() {
        boolean good = true;
        if((x0 < -1.0) || (x0 > 2.0)) {
            Log.severe("Invalid x0=" + x0);
            good = false;
        }
        if((y0 < -1.0) || (y0 > 2.0)) {
            Log.severe("Invalid y0=" + y0);
            good = false;
        }
        if((z0 < -1.0) || (z0 > 2.0)) {
            Log.severe("Invalid z0=" + z0);
            good = false;
        }
        if((xu < -1.0) || (xu > 2.0)) {
            Log.severe("Invalid xu=" + xu);
            good = false;
        }
        if((yu < -1.0) || (yu > 2.0)) {
            Log.severe("Invalid yu=" + yu);
            good = false;
        }
        if((zu < -1.0) || (zu > 2.0)) {
            Log.severe("Invalid zu=" + zu);
            good = false;
        }
        if((xv < -1.0) || (xv > 2.0)) {
            Log.severe("Invalid xv=" + xv);
            good = false;
        }
        if((yv < -1.0) || (yv > 2.0)) {
            Log.severe("Invalid yv=" + yv);
            good = false;
        }
        if((zv < -1.0) || (zv > 2.0)) {
            Log.severe("Invalid zv=" + zv);
            good = false;
        }
        if((umin < 0.0) || (umin > umax)) {
            Log.severe("Invalid umin=" + umin);
            good = false;
        }
        if((vmin < 0.0) || (vmin > vmax)) {
            Log.severe("Invalid vmin=" + vmin);
            good = false;
        }
        if(umax > 1.0) {
            Log.severe("Invalid umax=" + umax);
            good = false;
        }
        if(vmax > 1.0) {
            Log.severe("Invalid vmax=" + vmax);
            good = false;
        }
        
        return good;
    }
    @Override
    public boolean equals(Object o) {
        if(o == this)
            return true;
        if(o instanceof PatchDefinition) {
            PatchDefinition p = (PatchDefinition)o;
            if((hc == p.hc) && (textureindex == p.textureindex) && 
                    (x0 == p.x0) && (y0 == p.y0) && (z0 == p.z0) &&
                    (xu == p.xu) && (yu == p.yu) && (zu == p.zu) &&
                    (xv == p.xv) && (yv == p.yv) && (zv == p.zv) && 
                    (umin == p.umin) && (umax == p.umax) &&
                    (vmin == p.vmin) && (vmax == p.vmax) &&
                    (uplusvmax == p.uplusvmax) && (sidevis == p.sidevis) && explicitTexCoords == p.explicitTexCoords) {
                return true;
            }
        }
        return false;
    }
    @Override
    public int hashCode() {
        return hc;
    }
    @Override
    public int getTextureIndex() {
        return textureindex;
    }
}
