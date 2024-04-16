package org.dynmap.forge;

import cpw.mods.fml.common.Loader;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class GwmConfig {
    public static void init(File fileName){
        Configuration cfg = new Configuration(fileName);
        try {
            cfg.load();
            enableChunkLoadingMarkers = cfg.get("settings", "enableChunkLoadingMarkers", true).getBoolean();
            enableServerUtilitiesClaimsMarkers = cfg.get("ServerUtilities", "enableClaimMarkers", Loader.isModLoaded("serverutilities")).getBoolean();
            boostServerUtilitiesClaimsMarkers = cfg.get("ServerUtilities", "boostClaimMarkers", true).getBoolean();
        }
        finally
        {
            cfg.save();
        }
    }

    public static boolean enableChunkLoadingMarkers;
    public static boolean enableServerUtilitiesClaimsMarkers;
    public static boolean boostServerUtilitiesClaimsMarkers;
}
