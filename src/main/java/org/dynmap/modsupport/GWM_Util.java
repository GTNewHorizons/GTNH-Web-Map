package org.dynmap.modsupport;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class GWM_Util {
    static boolean initialized;
    static Map<String, Integer> blockIdMap;
    static Map<String, Integer> unlocalizedNameToIdMap;
    static Map<String, Integer> textureNameToIdMap = new HashMap<>();
    static public HashMap<String, ArrayList<Object>> blockGroups = new HashMap<>();

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

    public static void tryRegisterTextureByName(String name, int id) {
        if(textureNameToIdMap.get(name) == null)
            textureNameToIdMap.put(name, id);
    }

    public static int tryGetTextureIdByName(String name){
        Integer exact = textureNameToIdMap.get(name);
        if(exact != null)
            return exact;

        String noColon = name.replace(':', '/');
        Integer noColonAttempt = textureNameToIdMap.get(noColon);
        if(noColonAttempt != null)
            return noColonAttempt;

        String prependMinecraft = "minecraft/" + noColon;
        Integer prependMinecraftAttempt = textureNameToIdMap.get(prependMinecraft);
        if(prependMinecraftAttempt != null)
            return prependMinecraftAttempt;

        return -1;
    }
    public static void loadBlockGroups(DynmapCore core){
        if(!initialized)
            initialize(core);

        Log.verboseinfo("Loading block groups...");
        File f = new File(core.getDataFolder(), "blockgroups.yml");
        if(!core.updateUsingDefaultResource("/blockgroups.yml", f, "block-groups")) {
            return;
        }
        ConfigurationNode builtIn = new ConfigurationNode(f);
        builtIn.load();

        addBlockGroups(builtIn);

        /* Load custom block groups, if file is defined - or create empty one if not */
        f = new File(core.getDataFolder(), "blockgroups.yml");
        core.createDefaultFileFromResource("/blockgroups.yml", f);
        if(f.exists()) {
            ConfigurationNode custom = new ConfigurationNode(f);
            custom.load();
            addBlockGroups(custom);
        }
    }

    private static void addBlockGroups(ConfigurationNode builtIn) {
        for(Map<String, Object> cn : builtIn.getMapList("block-groups")){
            for(Map.Entry<String, Object> ent : cn.entrySet()){
                String key = ent.getKey();
                Object value = ent.getValue();
                if(value instanceof ArrayList){
                    blockGroups.put(key, (ArrayList) value);
                }
                else {
                    blockGroups.put(key, new ArrayList<>());
                }
            }
        }
    }

    public static BlockMatcher getBlockMatcher(ArrayList<Object> entries){
        BlockMatcher ret = new BlockMatcher();
        HashSet visitedGroups = new HashSet();

        addList(entries, ret, visitedGroups);

        return ret;
    }

    private static void addList(ArrayList<Object> entries, BlockMatcher bm, HashSet visitedGroups) {
        for (Object o : entries) {
            if (o instanceof Integer) {
                bm.addBlock((Integer) o);
            } else if (o instanceof String) {
                String s = (String) o;
                if (s.startsWith("@")) {
                    String sub = s.substring(1);
                    if (!visitedGroups.contains(sub)) {
                        ArrayList<Object> group = blockGroups.get(sub);
                        if (group != null) {
                            visitedGroups.add(sub);
                            addList(group, bm, visitedGroups);
                        }
                    }
                } else {
                    String blockName = s.trim();

                    Integer blockId = blockIdMap.get(blockName);
                    if(blockId == null)
                        continue;

                    String cond = null;
                    int meta = 0xF;
                    if(blockName.contains("[")){
                        int idx = blockName.indexOf('[');
                        int lastIdx = blockName.lastIndexOf(']');
                        cond = blockName.substring(idx+1, lastIdx);
                        blockName = blockName.substring(0, idx);
                    }

                    if(blockName.contains("#")){
                        int idx = blockName.indexOf('#');
                        String metaStr = blockName.substring(idx+1);
                        blockName = blockName.substring(0, idx);
                        if(metaStr.startsWith("m"))
                            meta = Integer.parseInt(metaStr.substring(1));
                        else
                            meta = 1 << Integer.parseInt(metaStr);
                    }

                    if(cond != null){
                        bm.addBlockWithCondition((int)blockId, meta, parseCondition(cond));
                    } else {
                        bm.addBlockMetaMask(blockId, meta);
                    }
                }
            }
        }
    }

    static BlockMatcher.AdvancedConditionParser dummyConditionParser = new BlockMatcher.AdvancedConditionParser();
    private static BlockMatcher.AdvancedCondition parseCondition(String cond) {
        return dummyConditionParser.parseExpression(cond);
    }
}
