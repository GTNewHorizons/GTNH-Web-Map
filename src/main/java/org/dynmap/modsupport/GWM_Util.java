package org.dynmap.modsupport;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.client.stream.IngestServerTester;
import org.dynmap.DynmapCore;

import java.util.HashMap;
import java.util.Map;

public class GWM_Util {
    static boolean initialized;
    static Map<String, Integer> blockIdMap;
    static Map<String, Integer> unlocalizedNameToIdMap;
    public static void initialize(DynmapCore core)
    {
        if(initialized)
            return;

        blockIdMap = core.getBlockIDMap();
        unlocalizedNameToIdMap = new HashMap<>();

        FMLControlledNamespacedRegistry<Block> blockRegistry = GameData.getBlockRegistry();
        for (Map.Entry<String, Integer> entry : blockIdMap.entrySet()) {
            Block block = blockRegistry.getObjectById(entry.getValue());
            unlocalizedNameToIdMap.put(block.getUnlocalizedName(), entry.getValue());
        }

        initialized = true;
    }
    public static int blockNameToId(String stringId){
        return blockNameToId(stringId, false);
    }
    public static int blockNameToId(String stringId, boolean alsoCheckUnlocalizedNames)
    {
        Integer intId = blockIdMap.get(stringId);
        if(intId != null)
            return intId.intValue();

        if(alsoCheckUnlocalizedNames) {
            intId = unlocalizedNameToIdMap.get(stringId);
            if (intId != null)
                return intId.intValue();
        }

        return objectToInt(stringId, 0);
    }

    public static int objectToInt(Object obj, int def){
        if(obj == null)
            return def;

        if(obj instanceof Integer)
            return (Integer)obj;

        if(obj instanceof Short)
            return (Short)obj;

        if(obj instanceof Byte)
            return (Byte)obj;

        try {
            if (obj instanceof String)
                return Integer.parseInt((String) obj);
        } catch(NumberFormatException nfe){
            // Do nothing
        }
        return def;
    }
}
