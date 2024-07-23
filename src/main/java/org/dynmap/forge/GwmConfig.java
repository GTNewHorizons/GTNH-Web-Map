package org.dynmap.forge;

import cpw.mods.fml.common.Loader;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class GwmConfig {
    public static void init(File fileName){
        Configuration cfg = new Configuration(fileName);
        try {
            cfg.load();
            enableChunkLoadingMarkers = cfg.get("settings", "enableChunkLoadingMarkers", true, "If this is true, a marker set is created with area markers for active chunk loaders. ").getBoolean();
            useOldBiomeColoring = cfg.get("settings", "useOldBiomeColoring", false, "Set to true to force old biome coloring. Only recommended if you have an existing map and do not wish to re-render it.").getBoolean();
            enableServerUtilitiesClaimsMarkers = cfg.get("ServerUtilities", "enableClaimMarkers", Loader.isModLoaded("serverutilities"), "If true, a marker set is created with area markers for all claimed chunks.").getBoolean();
            boostServerUtilitiesClaimsMarkers = cfg.get("ServerUtilities", "boostClaimMarkers", true, "Requires enableClaimMarkers. If true, the chunk claim markers will have the boost flag set, which will cause those chunks to be rendered in higher quality/zoom levels for maps with boosting enabled. Note that boosting is not enabled on any maps with the default configuration.").getBoolean();
            useForgeEssentialsPermissions = cfg.get("ForgeEssentials", "usePermissions", true).getBoolean();
            useForgeEssentialsSignHook = cfg.get("ForgeEssentials", "useSignHook", true).getBoolean();
        }
        finally
        {
            cfg.save();
        }
    }

    public static boolean enableChunkLoadingMarkers;
    public static boolean enableServerUtilitiesClaimsMarkers;
    public static boolean boostServerUtilitiesClaimsMarkers;
    public static boolean useForgeEssentialsPermissions;
    public static boolean useForgeEssentialsSignHook;
    public static boolean useOldBiomeColoring;
}
