package org.dynmap.modsupport.gregtech;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.implementations.MTECable;
import gregtech.api.metatileentity.implementations.MTEFluid;
import gregtech.api.metatileentity.implementations.MTEFrame;
import gregtech.api.metatileentity.implementations.MTEItem;
import gregtech.common.render.GTCopiedBlockTextureRender;
import gregtech.common.render.GTMultiTextureRender;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechOrePrefixes;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.GTPPMTEFluid;
import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.forge.GwmCommand;
import org.dynmap.forge.GwmSubCommand;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.modsupport.appliedenergistics2.AE2Support;
import org.dynmap.renderer.MapDataContext;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class GregTechSupport {
    public static final GregTechSupport INSTANCE = new GregTechSupport();

    HashMap<String, IconSet> iconSets = new HashMap<>();
    MetaTileEntityEntry[] metaTileEntries = new MetaTileEntityEntry[65536];
    MaterialEntry[] materialEntries = new MaterialEntry[1000];

    public int[][] validHatchBaseBlocks = new int[65536][];
    public int[][] hatchBaseTextures2 = new int[128][];
    public boolean[] meConnectables;
    private GregTechSupport(){
        GwmCommand.registerSubCommand(new GTDumpCommand());
    }
    public void processIconSet(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        String name = data.get("name");

        if(name == null)
            return;

        IconSet is = new IconSet();

        is.front = getTexture(data, "front", filetoidx);
        is.bottom = getTexture(data, "bottom", filetoidx);
        is.side = getTexture(data, "side", filetoidx);
        is.top = getTexture(data, "top", filetoidx);
        is.output = getTexture(data, "output", filetoidx);

        iconSets.put(name, is);
    }
    public void processMte(HashMap<String, String> data, HashMap<String, Integer> filetoidx){
        String strId = data.get("id");

        if(strId == null)
            return;

        int id = Integer.parseInt(strId);

        if(id < 0 || id >= 65536)
            return;

        String iconSet = data.get("iconset");
        String activeIconSet = data.get("activeset");
        String baseSet = data.get("baseset");
        String colorMulStr = data.get("colormul");

        MetaTileEntityEntry ent = new MetaTileEntityEntry();
        if(iconSet != null)
            ent.icons = iconSets.get(iconSet);

        if(activeIconSet != null)
            ent.activeIcons = iconSets.get(activeIconSet);

        if(baseSet != null)
            ent.baseTextureSet = iconSets.get(baseSet);

        ent.baseTexture = getTexture(data, "basetex", filetoidx);
        ent.baseTexture2 = getTexture(data, "basetex2", filetoidx);

        String isHatch = data.get("ishatch");

        if(isHatch != null && isHatch.equals("true")) {
            ent.isHatch = true;
            ent.type = MteType.Hatch;
        }

        if(colorMulStr != null){
            ent.colorMul = Integer.parseInt(colorMulStr, 16);
        }

        String mteType = data.get("mtetype");

        if(mteType != null) {
            if (mteType.equals("pipe")) {
                ent.type = MteType.Pipe;
            } else if (mteType.equals("cable")) {
                ent.type = MteType.Cable;
            } else if (mteType.equals("wire")) {
                ent.type = MteType.Wire;
            } else if (mteType.equals("controller")) {
                ent.type = MteType.Controller;
            } else if (mteType.equals("hatch")) {
                ent.type = MteType.Hatch;
            }
        }

        String strSize = data.get("size");
        if(strSize != null){
            ent.thickness = Integer.parseInt(strSize);
        }

        metaTileEntries[id] = ent;

    }

    public MetaTileEntityEntry getMTEEntre(int id){
        if(id < 0 || id >= metaTileEntries.length)
            return null;

        return metaTileEntries[id];
    }
    public MaterialEntry getMaterial(int id){
        if(id < 0 || id >= materialEntries.length)
            return null;

        return materialEntries[id];
    }

    int getTexture(HashMap<String, String> data, String key, HashMap<String, Integer> filetoidx){
        String val = data.get(key);

        if(val == null)
            return -1;

        return TexturePack.parseTextureIndex(filetoidx, val);
    }
    public void processHatchBaseExplicit(HashMap<String, String> data, HashMap<String, Integer> filetoidx){
        int index = GWM_Util.objectToInt(data.get("index"), -1);
        int page = GWM_Util.objectToInt(data.get("page"), 0);
        String tex = data.get("tex");
        if(index >= 0 && index < 128 && page >= 0 && page < 128 && tex != null){
            if(hatchBaseTextures2[page] == null)
                hatchBaseTextures2[page] = new int[128];

            hatchBaseTextures2[page][index] = TexturePack.parseTextureIndex(filetoidx, tex);
        }
    }
    public void processHatchBase(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        String blockId = data.get("block");
        if(blockId != null){
            int id = GWM_Util.blockNameToId(blockId);

            int start = GWM_Util.objectToInt(data.get("start"), -1);
            int page = GWM_Util.objectToInt(data.get("page"), 0);


            if(id > 0 && id < 65536) {
                if(validHatchBaseBlocks[id] == null) {
                    validHatchBaseBlocks[id] = new int[16];
                    for (int i = 0; i < 16; i++) {
                        TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(id, i, 0);
                        validHatchBaseBlocks[id][i] = map.getIndexForFace(0);

                        if(start >= 0) {
                            if (hatchBaseTextures2[page] == null)
                                hatchBaseTextures2[page] = new int[128];

                            if(hatchBaseTextures2[page][start + i] == 0)
                                hatchBaseTextures2[page][start + i] = map.getIndexForFace(0);
                        }
                    }
                }
            }
        }

    }

    public void processMeHatch(HashMap<String, String> data) {
        if(meConnectables == null){
            meConnectables = new boolean[65536];
            AE2Support.addConnectableBlock(GWM_Util.blockNameToId("gregtech:gt.blockmachines"), new GregTechMTEConnectableBlockData());
        }

        int id = GWM_Util.objectToInt(data.get("id"),-1);

        if(id >= 0 && id < 65535)
            meConnectables[id] = true;

    }

    public void processMaterial(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        int id = GWM_Util.objectToInt(data.get("id"),-1);

        if(id >= 0 && id < materialEntries.length){
            MaterialEntry ent = new MaterialEntry();
            ent.id = id;
            ent.name = data.get("name");
            ent.oreTexture = TexturePack.parseTextureIndex(filetoidx, data.get("oreTex"));
            ent.smallOreTexture = TexturePack.parseTextureIndex(filetoidx, data.get("smallOreTex"));
            ent.color = Integer.parseInt(data.get("color"), 16);

            materialEntries[id] = ent;
        }
    }

    public class IconSet {
        public int front = -1, bottom = -1, side = -1, top = -1, output = -1;
    }
    public class MetaTileEntityEntry{
        public int baseTexture = -1;
        public int baseTexture2 = -1;
        public IconSet baseTextureSet;
        public IconSet icons;
        public IconSet activeIcons;

        public boolean isHatch;
        public int colorMul = 0xFFFFFF;

        public MteType type = MteType.None;

        public int thickness = 1000;
    }
    public class MaterialEntry{
        public int id;
        public String name;

        public int oreTexture, smallOreTexture;
        public int color;
    }

    public enum MteType {
        None,
        Hatch,
        Controller,
        Pipe,
        Cable,
        Wire,
        Frame

    }

    public class GTDumpCommand extends GwmSubCommand {
        public GTDumpCommand() {
            super("gtdump");
        }


        @Override
        protected void process(ICommandSender sender, String[] args) {
            BufferedWriter writerMteAll, writerMteUnhadled, writerMaterials, writerMaterials2;
            try {
                writerMteAll = new BufferedWriter(new FileWriter("gwm-gt-dump.txt"));
                writerMteUnhadled = new BufferedWriter(new FileWriter("gwm-gt-dump-unhandled.txt"));
                writerMaterials = new BufferedWriter(new FileWriter("gwm-gt-dump-materials.txt"));
                writerMaterials2 = new BufferedWriter(new FileWriter("gwm-gt-dump-materials2.txt"));

                IMetaTileEntity[] metatileentities = GregTechAPI.METATILEENTITIES;
                for (int i = 0; i < metatileentities.length; i++) {
                    IMetaTileEntity imte = metatileentities[i];

                    if(imte == null)
                        continue;

                    dumpMetaTileEntity(writerMteAll, i, imte);

                    if(GregTechSupport.INSTANCE.metaTileEntries[i] == null)
                        dumpMetaTileEntity(writerMteUnhadled, i, imte);
                }

                for(Materials mat : Materials.values()){
                    writerMaterials.write("name=" + mat.mName );

                    if(mat.mIconSet != null && mat.mIconSet.mSetName != null){
                        writerMaterials.write(",iconset=" + mat.mIconSet.mSetName);
                    }
                    if(mat.mCustomID != null) {
                        writerMaterials.write(",customid=" + mat.mCustomID);
                    }
                    writerMaterials.write(",color=" + String.format("%02X%02X%02X", mat.mRGBa[0], mat.mRGBa[1], mat.mRGBa[2])  );

                    writerMaterials.write("\r\n");
                }

                Materials[] sGeneratedMaterials = GregTechAPI.sGeneratedMaterials;
                for (int i = 0; i < sGeneratedMaterials.length; i++) {
                    Materials mat = sGeneratedMaterials[i];
                    writerMaterials2.write("idx=" + i);

                    if(mat == null) {
                        writerMaterials2.write("\r\n");
                        continue;
                    }

                    writerMaterials2.write(",name=" + mat.mName);

                    if (mat.mIconSet != null && mat.mIconSet.mSetName != null) {
                        writerMaterials2.write(",iconset=" + mat.mIconSet.mSetName);
                    }
                    if (mat.mCustomID != null) {
                        writerMaterials2.write(",customid=" + mat.mCustomID);
                    }
                    writerMaterials2.write(",color=" + String.format("%02X%02X%02X", mat.mRGBa[0], mat.mRGBa[1], mat.mRGBa[2]));

                    writerMaterials2.write("\r\n");
                }

                writerMteAll.close();
                writerMteUnhadled.close();
                writerMaterials.close();
                writerMaterials2.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (MinecraftServer.getServer() == null || !MinecraftServer.getServer().isDedicatedServer()) {
                dumpTextures();
            }
            sender.addChatMessage(new ChatComponentText("[GWM] Dumped GT data"));
        }

        private void dumpTextures() {
            try {
                BufferedWriter writerTex = new BufferedWriter(new FileWriter("gwm-gt-dump-textures.txt"));

                for(int i = 0; i < 16384;i++) {
                    ITexture tex = Textures.BlockIcons.getCasingTextureForId(i);
                    writerTex.write("idx=" + i + ",p=" + (i >> 7) + "," + "t=" + (i & 0x7F) + ",");
                    try {
                        if (tex == null) {
                            writerTex.write("tex=NULL");
                        }
                        else if (tex instanceof GTCopiedBlockTextureRender) {
                            GTCopiedBlockTextureRender gtTex = (GTCopiedBlockTextureRender) tex;
                            Block b = gtTex.getBlock();
                            writerTex.write("block=" + GameRegistry.findUniqueIdentifierFor(b) + ",meta=" + gtTex.getMeta());
                            writerTex.write(",icon=" + b.getIcon(2, gtTex.getMeta()));
                            writerTex.write(",class=" + b.getClass().getName());
                        }
                        else {
                            writerTex.write("tex=" + tex);
                        }
                    } catch (Exception ex){
                        writerTex.write("ERROR=" + ex.getMessage());
                    }
                    writerTex.write("\r\n");
                }

                writerTex.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void dumpMetaTileEntity(BufferedWriter writer, int i, IMetaTileEntity imte) throws IOException {
            writer.write("id=" + i + ",name=");
            writer.write(imte.getMetaName());
            if(imte instanceof MetaPipeEntity){
                MetaPipeEntity mpe = (MetaPipeEntity) imte;
                writer.write(",thickness="+mpe.getThickNess());
                writer.write(",capacity="+mpe.getCapacity());

                Materials mat = null;
                if(mpe instanceof MTEFrame){
                    MTEFrame frm = (MTEFrame) mpe;
                    mat =frm.mMaterial;
                }
                else if(mpe instanceof MTEItem){
                    MTEItem frm = (MTEItem) mpe;
                    mat =frm.mMaterial;
                }
                else if(mpe instanceof MTEFluid){
                    MTEFluid frm = (MTEFluid) mpe;
                    mat =frm.mMaterial;
                }
                else if(mpe instanceof MTECable){
                    MTECable frm = (MTECable) mpe;
                    mat =frm.mMaterial;
                }

                if(mat != null) {
                    writer.write(",material=" + mat.mName);
                    writer.write(",matCol="+String.format("%02X%02X%02X",  mat.mRGBa[0]&0xFF,  mat.mRGBa[1]&0xFF,  mat.mRGBa[2]&0xFF) );
                    writer.write(",matIcons=" );
                    if(mat.mIconSet != null){
                        writer.write(mat.mIconSet.mSetName);
                    }
                    else{
                        writer.write("NULL");
                    }
                }
                else{
                    if(mpe instanceof GTPPMTEFluid){
                        GTPPMTEFluid ppfp = (GTPPMTEFluid)mpe;
                        GregtechOrePrefixes.GT_Materials ppmat = ppfp.mMaterial;
                        if(ppmat != null){
                            writer.write(",material=" + ppmat.name());
                            writer.write(",matCol="+String.format("%02X%02X%02X",  ppmat.mRGBa[0]&0xFF,  ppmat.mRGBa[1]&0xFF,  ppmat.mRGBa[2]&0xFF) );
                            writer.write(",matIcons=" );
                            if(ppmat.mIconSet != null){
                                writer.write(ppmat.mIconSet.mSetName);
                            }
                            else{
                                writer.write("NULL");
                            }
                        } else {
                            writer.write(",material=NULL,matCol=000000,matIcons=NULL");
                        }
                    }
                    else {
                        writer.write(",material=NULL,matCol=000000,matIcons=NULL");
                    }
                }
            }
            writer.write(",class=" + imte.getClass().toString().replace("class ",""));
            writer.write("\r\n");
        }
    }

    private class GregTechMTEConnectableBlockData extends AE2Support.ConnectableBlockData {


        public GregTechMTEConnectableBlockData() {

        }

        @Override
        public boolean canConnectFrom(MapDataContext ctx, ForgeDirection dir) {
            ForgeDirection from = dir.getOpposite();
            int id = GWM_Util.objectToInt(ctx.getBlockTileEntityFieldAt("mID",from.offsetX, from.offsetY, from.offsetZ ), -1);
            if(id >= 0 && id < 65535 && meConnectables[id]) {

                int facing = GWM_Util.objectToInt(ctx.getBlockTileEntityFieldAt("mFacing", from.offsetX, from.offsetY, from.offsetZ), -1);

                return facing == dir.ordinal();
            }
            return false;
        }
    }
}
