package org.dynmap.modsupport.gregtech;

import gregtech.api.GregTech_API;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.MetaPipeEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Cable;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Fluid;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Frame;
import gregtech.api.metatileentity.implementations.GT_MetaPipeEntity_Item;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import org.dynmap.forge.GwmCommand;
import org.dynmap.forge.GwmSubCommand;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class GregTechSupport {
    public static final GregTechSupport INSTANCE = new GregTechSupport();

    HashMap<String, IconSet> iconSets = new HashMap<>();
    MetaTileEntityEntry[] metaTileEntries = new MetaTileEntityEntry[65536];

    public int[][] validHatchBaseBlocks = new int[65536][];
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

    int getTexture(HashMap<String, String> data, String key, HashMap<String, Integer> filetoidx){
        String val = data.get(key);

        if(val == null)
            return -1;

        return TexturePack.parseTextureIndex(filetoidx, val);
    }

    public void processHatchBase(HashMap<String, String> data, HashMap<String, Integer> filetoidx) {
        String blockId = data.get("block");
        if(blockId != null){
            int id = GWM_Util.blockNameToId(blockId);

            if(id > 0 && id < 65536) {
                if(validHatchBaseBlocks[id] == null) {
                    validHatchBaseBlocks[id] = new int[16];
                    for (int i = 0; i < 16; i++) {
                        TexturePack.HDTextureMap map = TexturePack.HDTextureMap.getMap(id, i, 0);
                        validHatchBaseBlocks[id][i] = map.getIndexForFace(0);
                    }
                }
            }
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
            BufferedWriter writerMteAll, writerMteUnhadled, writerMaterials;
            try {
                writerMteAll = new BufferedWriter(new FileWriter("gwm-gt-dump.txt"));
                writerMteUnhadled = new BufferedWriter(new FileWriter("gwm-gt-dump-unhandled.txt"));
                writerMaterials = new BufferedWriter(new FileWriter("gwm-gt-dump-materials.txt"));



                IMetaTileEntity[] metatileentities = GregTech_API.METATILEENTITIES;
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

                writerMteAll.close();
                writerMteUnhadled.close();
                writerMaterials.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


            sender.addChatMessage(new ChatComponentText("[GWM] Dumped GT data"));
        }

        private void dumpMetaTileEntity(BufferedWriter writer, int i, IMetaTileEntity imte) throws IOException {
            writer.write("id=" + i + ",name=");
            writer.write(imte.getMetaName());
            if(imte instanceof MetaPipeEntity){
                MetaPipeEntity mpe = (MetaPipeEntity) imte;
                writer.write(",thickness="+mpe.getThickNess());
                writer.write(",capacity="+mpe.getCapacity());

                Materials mat = null;
                if(mpe instanceof GT_MetaPipeEntity_Frame){
                    GT_MetaPipeEntity_Frame frm = (GT_MetaPipeEntity_Frame) mpe;
                    mat =frm.mMaterial;
                }
                else if(mpe instanceof GT_MetaPipeEntity_Item){
                    GT_MetaPipeEntity_Item frm = (GT_MetaPipeEntity_Item) mpe;
                    mat =frm.mMaterial;
                }
                else if(mpe instanceof GT_MetaPipeEntity_Fluid){
                    GT_MetaPipeEntity_Fluid frm = (GT_MetaPipeEntity_Fluid) mpe;
                    mat =frm.mMaterial;
                }
                else if(mpe instanceof GT_MetaPipeEntity_Cable){
                    GT_MetaPipeEntity_Cable frm = (GT_MetaPipeEntity_Cable) mpe;
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
                    writer.write(",material=NULL,matCol=000000,matIcons=NULL");
                }
            }
            writer.write(",class=" + imte.getClass().toString().replace("class ",""));
            writer.write("\r\n");
        }
    }
}
