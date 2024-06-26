package org.dynmap.forge;

import java.io.File;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.Loader;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.Log;
import org.dynmap.Tags;
import org.dynmap.forge.DynmapPlugin.OurLog;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent; 
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import org.dynmap.forge.integration.ForgeChunkLoaderMarkers;
import org.dynmap.forge.integration.ServerUtilitiesClaimedChunksMarkers;

@Mod(modid = "Dynmap", name = "Dynmap", version = Tags.GRADLETOKEN_VERSION)
public class DynmapMod
{
    // The instance of your mod that Forge uses.
    @Instance("Dynmap")
    public static DynmapMod instance;

    // Says where the client and server 'proxy' code is loaded.
    @SidedProxy(clientSide = "org.dynmap.forge.ClientProxy", serverSide = "org.dynmap.forge.Proxy")
    public static Proxy proxy;
    
    public static DynmapPlugin plugin;
    public static File jarfile;
    public static boolean useforcedchunks;

    public class APICallback extends DynmapCommonAPIListener {
        @Override
        public void apiListenerAdded() {
            if(plugin == null) {
                plugin = proxy.startServer();
            }
        }
        @Override
        public void apiEnabled(DynmapCommonAPI api) {
        }
    } 
    
    public class LoadingCallback implements net.minecraftforge.common.ForgeChunkManager.LoadingCallback {
        @Override
        public void ticketsLoaded(List<Ticket> tickets, World world) {
            if(tickets.size() > 0) {
                DynmapPlugin.setBusy(world, tickets.get(0));
                for(int i = 1; i < tickets.size(); i++) {
                    ForgeChunkManager.releaseTicket(tickets.get(i));
                }
            }
        }
    }

    public DynmapMod() {
        Log.setLogger(new OurLog());      
        org.dynmap.modsupport.ModSupportImpl.init();
    }
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        jarfile = event.getSourceFile();

        Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
        try {
            cfg.load();
            
            useforcedchunks = cfg.get("Settings",  "UseForcedChunks", true).getBoolean(true);
        }
        finally
        {
            cfg.save();
        }

        GwmConfig.init(new File(event.getModConfigurationDirectory(), "GTNH-Web-Map.cfg"));

    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        /* Set up for chunk loading notice from chunk manager */
        if(useforcedchunks) {
            ForgeChunkManager.setForcedChunkLoadingCallback(DynmapMod.instance, new LoadingCallback());
        }
        else {
            System.out.println("[Dynmap] World loading using forced chunks is disabled");
        }
    }

    private boolean isMCPC;
    
    @EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        MinecraftServer ms = MinecraftServer.getServer();
        isMCPC = (ms != null) && (ms.getServerModName().contains("mcpc"));
        if (isMCPC) {
            DynmapCommonAPIListener.register(new APICallback()); 
        }
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
    }
    
    @EventHandler
    public void serverStarted(FMLServerStartedEvent event)
    {
        if (!isMCPC) {
            DynmapCommonAPIListener.register(new APICallback()); 
        }
        if(plugin == null)
            plugin = proxy.startServer();
        plugin.serverStarted();

        if(GwmConfig.enableChunkLoadingMarkers){
            DynmapCommonAPIListener.register(new ForgeChunkLoaderMarkers());
            ForgeChunkLoaderMarkers.updateMarkers();
        }

        if(GwmConfig.enableServerUtilitiesClaimsMarkers && Loader.isModLoaded("serverutilities")){
            DynmapCommonAPIListener.register(new ServerUtilitiesClaimedChunksMarkers());
        }
    }
    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event)
    {
    	proxy.stopServer(plugin);
    	plugin = null;
    }
    @NetworkCheckHandler
    public boolean netCheckHandler(Map<String, String> mods, Side side) {
        return true;
    }
}
