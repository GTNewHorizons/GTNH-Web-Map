package org.dynmap.modsupport;

import net.minecraft.client.stream.IngestServerTester;
import org.dynmap.DynmapCore;

import java.util.Map;

public class GWM_Util {
    static boolean initialized;
    static Map<String, Integer> blockIdMap;
    public static void initialize(DynmapCore core)
    {
        if(initialized)
            return;

        blockIdMap = core.getBlockIDMap();

        initialized = true;
    }

    public static int blockNameToId(String stringId)
    {
        Integer intId = blockIdMap.get(stringId);
        if(intId != null)
            return intId.intValue();

        return 0;
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

        return def;
    }
}
