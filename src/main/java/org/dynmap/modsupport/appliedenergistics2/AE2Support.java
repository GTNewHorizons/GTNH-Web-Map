package org.dynmap.modsupport.appliedenergistics2;

import net.minecraftforge.common.util.ForgeDirection;
import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.GWM_Util;
import org.dynmap.renderer.MapDataContext;

import java.util.HashMap;

public class AE2Support {
    public static CableType[] cableTypes = new CableType[32768];
    public static void addCableType(HashMap<String, Integer> filetoidx, int damage, String texture, String texture2, String texture3, int size) {
        cableTypes[damage] = new CableType(size, TexturePack.parseTextureIndex(filetoidx, texture), TexturePack.parseTextureIndex(filetoidx, texture2), TexturePack.parseTextureIndex(filetoidx, texture3));
    }

    public static ConnectableBlockData getConnectableData(int id) {
        if(connectableBlocks == null){
            initConnectableBlocks();
        }
        return connectableBlocks.get(id);
    }
    private static void initConnectableBlocks() {
        connectableBlocks = new HashMap<>();
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockController"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockDrive"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockChest"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockInterface"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockCellWorkbench"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockCondenser"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockVibrationChamber"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockQuartzGrowthAccelerator"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockEnergyCell"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockDenseEnergyCell"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockCreativeEnergyCell"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockQuartzGrowthAccelerator"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockCraftingUnit"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockCraftingStorage"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockAdvancedCraftingStorage"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockMolecularAssembler"), new ConnectableBlockData());
        connectableBlocks.put(GWM_Util.blockNameToId("appliedenergistics2:tile.BlockIOPort"), new ConnectableBlockData());
    }

    public static void addConnectableBlock(int blockId, ConnectableBlockData data){
        if(connectableBlocks == null)
            initConnectableBlocks();

        connectableBlocks.put(blockId, data);
    }

    public static class CableType{
        public int size;
        public final int textureId;
        public final int textureId2;
        public final int textureId3;

        public CableType(int size, int textureId, int textureId2, int textureId3) {

            this.size = size;
            this.textureId = textureId;
            this.textureId2 = textureId2;
            this.textureId3 = textureId3;
        }
    }
    static HashMap<Integer, ConnectableBlockData> connectableBlocks;

    public static class ConnectableBlockData{
        public boolean canConnectFrom(MapDataContext ctx, ForgeDirection dir){
            return true;
        }
    }
}
