package org.dynmap.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/**
 * Abstract base class for custom renderers - used to allow creation of customized patch sets for blocks
 *
 * Custom renderer classes are loaded by classname, and must have a default constructor
 */
public abstract class CustomRenderer {
    /**
     * Constructor - subclass must have public default constructor
     */
    protected CustomRenderer() {

    }

    public static RenderPatch[] getBoxSingleTexture(RenderPatchFactory rpf, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax, int tex, boolean rotateTopAndBottom) {
        int[] patchTextureIds = new int[]{tex, tex, tex, tex, tex, tex};
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();

        if(rotateTopAndBottom)
            CustomRenderer.addBoxRotatedTopAndBottom(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchTextureIds);
        else
            CustomRenderer.addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchTextureIds);

        return list.toArray(new RenderPatch[patchTextureIds.length]);
    }
    public static RenderPatch[] getBoxSingleTextureInt(RenderPatchFactory rpf, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax, int tex, boolean rotateTopAndBottom) {
        return getBoxSingleTexture(rpf, xmin/16.0, xmax/16.0, ymin/16.0,ymax/16.0,zmin/16.0,zmax/16.0,tex, rotateTopAndBottom);
    }
    public static RenderPatch[] getBoxMultiTexture(RenderPatchFactory rpf, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax, int startTex) {
        int[] patchTextureIds = new int[]{startTex, startTex + 1, startTex + 2, startTex + 3, startTex + 4, startTex + 5};

        return getBoxMultiTexture(rpf, xmin, xmax, ymin, ymax, zmin, zmax, patchTextureIds);
    }

    public static RenderPatch[] getBoxMultiTexture(RenderPatchFactory rpf, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax, int[] patchTextureIds) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, xmin, xmax, ymin, ymax, zmin, zmax, patchTextureIds);
        return list.toArray(new RenderPatch[patchTextureIds.length]);
    }
    public static RenderPatch[] getBoxMultiTextureInt(RenderPatchFactory rpf, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax, int startTex) {
        return getBoxMultiTexture(rpf, xmin/16.0, xmax/16.0, ymin/16.0,ymax/16.0,zmin/16.0,zmax/16.0, startTex);
    }
    public static RenderPatch[] getBoxFull(RenderPatchFactory rpf, int[] patchIndices) {
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1, 0, 1, patchIndices);
        return list.toArray(new RenderPatch[patchIndices.length]);
    }

    /**
     * Initialize custom renderer
     *
     * If overridden, super.initializeRenderer() should be called and cause exit if false is returned
     *
     * @param rpf - render patch factory (used for allocating patches)
     * @param blkid - block type ID
     * @param blockdatamask - block data mask (bit N=1 if block data value N is to be handled by renderer)
     * @param custparm - parameter strings for custom renderer - renderer specific
     * @return true if initialized successfully, false if not
     */
    public boolean initializeRenderer(RenderPatchFactory rpf, int blkid, int blockdatamask, Map<String,String> custparm) {
        return true;
    }
    /**
     * Cleanup custom renderer
     *
     * If overridden, super.cleanupRenderer() should be called
     */
    public void cleanupRenderer() {

    }
    /**
     * Return list of fields from the TileEntity associated with the blocks initialized for the renderer, if any.
     *
     * @return array of field ID strings, or null if none (the default)
     */
    public String[] getTileEntityFieldsNeeded() {
        return null;
    }
    /**
     * Return the maximum number of texture indices that may be returned by the renderer.  Used to validate
     * the teture mapping defined for the block definitions.
     * @return highest texture index that may be returned, plus 1.  Default is 1.
     */
    protected int getMaximumTextureCount() {
        return 1;
    }
    /**
     * Return the maximum number of texture indices that may be returned by the renderer.  Used to validate
     * the teture mapping defined for the block definitions.
     *
     * @param rpf - render patch factory (used for allocating patches)
     * @return highest texture index that may be returned, plus 1.  Default is 1.
     */
    public int getMaximumTextureCount(RenderPatchFactory rpf) {
        return getMaximumTextureCount();
    }

    public static RenderPatch[] getRotatedSet(RenderPatchFactory rpf, RenderPatch[] input, int rotX, int rotY, int rotZ){
        return getRotatedSet(rpf, input, rotX, rotY, rotZ, false);
    }
    public static RenderPatch[] getRotatedSet(RenderPatchFactory rpf, RenderPatch[] input, int rotX, int rotY, int rotZ, boolean autoTexCoords){
        RenderPatch[] ret = new RenderPatch[input.length];

        for (int i = 0; i < input.length; i++){
            ret[i] = autoTexCoords ? rpf.getRotatedPatchAutoTexCoords(input[i], 0, rotY, 0, input[i].getTextureIndex()) : rpf.getRotatedPatch(input[i], 0, rotY, 0, input[i].getTextureIndex());
        }
        if(rotX != 0) {
            for (int i = 0; i < input.length; i++){
                ret[i] = autoTexCoords ? rpf.getRotatedPatchAutoTexCoords(ret[i], rotX, 0, 0, ret[i].getTextureIndex()) : rpf.getRotatedPatch(ret[i], rotX, 0, 0, ret[i].getTextureIndex());
            }
        }
        if(rotZ != 0) {
            for (int i = 0; i < input.length; i++){
                ret[i] = autoTexCoords ? rpf.getRotatedPatchAutoTexCoords(ret[i], 0, 0, rotZ, ret[i].getTextureIndex()) : rpf.getRotatedPatch(ret[i], 0, 0, rotZ, ret[i].getTextureIndex());
            }
        }
        return ret;
    }

    /**
     * Return list of patches for block at given coordinates.  List will be treated as read-only, so the same list can
     * and should be returned, when possible, for different blocks with the same patch list.  The provided map data
     * context will always allow reading of data for the requested block, any data within its chunk, and any block
     * within one block in any direction of the requested block, at a minimum.  Also will include any requested tile
     * entity data for those blocks.
     *
     * @param mapDataCtx - Map data context: can be used to read any data available for map.
     * @return patch list for given block
     */

    public abstract RenderPatch[] getRenderPatchList(MapDataContext mapDataCtx);

    public static RenderPatch[] combineMultiple(RenderPatch[]... list){
        int count = 0;
        for(RenderPatch[] rp : list){
            count += rp.length;
        }
        RenderPatch[] ret = new RenderPatch[count];

        int j = 0;
        for(RenderPatch[] rp : list){
            for(int i = 0; i < rp.length; i++)
                ret[j++] = rp[i];
        }
        return ret;
    }
    public static RenderPatch[] combineMultiple(RenderPatch... list){
        RenderPatch[] ret = new RenderPatch[list.length];
        int j = 0;
        for(RenderPatch rp : list){
                ret[j++] = rp;
        }
        return ret;
    }
    public CustomRendererData getRenderData(MapDataContext mapDataCtx){
        return new CustomRendererData(getRenderPatchList(mapDataCtx), null, null);
    };
    private static final int[] default_patches = { 0, 0, 0, 0, 0, 0 };

    private static void addIfNonNull(List<RenderPatch> list, RenderPatch p) {
        if (p != null)
            list.add(p);
    }
    /**
     *  Utility method: add simple box to give list
     * @param rpf - patch factory
     * @param list - list to add patches to
     * @param xmin - minimum for X axis
     * @param xmax - maximum for X axis
     * @param ymin - minimum for Y axis
     * @param ymax - maximum for Y axis
     * @param zmin - minimum for Z axis
     * @param zmax - maximum for Z axis
     * @param patchids - patch IDs for each face (bottom,top,xmin,xmax,zmin,zmax)
     */
    public static void addBox(RenderPatchFactory rpf, List<RenderPatch> list, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax, int[] patchids)  {
        if(patchids == null) {
            patchids = default_patches;
        }
        /* Add bottom */
        if(patchids[0] >= 0)
            addIfNonNull(list, rpf.getPatch(0, ymin, 0, 1, ymin, 0, 0, ymin, 1, xmin, xmax, zmin, zmax, SideVisible.TOP, patchids[0]));
        /* Add top */
        if(patchids[1] >= 0)
            addIfNonNull(list, rpf.getPatch(0, ymax, 1, 1, ymax, 1, 0, ymax, 0, xmin, xmax, 1-zmax, 1-zmin, SideVisible.TOP, patchids[1]));
        /* Add minX side */
        if(patchids[2] >= 0)
            addIfNonNull(list, rpf.getPatch(xmin, 0, 0, xmin, 0, 1, xmin, 1, 0, zmin, zmax, ymin, ymax, SideVisible.TOP, patchids[2]));
        /* Add maxX side */
        if(patchids[3] >= 0)
            addIfNonNull(list, rpf.getPatch(xmax, 0, 1, xmax, 0, 0, xmax, 1, 1, 1-zmax, 1-zmin, ymin, ymax, SideVisible.TOP, patchids[3]));
        /* Add minZ side */
        if(patchids[4] >= 0)
            addIfNonNull(list, rpf.getPatch(1, 0, zmin, 0, 0, zmin, 1, 1, zmin, 1-xmax, 1-xmin, ymin, ymax, SideVisible.TOP, patchids[4]));
        /* Add maxZ side */
        if(patchids[5] >= 0)
            addIfNonNull(list, rpf.getPatch(0, 0, zmax, 1, 0, zmax, 0, 1, zmax, xmin, xmax, ymin, ymax, SideVisible.TOP, patchids[5]));
    }
    public static void addBoxRotatedTopAndBottom(RenderPatchFactory rpf, List<RenderPatch> list, double xmin, double xmax, double ymin, double ymax, double zmin, double zmax, int[] patchids)  {
        if(patchids == null) {
            patchids = default_patches;
        }
        /* Add bottom */
        if(patchids[0] >= 0)
            addIfNonNull(list, rpf.getPatch(0, ymin, 1, 1, ymin, 1, 0, ymin, 0, xmin, xmax, zmin, zmax, SideVisible.TOP, patchids[0]));
        /* Add top */
        if(patchids[1] >= 0)
            addIfNonNull(list, rpf.getPatch(0, ymax, 0, 0, ymax, 1, 1, ymax, 0, 1-zmax, 1-zmin, xmin, xmax, SideVisible.TOP, patchids[1]));
        /* Add minX side */
        if(patchids[2] >= 0)
            addIfNonNull(list, rpf.getPatch(xmin, 0, 0, xmin, 0, 1, xmin, 1, 0, zmin, zmax, ymin, ymax, SideVisible.TOP, patchids[2]));
        /* Add maxX side */
        if(patchids[3] >= 0)
            addIfNonNull(list, rpf.getPatch(xmax, 0, 1, xmax, 0, 0, xmax, 1, 1, 1-zmax, 1-zmin, ymin, ymax, SideVisible.TOP, patchids[3]));
        /* Add minZ side */
        if(patchids[4] >= 0)
            addIfNonNull(list, rpf.getPatch(1, 0, zmin, 0, 0, zmin, 1, 1, zmin, 1-xmax, 1-xmin, ymin, ymax, SideVisible.TOP, patchids[4]));
        /* Add maxZ side */
        if(patchids[5] >= 0)
            addIfNonNull(list, rpf.getPatch(0, 0, zmax, 1, 0, zmax, 0, 1, zmax, xmin, xmax, ymin, ymax, SideVisible.TOP, patchids[5]));
    }
    public static void addBoxInt(RenderPatchFactory rpf, List<RenderPatch> list, int xmin, int xmax, int ymin, int ymax, int zmin, int zmax, int[] patchids){
        addBox(rpf, list, xmin/16.0, xmax/16.0, ymin/16.0,ymax/16.0, zmin/16.0,zmax/16.0, patchids);
    }
    /**
     * Get patch corresponding to side N (per MC side index: 0=bottom (y-), 1=top (y+), 2=z-, 3=z+, 4=x-, 5=x+)
     * @param rpf - patch factory
     * @param side - side number
     * @param setback - amount face is set back from block edge (0=on side, 0.5=at middle)
     * @param umin - texture horizontal minimum
     * @param umax - texture horizontal maximum
     * @param vmin - texture vertical minimum
     * @param vmax - texture vertical maximum
     * @param rot - rotation of texture (clockwise on given side)
     * @param textureidx - texture index
     */
    public static RenderPatch getSidePatch(RenderPatchFactory rpf, int side, double setback, double umin, double umax, double vmin, double vmax, int rot, int textureidx) {
        RenderPatch rp = null;
        switch(side) {
            case 0:
                rp = rpf.getPatch(0, setback, 0, 1, setback, 0, 0, setback, 1, umin, umax, vmin, vmax, SideVisible.TOP, textureidx);
                if (rot != 0) {
                    rp = rpf.getRotatedPatch(rp, 0, rot, 0, textureidx);
                }
                break;
            case 1:
                rp = rpf.getPatch(0, 1.0-setback, 1, 1, 1.0-setback, 1, 0, 1.0-setback, 0, umin, umax, 1-vmax, 1-vmin, SideVisible.TOP, textureidx);
                if (rot != 0) {
                    rp = rpf.getRotatedPatch(rp, 0, 360-rot, 0, textureidx);
                }
                break;
            case 2:
                rp = rpf.getPatch(1, 0, setback, 0, 0, setback, 1, 1, setback, 1-umax, 1-umin, vmin, vmax, SideVisible.TOP, textureidx);
                if (rot != 0) {
                    rp = rpf.getRotatedPatch(rp, 0, 0, rot, textureidx);
                }
                break;
            case 3:
                rp = rpf.getPatch(0, 0, 1.0-setback, 1, 0, 1.0-setback, 0, 1, 1.0-setback, umin, umax, vmin, vmax, SideVisible.TOP, textureidx);
                if (rot != 0) {
                    rp = rpf.getRotatedPatch(rp, 0, 0, 360-rot, textureidx);
                }
                break;
            case 4:
                rp = rpf.getPatch(setback, 0, 0, setback, 0, 1, setback, 1, 0, umin, umax, vmin, vmax, SideVisible.TOP, textureidx);
                if (rot != 0) {
                    rp = rpf.getRotatedPatch(rp, rot, 0, 0, textureidx);
                }
                break;
            case 5:
                rp = rpf.getPatch(1.0-setback, 0, 1, 1.0-setback, 0, 0, 1.0-setback, 1, 1, 1-umax, 1-umin, vmin, vmax, SideVisible.TOP, textureidx);
                if (rot != 0) {
                    rp = rpf.getRotatedPatch(rp, 360-rot, 0, 0, textureidx);
                }
                break;
        }
        return rp;
    }
    /**
     * Get patch corresponding to side N (per MC side index: 0=bottom (y-), 1=top (y+), 2=z-, 3=z+, 4=x-, 5=x+)
     * @param rpf - patch factory
     * @param side - side number
     * @param rot - rotation of texture (clockwise on given side)
     * @param textureidx - texture index
     */
    public static RenderPatch getSidePatch(RenderPatchFactory rpf, int side, int rot, int textureidx) {
        return getSidePatch(rpf, side, 0.0, 0.0, 1.0, 0.0, 1.0, rot, textureidx);
    }

    public static RenderPatch[] getFullBlock(RenderPatchFactory rpf, int fixedTexture){
        int patchTextureIds[] = { 0, 1, 4, 5, 2, 3 };

        if(fixedTexture != -1)
            for(int i = 0; i< patchTextureIds.length;i++)
                patchTextureIds[i] = fixedTexture;

        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, patchTextureIds);
        return list.toArray(new RenderPatch[patchTextureIds.length]);
    }

    public static RenderPatch[] getFullBlock(RenderPatchFactory rpf, int patchTextureIds[]){
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        CustomRenderer.addBox(rpf, list, 0, 1, 0, 1,0, 1, patchTextureIds);
        return list.toArray(new RenderPatch[patchTextureIds.length]);
    }
}
