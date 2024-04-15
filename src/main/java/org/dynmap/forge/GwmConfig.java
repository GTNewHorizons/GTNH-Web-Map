package org.dynmap.forge;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class GwmConfig {
    public static void init(File fileName){
        Configuration cfg = new Configuration(fileName);
        try {
            cfg.load();
            enableChunkLoadingMarkers = cfg.get("settings", "enableChunkLoadingMarkers", true).getBoolean();
        }
        finally
        {
            cfg.save();
        }
    }

    public static boolean enableChunkLoadingMarkers;
}
