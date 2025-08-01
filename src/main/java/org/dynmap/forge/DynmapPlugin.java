package org.dynmap.forge;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.BanList;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.server.management.UserListBans;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapChunk;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapLocation;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.PlayerList;
import org.dynmap.Tags;
import org.dynmap.forge.DmapCommand;
import org.dynmap.forge.DmarkerCommand;
import org.dynmap.forge.DynmapCommand;
import org.dynmap.forge.DynmapMod;
import org.dynmap.forge.permissions.FilePermissions;
import org.dynmap.forge.permissions.OpPermissions;
import org.dynmap.forge.permissions.PermissionProvider;
import org.dynmap.common.BiomeMap;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.debug.Debug;
import org.dynmap.permissions.PermissionsHandler;
import org.dynmap.utils.DynmapLogger;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.VisibilityLimit;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class DynmapPlugin
{
    private DynmapCore core;
    private PermissionProvider permissions;
    private boolean core_enabled;
    public SnapshotCache sscache;
    public PlayerList playerList;
    private MapManager mapManager;
    private net.minecraft.server.MinecraftServer server;
    public static DynmapPlugin plugin;
    private ChatHandler chathandler;
    private HashMap<String, Integer> sortWeights = new HashMap<String, Integer>(); 
    // Drop world load ticket after 30 seconds
    private long worldIdleTimeoutNS = 30 * 1000000000L;
    private HashMap<String, ForgeWorld> worlds = new HashMap<String, ForgeWorld>();
    private World last_world;
    private ForgeWorld last_fworld;
    private Map<String, ForgePlayer> players = new HashMap<String, ForgePlayer>();
    //TODO private ForgeMetrics metrics;
    private HashSet<String> modsused = new HashSet<String>();
    private ForgeServer fserver = new ForgeServer();
    private boolean tickregistered = false;
    // TPS calculator
    private double tps;
    private long lasttick;
    private long avgticklen;
    // Per tick limit, in nsec
    private long perTickLimit = (50000000); // 50 ms
    private boolean isMCPC = false;
    private boolean useSaveFolder = true;
    private Field displayName = null; // MCPC+ display name

    private static final String[] TRIGGER_DEFAULTS = { "blockupdate", "chunkpopulate", "chunkgenerate" };

    private static final Pattern patternControlCode = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    public static class BlockUpdateRec {
    	World w;
    	String wid;
    	int x, y, z;
    }
    ConcurrentLinkedQueue<BlockUpdateRec> blockupdatequeue = new ConcurrentLinkedQueue<BlockUpdateRec>();

    public static final int getBlockID(World w, int x, int y, int z) {
        // Block.getIdFromBlock(w.getBlockType(x,y,z))
        return Block.getIdFromBlock(w.getBlock(x,  y,  z));
    }
    public static final Block getBlockByID(int id) {
        return Block.getBlockById(id);
    }
    public static final Item getItemByID(int id) {
        return Item.getItemById(id);
    }
    public static final Material getBlockMaterial(Block b) {
        return b.getMaterial();
    }
    public static final String getBlockUnlocalizedName(Block b) {
        String s = b.getUnlocalizedName();
        if (s.startsWith("tile.")) {
            s = s.substring(5);
        }
        return s;
    }
    public static final BiomeGenBase[] getBiomeList() {
        return BiomeGenBase.getBiomeGenArray();
    }
    public static final String getChatMessage(IChatComponent cc) {
        return cc.getUnformattedTextForChat();
    }
    public static final NetworkManager getNetworkManager(NetHandlerPlayServer nh) {
        return nh.netManager;
    }
    
    private ForgePlayer getOrAddPlayer(EntityPlayer p) {
        String name = p.getCommandSenderName();
    	ForgePlayer fp = players.get(name);
    	if(fp != null) {
    		fp.player = p;
    	}
    	else {
    		fp = new ForgePlayer(p);
    		players.put(name, fp);
    	}
    	return fp;
    }
    
    private static class TaskRecord implements Comparable<Object>
    {
        private long ticktorun;
        private long id;
        private FutureTask<?> future;
        @Override
        public int compareTo(Object o)
        {
            TaskRecord tr = (TaskRecord)o;

            if (this.ticktorun < tr.ticktorun)
            {
                return -1;
            }
            else if (this.ticktorun > tr.ticktorun)
            {
                return 1;
            }
            else if (this.id < tr.id)
            {
                return -1;
            }
            else if (this.id > tr.id)
            {
                return 1;
            }
            else
            {
                return 0;
            }
        }
    }

    private class ChatMessage {
    	String message;
    	EntityPlayer sender;
    }
    private ConcurrentLinkedQueue<ChatMessage> msgqueue = new ConcurrentLinkedQueue<ChatMessage>();
    
    public class ChatHandler {
		@SubscribeEvent
		public void handleChat(ServerChatEvent event) {
		    String msg = event.message;
            if(!msg.startsWith("/")) {
                ChatMessage cm = new ChatMessage();
                cm.message = msg;
                cm.sender = event.player;
                msgqueue.add(cm);
            }
		}
    }
    
    private static class WorldBusyRecord {
        long last_ts;
        Ticket ticket;
    }
    private static HashMap<Integer, WorldBusyRecord> busy_worlds = new HashMap<Integer, WorldBusyRecord>();
    
    private void setBusy(World w) {
        setBusy(w, null);
    }
    static void setBusy(World w, Ticket t) {
        if(w == null) return;
        if (!DynmapMod.useforcedchunks) return;
        WorldBusyRecord wbr = busy_worlds.get(w.provider.dimensionId);
        if(wbr == null) {   /* Not busy, make ticket and keep spawn loaded */
            Debug.debug("World " + w.getWorldInfo().getWorldName() + "/"+ w.provider.getDimensionName() + " is busy");
            wbr = new WorldBusyRecord();
            if(t != null)
                wbr.ticket = t;
            else
                wbr.ticket = ForgeChunkManager.requestTicket(DynmapMod.instance, w, ForgeChunkManager.Type.NORMAL);
            if(wbr.ticket != null) {
                ChunkCoordinates cc = w.getSpawnPoint();
                ChunkCoordIntPair ccip = new ChunkCoordIntPair(cc.posX >> 4, cc.posZ >> 4);
                ForgeChunkManager.forceChunk(wbr.ticket, ccip);
                busy_worlds.put(w.provider.dimensionId, wbr);  // Add to busy list
            }
        }
        wbr.last_ts = System.nanoTime();
    }

    private void doIdleOutOfWorlds() {
        if (!DynmapMod.useforcedchunks) return;
        long ts = System.nanoTime() - worldIdleTimeoutNS;
        for (Iterator<WorldBusyRecord> itr = busy_worlds.values().iterator(); itr.hasNext(); ) {
            WorldBusyRecord wbr = itr.next();
            try {
                if (wbr.last_ts < ts) {
                    World w = wbr.ticket.world;
                    Debug.debug("World " + w.getWorldInfo().getWorldName() + "/" + wbr.ticket.world.provider.getDimensionName() + " is idle");
                    ForgeChunkManager.releaseTicket(wbr.ticket);    // Release hold on world
                    itr.remove();
                }
            } catch (NullPointerException npe) {
                Debug.error("Failed to release idle chunk in " + wbr.ticket.world.getWorldInfo().getWorldName(), npe);
                itr.remove();
            }
        }
    }
    
    public static class OurLog implements DynmapLogger {
        Logger log;
        public static final String DM = "[Dynmap] ";
        OurLog() {
            log = LogManager.getLogger("Dynmap");
        }
        @Override
        public void info(String s) {
            log.info(DM + s);
        }

        @Override
        public void severe(Throwable t) {
            log.fatal(t);
        }

        @Override
        public void severe(String s) {
            log.fatal(DM + s);
        }

        @Override
        public void severe(String s, Throwable t) {
            log.fatal(DM + s, t);
        }

        @Override
        public void verboseinfo(String s) {
            log.info(DM + s);
        }

        @Override
        public void warning(String s) {
            log.warn(DM + s);
        }

        @Override
        public void warning(String s, Throwable t) {
            log.warn(DM + s, t);
        }
    }
    
    public DynmapPlugin()
    {
        plugin = this;
        
        displayName = null;
        try {
            displayName = EntityPlayerMP.class.getField("displayName");
        } catch (SecurityException e) {
        } catch (NoSuchFieldException e) {
        }
    }

    public boolean isOp(String player) {
    	player = player.toLowerCase();
    	return (server.getConfigurationManager().func_152603_m().func_152700_a(player) != null) ||
    			(server.isSinglePlayer() && player.equalsIgnoreCase(server.getServerOwner()));
    }
    
    private boolean hasPerm(ICommandSender sender, String permission) {
        PermissionsHandler ph = PermissionsHandler.getHandler();
        if(ph != null) {
            if((sender instanceof EntityPlayer) && ph.hasPermission(sender.getCommandSenderName(), permission)) {
                return true;
            }
        }
        return permissions.has(sender, permission);
    }
    
    private boolean hasPermNode(ICommandSender sender, String permission) {
        PermissionsHandler ph = PermissionsHandler.getHandler();
        if(ph != null) {
            if((sender instanceof EntityPlayer) && ph.hasPermissionNode(sender.getCommandSenderName(), permission)) {
                return true;
            }
        }
        return permissions.hasPermissionNode(sender, permission);
    } 

    private Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        Set<String> rslt = null;
        PermissionsHandler ph = PermissionsHandler.getHandler();
        if(ph != null) {
            rslt = ph.hasOfflinePermissions(player, perms);
        }
        Set<String> rslt2 = hasOfflinePermissions(player, perms);
        if((rslt != null) && (rslt2 != null)) {
            Set<String> newrslt = new HashSet<String>(rslt);
            newrslt.addAll(rslt2);
            rslt = newrslt;
        }
        else if(rslt2 != null) {
            rslt = rslt2;
        }
        return rslt;
    }
    private boolean hasOfflinePermission(String player, String perm) {
        PermissionsHandler ph = PermissionsHandler.getHandler();
        if(ph != null) {
            if(ph.hasOfflinePermission(player, perm)) {
                return true;
            }
        }
        return permissions.hasOfflinePermission(player, perm);
    }

    /**
     * Server access abstraction class
     */
    public class ForgeServer extends DynmapServerInterface
    {
        /* Server thread scheduler */
        private Object schedlock = new Object();
        private long cur_tick;
        private long next_id;
        private long cur_tick_starttime;
        private PriorityQueue<TaskRecord> runqueue = new PriorityQueue<TaskRecord>();

        public ForgeServer() {
        }
        
        @Override
        public int getBlockIDAt(String wname, int x, int y, int z) {
        	DynmapWorld dw = this.getWorldByName(wname);
        	if (dw != null) {
        		World w = ((ForgeWorld)dw).getWorld();
        		if((w != null) && (w.getChunkProvider().chunkExists(x >> 4,  z >> 4))) {
        			return getBlockID(w, x, y, z);
        		}
        	}
            return -1;
        }

        @Override
        public void scheduleServerTask(Runnable run, long delay)
        {
            TaskRecord tr = new TaskRecord();
            tr.future = new FutureTask<Object>(run, null);

            /* Add task record to queue */
            synchronized (schedlock)
            {
                tr.id = next_id++;
                tr.ticktorun = cur_tick + delay;
                runqueue.add(tr);
            }
        }
        @Override
        public DynmapPlayer[] getOnlinePlayers()
        {
            if(server.getConfigurationManager() == null)
                return new DynmapPlayer[0];
            List<?> playlist = server.getConfigurationManager().playerEntityList;
            int pcnt = playlist.size();
            DynmapPlayer[] dplay = new DynmapPlayer[pcnt];

            for (int i = 0; i < pcnt; i++)
            {
                EntityPlayer p = (EntityPlayer)playlist.get(i);
                dplay[i] = getOrAddPlayer(p);
            }

            return dplay;
        }
        @Override
        public void reload()
        {
            plugin.onDisable();
            plugin.onEnable();
            plugin.onStart();
        }
        @Override
        public DynmapPlayer getPlayer(String name)
        {
            List<?> players = server.getConfigurationManager().playerEntityList;

            for (Object o : players)
            {
                EntityPlayer p = (EntityPlayer)o;

                if (p.getCommandSenderName().equalsIgnoreCase(name))
                {
                    return getOrAddPlayer(p);
                }
            }

            return null;
        }
        @Override
        public Set<String> getIPBans()
        {
            BanList bl = server.getConfigurationManager().getBannedIPs();
            Set<String> ips = new HashSet<String>();

            for (String s : bl.func_152685_a()) {
                ips.add(s);
            }
            
            return ips;
        }
        @Override
        public <T> Future<T> callSyncMethod(Callable<T> task) {
        	return callSyncMethod(task, 0);
        }
        public <T> Future<T> callSyncMethod(Callable<T> task, long delay)
        {
            TaskRecord tr = new TaskRecord();
            FutureTask<T> ft = new FutureTask<T>(task);
            tr.future = ft;

            /* Add task record to queue */
            synchronized (schedlock)
            {
                tr.id = next_id++;
                tr.ticktorun = cur_tick + delay;
                runqueue.add(tr);
            }

            return ft;
        }
        @Override
        public String getServerName()
        {
            String sn;
            if (server.isSinglePlayer())
                sn = "Integrated";
            else
                sn = server.getServerHostname();
        	if(sn == null) sn = "Unknown Server";
        	return sn;
        }
        @Override
        public boolean isPlayerBanned(String pid)
        {
            UserListBans bl = server.getConfigurationManager().func_152608_h();
            return bl.func_152703_a(pid) != null;
        }
        
        @Override
        public String stripChatColor(String s)
        {
            return patternControlCode.matcher(s).replaceAll("");
        }
        private Set<EventType> registered = new HashSet<EventType>();
        @Override
        public boolean requestEventNotification(EventType type)
        {
            if (registered.contains(type))
            {
                return true;
            }

            switch (type)
            {
                case WORLD_LOAD:
                case WORLD_UNLOAD:
                    /* Already called for normal world activation/deactivation */
                    break;

                case WORLD_SPAWN_CHANGE:
                    /*TODO
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onSpawnChange(SpawnChangeEvent evt) {
                            DynmapWorld w = new BukkitWorld(evt.getWorld());
                            core.listenerManager.processWorldEvent(EventType.WORLD_SPAWN_CHANGE, w);
                        }
                    }, DynmapPlugin.this);
                    */
                    break;

                case PLAYER_JOIN:
                case PLAYER_QUIT:
                    /* Already handled */
                    break;

                case PLAYER_BED_LEAVE:
                    /*TODO
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onPlayerBedLeave(PlayerBedLeaveEvent evt) {
                            DynmapPlayer p = new BukkitPlayer(evt.getPlayer());
                            core.listenerManager.processPlayerEvent(EventType.PLAYER_BED_LEAVE, p);
                        }
                    }, DynmapPlugin.this);
                    */
                    break;

                case PLAYER_CHAT:
                	if (chathandler == null) {
                		chathandler = new ChatHandler();
                		MinecraftForge.EVENT_BUS.register(chathandler);
                	}
                    break;

                case BLOCK_BREAK:
                    /*TODO
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onBlockBreak(BlockBreakEvent evt) {
                            if(evt.isCancelled()) return;
                            Block b = evt.getBlock();
                            if(b == null) return;
                            Location l = b.getLocation();
                            core.listenerManager.processBlockEvent(EventType.BLOCK_BREAK, b.getType().getId(),
                                    BukkitWorld.normalizeWorldName(l.getWorld().getName()), l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }, DynmapPlugin.this);
                    */
                    break;

                case SIGN_CHANGE:
                    /*TODO
                    pm.registerEvents(new Listener() {
                        @EventHandler(priority=EventPriority.MONITOR)
                        public void onSignChange(SignChangeEvent evt) {
                            if(evt.isCancelled()) return;
                            Block b = evt.getBlock();
                            Location l = b.getLocation();
                            String[] lines = evt.getLines();
                            DynmapPlayer dp = null;
                            Player p = evt.getPlayer();
                            if(p != null) dp = new BukkitPlayer(p);
                            core.listenerManager.processSignChangeEvent(EventType.SIGN_CHANGE, b.getType().getId(),
                                    BukkitWorld.normalizeWorldName(l.getWorld().getName()), l.getBlockX(), l.getBlockY(), l.getBlockZ(), lines, dp);
                        }
                    }, DynmapPlugin.this);
                    */
                    break;

                default:
                    Log.severe("Unhandled event type: " + type);
                    return false;
            }

            registered.add(type);
            return true;
        }
        @Override
        public boolean sendWebChatEvent(String source, String name, String msg)
        {
            return DynmapCommonAPIListener.fireWebChatEvent(source, name, msg);
        }
        @Override
        public void broadcastMessage(String msg)
        {
            IChatComponent ichatcomponent = new ChatComponentText(msg);
            MinecraftServer.getServer().getConfigurationManager().sendChatMsg(ichatcomponent);
            Log.info(stripChatColor(msg));
        }
        @Override
        public String[] getBiomeIDs()
        {
            BiomeMap[] b = BiomeMap.values();
            String[] bname = new String[b.length];

            for (int i = 0; i < bname.length; i++)
            {
                bname[i] = b[i].toString();
            }

            return bname;
        }
        @Override
        public double getCacheHitRate()
        {
            if(sscache != null)
                return sscache.getHitRate();
            return 0.0;
        }
        @Override
        public void resetCacheStats()
        {
            if(sscache != null)
                sscache.resetStats();
        }
        @Override
        public DynmapWorld getWorldByName(String wname)
        {
        	return DynmapPlugin.this.getWorldByName(wname);
        }
        @Override
        public DynmapPlayer getOfflinePlayer(String name)
        {
            /*
            OfflinePlayer op = getServer().getOfflinePlayer(name);
            if(op != null) {
                return new BukkitPlayer(op);
            }
            */
            return null;
        }
        @Override
        public Set<String> checkPlayerPermissions(String player, Set<String> perms)
        {
            ServerConfigurationManager scm = MinecraftServer.getServer().getConfigurationManager();
            if (scm == null) return Collections.emptySet();
            UserListBans bl = scm.func_152608_h();
            if (bl == null) return Collections.emptySet();
            if(bl.func_152703_a(player) != null) {
                return Collections.emptySet();
            }
            Set<String> rslt = hasOfflinePermissions(player, perms);
            if (rslt == null) {
                rslt = new HashSet<String>();
                if(plugin.isOp(player)) {
                    rslt.addAll(perms);
                }
            }
            return rslt;
        }
        @Override
        public boolean checkPlayerPermission(String player, String perm)
        {
            ServerConfigurationManager scm = MinecraftServer.getServer().getConfigurationManager();
            if (scm == null) return false;
            UserListBans bl = scm.func_152608_h();
            if (bl == null) return false;
            if(bl.func_152703_a(player) != null) {
                return false;
            }
            return hasOfflinePermission(player, perm);
        }
        /**
         * Render processor helper - used by code running on render threads to request chunk snapshot cache from server/sync thread
         */
        @Override
        public MapChunkCache createMapChunkCache(DynmapWorld w, List<DynmapChunk> chunks,
                boolean blockdata, boolean highesty, boolean biome, boolean rawbiome)
        {
            ForgeMapChunkCache c = (ForgeMapChunkCache) w.getChunkCache(chunks);
            if(c == null) {
            	return null;
            }
            if (w.visibility_limits != null)
            {
                for (VisibilityLimit limit: w.visibility_limits)
                {
                    c.setVisibleRange(limit);
                }

                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }

            if (w.hidden_limits != null)
            {
                for (VisibilityLimit limit: w.hidden_limits)
                {
                    c.setHiddenRange(limit);
                }

                c.setHiddenFillStyle(w.hiddenchunkstyle);
            }

            if (c.setChunkDataTypes(blockdata, biome, highesty, rawbiome) == false)
            {
                Log.severe("CraftBukkit build does not support biome APIs");
            }

            if (chunks.size() == 0)     /* No chunks to get? */
            {
                c.loadChunks(0);
                return c;
            }
            
            //Now handle any chunks in server thread that are already loaded (on server thread)
            final ForgeMapChunkCache cc = c;
            Future<Boolean> f = this.callSyncMethod(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    // Update busy state on world
                    ForgeWorld fw = (ForgeWorld)cc.getWorld();
                    setBusy(fw.getWorld());
                    cc.getLoadedChunks();
                    return true;
                }
            }, 0);
            try {
                f.get();
            }
            catch (CancellationException cx) {
                return null;
            }
            catch (ExecutionException xx) {
                Log.severe("Exception while loading chunks", xx.getCause());
                return null;
            }
            catch (Exception ix) {
                Log.severe(ix);
                return null;
            }
            if(w.isLoaded() == false) {
            	return null;
            }
            // Now, do rest of chunk reading from calling thread
            c.readChunks(chunks.size());
            
            return c;
        }
        @Override
        public int getMaxPlayers()
        {
            if(server.getConfigurationManager() != null) 
                return server.getMaxPlayers();
            else
                return 0;
        }
        @Override
        public int getCurrentPlayers()
        {
            if(server.getConfigurationManager() != null) 
                return server.getConfigurationManager().playerEntityList.size();
            else
                return 0;
        }

        @SubscribeEvent
		public void tickEvent(TickEvent.ServerTickEvent event)  {
            if (event.phase == TickEvent.Phase.START) {
                return;
            }
            cur_tick_starttime = System.nanoTime();
            long elapsed = cur_tick_starttime - lasttick;
            lasttick = cur_tick_starttime;
            avgticklen = ((avgticklen * 99) / 100) + (elapsed / 100);
            tps = (double)1E9 / (double)avgticklen;
            // Tick core
            if (core != null) {
                core.serverTick(tps);
            }

            boolean done = false;
            TaskRecord tr = null;

            while(!blockupdatequeue.isEmpty()) {
                BlockUpdateRec r = blockupdatequeue.remove();
                int id = 0;
                int meta = 0;
                if((r.w != null) && r.w.getChunkProvider().chunkExists(r.x >> 4,  r.z >> 4)) {
                    id = getBlockID(r.w, r.x, r.y, r.z);
                    meta = r.w.getBlockMetadata(r.x, r.y, r.z);
                }
                if(!org.dynmap.hdmap.HDBlockModels.isChangeIgnoredBlock(id,  meta)) {
                    if(onblockchange_with_id)
                        mapManager.touch(r.wid, r.x, r.y, r.z, "blockchange[" + id + ":" + meta + "]");
                    else
                        mapManager.touch(r.wid, r.x, r.y, r.z, "blockchange");
                }
            }

            long now;

            synchronized(schedlock) {
                cur_tick++;
                now = System.nanoTime();
                tr = runqueue.peek();
                /* Nothing due to run */
                if((tr == null) || (tr.ticktorun > cur_tick) || ((now - cur_tick_starttime) > perTickLimit)) {
                    done = true;
                }
                else {
                    tr = runqueue.poll();
                }
            }
            while (!done) {
                tr.future.run();

                synchronized(schedlock) {
                    tr = runqueue.peek();
                    now = System.nanoTime();
                    /* Nothing due to run */
                    if((tr == null) || (tr.ticktorun > cur_tick) || ((now - cur_tick_starttime) > perTickLimit)) {
                        done = true;
                    }
                    else {
                        tr = runqueue.poll();
                    }
                }
            }
            while(!msgqueue.isEmpty()) {
                ChatMessage cm = msgqueue.poll();
                DynmapPlayer dp = null;
                if(cm.sender != null)
                    dp = getOrAddPlayer(cm.sender);
                else
                    dp = new ForgePlayer(null);

                core.listenerManager.processChatEvent(EventType.PLAYER_CHAT, dp, cm.message);
            }
            /* Check for idle worlds */
            if((cur_tick % 20) == 0) {
                doIdleOutOfWorlds();
            }
		}

		@Override
		public boolean isModLoaded(String name) {
			boolean loaded = Loader.isModLoaded(name);
			if (loaded) {
                modsused.add(name);
			}
			return loaded;
		}
		@Override
		public String getModVersion(String name) {
	          ModContainer mod = Loader.instance().getIndexedModList().get(name);
	          if (mod == null) return null;
	          return mod.getVersion();
		}

        @Override
        public double getServerTPS() {
            return tps;
        }
        
        @Override
        public String getServerIP() {
            if (server.isSinglePlayer())
                return "0.0.0.0";
            else
                return server.getServerHostname();
        }
        @Override
        public File getModContainerFile(String name) {
            ModContainer mod = Loader.instance().getIndexedModList().get(name);
            if (mod == null) return null;
            return mod.getSource();
        }
        @Override
        public List<String> getModList() {
            return new ArrayList<String>(Loader.instance().getIndexedModList().keySet());
        }

        @Override
        public Map<Integer, String> getBlockIDMap() {
            Map<Integer, String> map = new HashMap<Integer, String>();
            for (int i = 0; i < 65536; i++) {
                Block b = getBlockByID(i);
                if (b == null) continue;
                String blockname = GameData.getBlockRegistry().getNameForObject(b);
                if (blockname != null) {
                    map.put(i, blockname);
                }
            }
            return map;
        }

        @Override
        public InputStream openResource(String modid, String rname) {
            if (modid != null) {
                ModContainer mc = Loader.instance().getIndexedModList().get(modid);
                Object mod = mc.getMod();
                if (mod != null) {
                    InputStream is = mod.getClass().getClassLoader().getResourceAsStream(rname);
                    if (is != null) {
                        return is;
                    }
                }
            }
            List<ModContainer> mcl = Loader.instance().getModList();
            for (ModContainer mc : mcl) {
                Object mod = mc.getMod();
                if (mod == null) continue;
                InputStream is = mod.getClass().getClassLoader().getResourceAsStream(rname);
                if (is != null) {
                    return is;
                }
            }
            return null;
        }
        /**
         * Get block unique ID map (module:blockid)
         */
        @Override
        public Map<String, Integer> getBlockUniqueIDMap() {
            HashMap<String, Integer> map = new HashMap<String, Integer>();
            for (int i = 0; i < 65536; i++) {
                Block b = getBlockByID(i);
                if (b == null) continue;
                String blockname = GameData.getBlockRegistry().getNameForObject(b);
                if (blockname != null) {
                    map.put(blockname, i);
                }
            }
            return map;
        }
        /**
         * Get item unique ID map (module:itemid)
         */
        @Override
        public Map<String, Integer> getItemUniqueIDMap() {
            HashMap<String, Integer> map = new HashMap<String, Integer>();
            for (int i = 0; i < 32000; i++) {
                Item itm = getItemByID(i);
                if (itm == null) continue;
                UniqueIdentifier ui = null;
                try {
                    ui = GameRegistry.findUniqueIdentifierFor(itm);
                } catch (Exception x) {
                    Log.warning("Exception caught reading unique ID for item " + i);
                }
                if (ui != null) {
                    map.put(ui.modId + ":" + ui.name, i);
                }
            }
            return map;
        }

    }
    private static final Gson gson = new GsonBuilder().create();

    public class TexturesPayload {
        public long timestamp;
        public String profileId;
        public String profileName;
        public boolean isPublic;
        public Map<String, ProfileTexture> textures;

    }
    public class ProfileTexture {
        public String url;
    }
    
    /**
     * Player access abstraction class
     */
    public class ForgePlayer extends ForgeCommandSender implements DynmapPlayer
    {
        private EntityPlayer player;
        private final String skinurl;
        private final UUID uuid;


        public ForgePlayer(EntityPlayer p)
        {
            player = p;
            String url = null;
        	if (player != null) {
        		uuid = player.getUniqueID();
        		GameProfile prof = player.getGameProfile();
        		if (prof != null) {
        	        Property textureProperty = Iterables.getFirst(prof.getProperties().get("textures"), null);

        	        if (textureProperty != null) {
        	        	TexturesPayload result = null;
        	        	try {
        	        		String json = new String(Base64.decodeBase64(textureProperty.getValue()), Charsets.UTF_8);
        	        		result = gson.fromJson(json, TexturesPayload.class);
        	        	} catch (JsonParseException e) {
        	        	}
        	        	if ((result != null) && (result.textures != null) && (result.textures.containsKey("SKIN"))) {
        	        		url = result.textures.get("SKIN").url;
        	        	}
        			}
        		}
        	}
        	else {
        		uuid = null;
        	}
        	skinurl = url;
        }
        @Override
        public boolean isConnected()
        {
            return true;
        }
        @Override
        public String getName()
        {
        	if(player != null)
        		return player.getCommandSenderName();
        	else
        		return "[Server]";
        }
        @Override
        public String getDisplayName()
        {
        	if(player != null) {
        	    if (displayName != null) {
        	        try {
                        return (String) displayName.get(player);
                    } catch (IllegalArgumentException e) {
                    } catch (IllegalAccessException e) {
                    }
        	    }
        		return player.getDisplayName();
        	}
        	else
        		return "[Server]";
        }
        @Override
        public boolean isOnline()
        {
            return true;
        }
        @Override
        public DynmapLocation getLocation()
        {
            if (player == null)
            {
                return null;
            }

            return toLoc(player.worldObj, player.posX, player.posY, player.posZ);
        }
        @Override
        public String getWorld()
        {
            if (player == null)
            {
                return null;
            }

            if (player.worldObj != null)
            {
                return DynmapPlugin.this.getWorld(player.worldObj).getName();
            }

            return null;
        }
        @Override
        public InetSocketAddress getAddress()
        {
            if((player != null) && (player instanceof EntityPlayerMP)) {
            	NetHandlerPlayServer nsh = ((EntityPlayerMP)player).playerNetServerHandler;
            	if((nsh != null) && (getNetworkManager(nsh) != null)) {
            		SocketAddress sa = getNetworkManager(nsh).getSocketAddress();
            		if(sa instanceof InetSocketAddress) {
            			return (InetSocketAddress)sa;
            		}
            	}
            }
            return null;
        }
        @Override
        public boolean isSneaking()
        {
            if (player != null)
            {
                return player.isSneaking();
            }

            return false;
        }
        @Override
        public double getHealth()
        {
            if (player != null)
            {
                double h = player.getHealth();
                if(h > 20) h = 20;
                return h;  // Scale to 20 range
            }
            else
            {
                return 0;
            }
        }
        @Override
        public int getArmorPoints()
        {
            if (player != null)
            {
                return player.getTotalArmorValue();
            }
            else
            {
                return 0;
            }
        }
        @Override
        public DynmapLocation getBedSpawnLocation()
        {
            return null;
        }
        @Override
        public long getLastLoginTime()
        {
            return 0;
        }
        @Override
        public long getFirstLoginTime()
        {
            return 0;
        }
        @Override
        public boolean hasPrivilege(String privid)
        {
            if(player != null)
                return hasPerm(player, privid);
            return false;
        }
        @Override
        public boolean isOp()
        {
        	return DynmapPlugin.this.isOp(player.getCommandSenderName());
    	}
        @Override
        public void sendMessage(String msg)
        {
            IChatComponent ichatcomponent = new ChatComponentText(msg);
            player.addChatComponentMessage(ichatcomponent);
        }
        @Override
        public boolean isInvisible() {
        	if(player != null) {
        		return player.isPotionActive(Potion.invisibility);
        	}
        	return false;
        }
        @Override
        public int getSortWeight() {
            Integer wt = sortWeights.get(getName());
            if (wt != null)
                return wt;
            return 0;
        }
        @Override
        public void setSortWeight(int wt) {
            if (wt == 0) {
                sortWeights.remove(getName());
            }
            else {
                sortWeights.put(getName(), wt);
            }
        }
        @Override
        public boolean hasPermissionNode(String node) {
            if(player != null)
                return hasPermNode(player, node);
            return false;
        }
        @Override
        public String getSkinURL() {
        	return skinurl;
        }
        @Override
        public UUID getUUID() {
        	return uuid;
        }
    }
    /* Handler for generic console command sender */
    public class ForgeCommandSender implements DynmapCommandSender
    {
        private ICommandSender sender;

        protected ForgeCommandSender() {
        	sender = null;
        }

        public ForgeCommandSender(ICommandSender send)
        {
            sender = send;
        }

        @Override
        public boolean hasPrivilege(String privid)
        {
        	return true;
        }

        @Override
        public void sendMessage(String msg)
        {
        	if(sender != null) {
                IChatComponent ichatcomponent = new ChatComponentText(msg);
        	    sender.addChatMessage(ichatcomponent);
        	}
        }

        @Override
        public boolean isConnected()
        {
            return false;
        }
        @Override
        public boolean isOp()
        {
            return true;
        }
        @Override
        public boolean hasPermissionNode(String node) {
            return true;
        } 
    }

    public void loadExtraBiomes(String mcver) {
    	int cnt = 0;
        BiomeMap.loadWellKnownByVersion(mcver);

    	BiomeGenBase[] list = getBiomeList();
    	
        for(int i = 0; i < list.length; i++) {
            BiomeGenBase bb = list[i];
            if(bb != null) {
                String id = bb.biomeName;
                float tmp = bb.temperature, hum = bb.rainfall;
                BiomeMap bmap = BiomeMap.byBiomeID(i);
                if (bmap.isDefault()) {
                    BiomeMap m = new BiomeMap(i, id, tmp, hum);
                    Log.verboseinfo("Add custom biome [" + m.toString() + "] (" + i + ")");
                    cnt++;
                }
                else {
                    bmap.setTemperature(tmp);
                    bmap.setRainfall(hum);
                }
            }
        }
        if(cnt > 0)
        	Log.info("Added " + cnt + " custom biome mappings");
    }

    private String[] getBiomeNames() {
        BiomeGenBase[] list = getBiomeList();
        String[] lst = new String[list.length];
        for(int i = 0; i < list.length; i++) {
            BiomeGenBase bb = list[i];
            if (bb != null) {
                lst[i] = bb.biomeName;
            }
        }
        return lst;
    }
    
    private String[] getBlockNames() {
        String[] lst = new String[65536];
        for(int i = 0; i < lst.length; i++) {
            Block b = getBlockByID(i);
            if(b != null) {
                lst[i] = getBlockUnlocalizedName(b);
                if(lst[i].startsWith("tile.")) {
                    lst[i] = lst[i].substring(5);
                }
            }
        }
        return lst;
    }

    private int[] getBlockMaterialMap() {
        int[] map = new int[65536];
        ArrayList<Material> mats = new ArrayList<Material>();
        for (int i = 0; i < map.length; i++) {
            Block b = getBlockByID(i);
            if(b != null) {
                Material mat = getBlockMaterial(b);
                if (mat != null) {
                    map[i] = mats.indexOf(mat);
                    if (map[i] < 0) {
                        map[i] = mats.size();
                        mats.add(mat);
                    }
                }
                else {
                    map[i] = -1;
                }
            }
        }
        return map;
    }

    public void onEnable()
    {
        server = MinecraftServer.getServer();

        /* Get MC version */
        String mcver = server.getMinecraftVersion();

        /* Load extra biomes */
        loadExtraBiomes(mcver);
        /* Set up player login/quit event handler */
        registerPlayerLoginListener();
        /* Initialize permissions handler */
        permissions = FilePermissions.create();
        if(permissions == null) {
            permissions = new OpPermissions(new String[] { "webchat", "marker.icons", "marker.list", "webregister", "stats", "hide.self", "show.self" });
        }
        /* Get and initialize data folder */
        File dataDirectory = new File("dynmap");

        if (dataDirectory.exists() == false)
        {
            dataDirectory.mkdirs();
        }

        /* Instantiate core */
        if (core == null)
        {
            core = new DynmapCore();
        }

        /* Inject dependencies */
        core.setPluginJarFile(DynmapMod.jarfile);
        core.setPluginVersion(Tags.GRADLETOKEN_VERSION);
        core.setMinecraftVersion(mcver);
        core.setDataFolder(dataDirectory);
        core.setServer(fserver);
        ForgeMapChunkCache.init();
        core.setTriggerDefault(TRIGGER_DEFAULTS);
        core.setBiomeNames(getBiomeNames());
        core.setBlockNames(getBlockNames());
        core.setBlockMaterialMap(getBlockMaterialMap());

        if(!core.initConfiguration(null))
        {
        	return;
        }
        DynmapCommonAPIListener.apiInitialized(core);
    }
    
    public void onStart() {
        /* Enable core */
        if (!core.enableCore(null))
        {
            return;
        }
        core_enabled = true;
        // Get per tick time limit
        perTickLimit = core.getMaxTickUseMS() * 1000000;
        // Prep TPS
        lasttick = System.nanoTime();
        tps = 20.0;
        
        /* Register tick handler */
        if(!tickregistered) {
            FMLCommonHandler.instance().bus().register(fserver);
            tickregistered = true;
        }

        playerList = core.playerList;
        sscache = new SnapshotCache(core.getSnapShotCacheSize(), core.useSoftRefInSnapShotCache());
        /* Get map manager from core */
        mapManager = core.getMapManager();

        /* Load saved world definitions */
        loadWorlds();
        
        /* Initialized the currently loaded worlds */
        if(server.worldServers != null) { 
            for (WorldServer world : server.worldServers) {
                ForgeWorld w = this.getWorld(world);
                if(DimensionManager.getWorld(world.provider.dimensionId) == null) { /* If not loaded */
                    w.setWorldUnloaded();
                }
            }
        }
        for(ForgeWorld w : worlds.values()) {
            if (core.processWorldLoad(w)) {   /* Have core process load first - fire event listeners if good load after */
                if(w.isLoaded()) {
                    core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
                }
            }
        }
        core.updateConfigHashcode();

        /* Register our update trigger events */
        registerEvents();
        Log.info("Register events");
        /* Register command hander */
        ICommandManager cm = server.getCommandManager();

        if(cm instanceof CommandHandler) {
        	CommandHandler scm = (CommandHandler)cm;
            scm.registerCommand(new DynmapCommand(this));
            scm.registerCommand(new DmapCommand(this));
            scm.registerCommand(new DmarkerCommand(this));
            scm.registerCommand(new DynmapExpCommand(this));
            scm.registerCommand(new GwmCommand(this));
            Log.info("Register commands");
        }
        /* Submit metrics to mcstats.org */
        initMetrics();

        //DynmapCommonAPIListener.apiInitialized(core);

        Log.info("Enabled");
    }

    public void onDisable()
    {
        DynmapCommonAPIListener.apiTerminated();

    	//if (metrics != null) {
    	//	metrics.stop();
    	//	metrics = null;
    	//}
    	/* Save worlds */
        saveWorlds();

        /* Purge tick queue */
        fserver.runqueue.clear();
        
        /* Disable core */
        core.disableCore();
        core_enabled = false;

        if (sscache != null)
        {
            sscache.cleanup();
            sscache = null;
        }
        
        Log.info("Disabled");
    }

    void onCommand(ICommandSender sender, String cmd, String[] args)
    {
        DynmapCommandSender dsender;

        if (sender instanceof EntityPlayer)
        {
            dsender = getOrAddPlayer((EntityPlayer)sender);
        }
        else
        {
            dsender = new ForgeCommandSender(sender);
        }

        core.processCommand(dsender, cmd, cmd, args);
    }

    private DynmapLocation toLoc(World worldObj, double x, double y, double z)
    {
        return new DynmapLocation(DynmapPlugin.this.getWorld(worldObj).getName(), x, y, z);
    }

    public class PlayerTracker {
		@SubscribeEvent
		public void onPlayerLogin(PlayerLoggedInEvent event) {			
			if(!core_enabled) return;
            final DynmapPlayer dp = getOrAddPlayer(event.player);
            /* This event can be called from off server thread, so push processing there */
            core.getServer().scheduleServerTask(new Runnable() {
                public void run() {
                    core.listenerManager.processPlayerEvent(EventType.PLAYER_JOIN, dp);
                }
            }, 2);
		}
        @SubscribeEvent
		public void onPlayerLogout(PlayerLoggedOutEvent event) {
			if(!core_enabled) return;
            final DynmapPlayer dp = getOrAddPlayer(event.player);
            final String name = event.player.getCommandSenderName();
            /* This event can be called from off server thread, so push processing there */
            core.getServer().scheduleServerTask(new Runnable() {
                public void run() {
                    core.listenerManager.processPlayerEvent(EventType.PLAYER_QUIT, dp);
                    players.remove(name);
                }
            }, 0);
		}
        @SubscribeEvent
		public void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
            if(!core_enabled) return;
            getOrAddPlayer(event.player);	// Freshen player object reference

            try {
                MinecraftServer server = MinecraftServer.getServer();
                WorldServer ws = server.worldServerForDimension(event.toDim);
                if (ws != null) {
                    String name = ForgeWorld.getWorldName(ws);
                    core.visitWorld(name);
                }
            }
            catch (Exception ex){
                Log.severe("Exception when trying to visit dimension " + event.toDim + "!");
            }
		}
        @SubscribeEvent
		public void onPlayerRespawn(PlayerRespawnEvent event) {
            if(!core_enabled) return;
            getOrAddPlayer(event.player);	// Freshen player object reference
		}
    }
    private PlayerTracker playerTracker = null;
    
    private void registerPlayerLoginListener()
    {
    	if (playerTracker == null) {
    		playerTracker = new PlayerTracker();
    		FMLCommonHandler.instance().bus().register(playerTracker);
    	}
    }

    public class WorldTracker {
    	@SubscribeEvent
    	public void handleWorldLoad(WorldEvent.Load event) {
			if(!core_enabled) return;
			if(!(event.world instanceof WorldServer)) return;
            final ForgeWorld w = getWorld(event.world);
            /* This event can be called from off server thread, so push processing there */
            core.getServer().scheduleServerTask(new Runnable() {
            	public void run() {
            		if(core.processWorldLoad(w))    /* Have core process load first - fire event listeners if good load after */
            			core.listenerManager.processWorldEvent(EventType.WORLD_LOAD, w);
            	}
            }, 0);
    	}
        @SubscribeEvent
    	public void handleWorldUnload(WorldEvent.Unload event) {
			if(!core_enabled) return;
            if(!(event.world instanceof WorldServer)) return;
            final ForgeWorld fw = getWorld(event.world);
            if(fw != null) {
                /* This event can be called from off server thread, so push processing there */
                core.getServer().scheduleServerTask(new Runnable() {
                	public void run() {
                		core.listenerManager.processWorldEvent(EventType.WORLD_UNLOAD, fw);
                		core.processWorldUnload(fw);
                	}
                }, 0);
                /* Set world unloaded (needs to be immediate, since it may be invalid after event) */
                fw.setWorldUnloaded();
                /* Clean up tracker */
                WorldUpdateTracker wut = updateTrackers.remove(fw.getName());
                if(wut != null) wut.world = null;
            }
        }
        @SubscribeEvent
    	public void handleChunkLoad(ChunkEvent.Load event) {
			if(!core_enabled) return;
			if(!onchunkgenerate) return;
            if(!(event.world instanceof WorldServer)) return;
			Chunk c = event.getChunk();
			if((c != null) && (c.lastSaveTime == 0)) {	// If new chunk?
				ForgeWorld fw = getWorld(event.world, false);
				if(fw == null) {
					return;
				}
				int ymax = 0;
				ExtendedBlockStorage[] sections = c.getBlockStorageArray();
				for(int i = 0; i < sections.length; i++) {
					if((sections[i] != null) && (sections[i].isEmpty() == false)) {
						ymax = 16*(i+1);
					}
				}
				int x = c.xPosition << 4;
				int z = c.zPosition << 4;
				if(ymax > 0) {
					mapManager.touchVolume(fw.getName(), x, 0, z, x+15, ymax, z+16, "chunkgenerate");
				}
			}
    	}

        @SubscribeEvent
    	public void handleChunkPopulate(PopulateChunkEvent.Post event) {
			if(!core_enabled) return;
			if(!onchunkpopulate) return;
            if(!(event.world instanceof WorldServer)) return;
			Chunk c = event.chunkProvider.loadChunk(event.chunkX, event.chunkZ);
			int ymin = 0, ymax = 0;
			if(c != null) {
                ForgeWorld fw = getWorld(event.world, false);
                if (fw == null) return;

                ExtendedBlockStorage[] sections = c.getBlockStorageArray();
				for(int i = 0; i < sections.length; i++) {
					if((sections[i] != null) && (sections[i].isEmpty() == false)) {
						ymax = 16*(i+1);
					}
				}
				int x = c.xPosition << 4;
				int z = c.zPosition << 4;
				if(ymax > 0)
					mapManager.touchVolume(fw.getName(), x, ymin, z, x+15, ymax, z+16, "chunkpopulate");
			}
    	}
        
        @SubscribeEvent
    	public void handleCommandEvent(CommandEvent event) {
    		if(event.isCanceled()) return;
    		if(event.command.getCommandName().equals("say")) {
    			String s = "";
    			for(String p : event.parameters) {
    				s += p + " ";
    			}
    			s = s.trim();
				ChatMessage cm = new ChatMessage();
				cm.message = s;
				cm.sender = null;
				msgqueue.add(cm);
    		}
    	}
    }
    
    private boolean onblockchange = false;
    private boolean onlightingchange = false;
    private boolean onchunkpopulate = false;
    private boolean onchunkgenerate = false;
    private boolean onblockchange_with_id = false;
    
    
    public class WorldUpdateTracker implements IWorldAccess {
    	String worldid;
    	World world;
		@Override
        public void markBlockForUpdate(int x, int y, int z) {
            if(sscache != null)
                sscache.invalidateSnapshot(worldid, x, y, z);
            if(onblockchange) {
            	BlockUpdateRec r = new BlockUpdateRec();
            	r.w = world;
            	r.wid = worldid;
            	r.x = x; r.y = y; r.z = z;
            	blockupdatequeue.add(r);
            }
		}
		@Override
	    public void markBlockForRenderUpdate(int x, int y, int z) {
            if(sscache != null)
                sscache.invalidateSnapshot(worldid, x, y, z);
            if(onlightingchange) {
            	mapManager.touch(worldid, x, y, z, "lightingchange");
            }
		}
		@Override
        public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
		}
		@Override
		public void playSound(String var1, double var2, double var4,
				double var6, float var8, float var9) {
		}
        @Override
	    public void playSoundToNearExcept(EntityPlayer entityplayer, String s, double d0, double d1, double d2, float f, float f1) {
        }
		@Override
		public void spawnParticle(String var1, double var2, double var4,
				double var6, double var8, double var10, double var12) {
		}
        @Override
	    public void onEntityCreate(Entity entity) {
        }
        @Override
        public void onEntityDestroy(Entity entity) {
        }
        @Override
        public void playRecord(String s, int i, int j, int k) {
        }
        @Override
        public void playAuxSFX(EntityPlayer entityplayer, int i, int j, int k, int l, int i1) {
        }
        @Override
        public void broadcastSound(int var1, int var2, int var3, int var4, int var5) {
        }
        @Override
        public void destroyBlockPartially(int var1, int var2, int var3,
                int var4, int var5) {
        }
        @Override
        public void onStaticEntitiesChanged() {
        }
    }
    
    private WorldTracker worldTracker = null;
    private HashMap<String, WorldUpdateTracker> updateTrackers = new HashMap<String, WorldUpdateTracker>();
    
    private void registerEvents()
    {
    	if(worldTracker == null) {
    		worldTracker = new WorldTracker();
    		MinecraftForge.EVENT_BUS.register(worldTracker);
    	}
        // To trigger rendering.
        onblockchange = core.isTrigger("blockupdate");
        onlightingchange = core.isTrigger("lightingupdate");
        onchunkpopulate = core.isTrigger("chunkpopulate");
        onchunkgenerate = core.isTrigger("chunkgenerate");
        onblockchange_with_id = core.isTrigger("blockupdate-with-id");
        if(onblockchange_with_id)
        	onblockchange = true;
    }

    private ForgeWorld getWorldByName(String name) {
    	return worlds.get(name);
    }
    
    private ForgeWorld getWorld(World w) {
    	return getWorld(w, true);
    }
    
    private ForgeWorld getWorld(World w, boolean add_if_not_found) {
    	if(last_world == w) {
    		return last_fworld;
    	}
    	String wname = ForgeWorld.getWorldName(w);
    	
    	for(ForgeWorld fw : worlds.values()) {
			if(fw.getRawName().equals(wname)) {
				last_world = w;
	           	last_fworld = fw;
           		if(fw.isLoaded() == false) {
       				fw.setWorldLoaded(w);
       				// Add tracker
       	    		WorldUpdateTracker wit = new WorldUpdateTracker();
       	    		wit.worldid = fw.getName();
       	    		wit.world = w;
       	    		updateTrackers.put(fw.getName(), wit);
       	    		w.addWorldAccess(wit);
           		}
    			return fw;
    		}
    	}
    	ForgeWorld fw = null;
    	if(add_if_not_found) {
    		/* Add to list if not found */
    		fw = new ForgeWorld(w);
    		worlds.put(fw.getName(), fw);
    		// Add tracker
    		WorldUpdateTracker wit = new WorldUpdateTracker();
    		wit.worldid = fw.getName();
    		wit.world = w;
    		updateTrackers.put(fw.getName(), wit);
    		w.addWorldAccess(wit);
    	}
		last_world = w;
		last_fworld = fw;
    	return fw;
    }

    /*
    private void removeWorld(ForgeWorld fw) {
    	WorldUpdateTracker wit = updateTrackers.remove(fw.getName());
    	if(wit != null) {
    		//fw.getWorld().removeWorldAccess(wit);
    	}
    	worlds.remove(fw.getName());
    	if(last_fworld == fw) {
			last_world = null;
			last_fworld = null;
    	}
    }
    */

    private void initMetrics() {
        /*
        try {
        	Mod m = DynmapMod.class.getAnnotation(Mod.class);
            metrics = new ForgeMetrics(m.name(), m.version());
            ;
            ForgeMetrics.Graph features = metrics.createGraph("Features Used");
            
            features.addPlotter(new ForgeMetrics.Plotter("Internal Web Server") {
                @Override
                public int getValue() {
                    if (!core.configuration.getBoolean("disable-webserver", false))
                        return 1;
                    return 0;
                }
            });
            features.addPlotter(new ForgeMetrics.Plotter("Login Security") {
                @Override
                public int getValue() {
                    if(core.configuration.getBoolean("login-enabled", false))
                        return 1;
                    return 0;
                }
            });
            features.addPlotter(new ForgeMetrics.Plotter("Player Info Protected") {
                @Override
                public int getValue() {
                    if(core.player_info_protected)
                        return 1;
                    return 0;
                }
            });
            
            ForgeMetrics.Graph maps = metrics.createGraph("Map Data");
            maps.addPlotter(new ForgeMetrics.Plotter("Worlds") {
                @Override
                public int getValue() {
                    if(core.mapManager != null)
                        return core.mapManager.getWorlds().size();
                    return 0;
                }
            });
            maps.addPlotter(new ForgeMetrics.Plotter("Maps") {
                @Override
                public int getValue() {
                    int cnt = 0;
                    if(core.mapManager != null) {
                        for(DynmapWorld w :core.mapManager.getWorlds()) {
                            cnt += w.maps.size();
                        }
                    }
                    return cnt;
                }
            });
            maps.addPlotter(new ForgeMetrics.Plotter("HD Maps") {
                @Override
                public int getValue() {
                    int cnt = 0;
                    if(core.mapManager != null) {
                        for(DynmapWorld w :core.mapManager.getWorlds()) {
                            for(MapType mt : w.maps) {
                                if(mt instanceof HDMap) {
                                    cnt++;
                                }
                            }
                        }
                    }
                    return cnt;
                }
            });
            for (String mod : modsused) {
                features.addPlotter(new ForgeMetrics.Plotter(mod + " Blocks") {
                    @Override
                    public int getValue() {
                        return 1;
                    }
                });
            }
            
            metrics.start();
        } catch (IOException e) {
            // Failed to submit the stats :-(
        }
        */
    }

    private void saveWorlds() {
        File f = new File(core.getDataFolder(), "forgeworlds.yml");
        ConfigurationNode cn = new ConfigurationNode(f);
        ArrayList<HashMap<String,Object>> lst = new ArrayList<HashMap<String,Object>>();
        for(DynmapWorld fw : core.mapManager.getWorlds()) {
            HashMap<String, Object> vals = new HashMap<String, Object>();
            vals.put("name", fw.getRawName());
            vals.put("height",  fw.worldheight);
            vals.put("sealevel", fw.sealevel);
            vals.put("nether",  fw.isNether());
            vals.put("the_end",  ((ForgeWorld)fw).isTheEnd());
            vals.put("title", fw.getTitle());
            lst.add(vals);
        }
        cn.put("worlds", lst);
        cn.put("isMCPC", isMCPC);
        cn.put("useSaveFolderAsName", useSaveFolder);
        cn.put("maxWorldHeight", ForgeWorld.getMaxWorldHeight());

        cn.save();
    }
    private void loadWorlds() {
        isMCPC = MinecraftServer.getServer().getServerModName().contains("mcpc");
        File f = new File(core.getDataFolder(), "forgeworlds.yml");
        if(f.canRead() == false) {
            useSaveFolder = true;
            if (isMCPC) {
                ForgeWorld.setMCPCMapping();
            }
            else {
                ForgeWorld.setSaveFolderMapping();
            }
            return;
        }
        ConfigurationNode cn = new ConfigurationNode(f);
        cn.load();
        // If defined, use maxWorldHeight
        ForgeWorld.setMaxWorldHeight(cn.getInteger("maxWorldHeight", 256));
        
        // If existing, only switch to save folder if MCPC+
        useSaveFolder = isMCPC;
        // If setting defined, use it 
        if (cn.containsKey("useSaveFolderAsName")) {
            useSaveFolder = cn.getBoolean("useSaveFolderAsName", useSaveFolder);
        }
        if (isMCPC) {
            ForgeWorld.setMCPCMapping();
        }
        else if (useSaveFolder) {
            ForgeWorld.setSaveFolderMapping();
        }
        // If inconsistent between MCPC and non-MCPC
        if (isMCPC != cn.getBoolean("isMCPC", false)) {
            return;
        }
        List<Map<String,Object>> lst = cn.getMapList("worlds");
        if(lst == null) {
            Log.warning("Discarding bad forgeworlds.yml");
            return;
        }
        
        for(Map<String,Object> world : lst) {
            try {
                String name = (String)world.get("name");
                int height = (Integer)world.get("height");
                int sealevel = (Integer)world.get("sealevel");
                boolean nether = (Boolean)world.get("nether");
                boolean theend = (Boolean)world.get("the_end");
                String title = (String)world.get("title");
                if(name != null) {
                    ForgeWorld fw = new ForgeWorld(name, height, sealevel, nether, theend, title);
                    fw.setWorldUnloaded();
                    core.processWorldLoad(fw);
                    worlds.put(fw.getName(), fw);
                }
            } catch (Exception x) {
                Log.warning("Unable to load saved worlds from forgeworlds.yml");
                return;
            }
        }
    }
    public void serverStarted() {
        this.onStart();
        if (core != null) {
            core.serverStarted();
        }
    }
}

class DynmapCommandHandler extends CommandBase
{
    private String cmd;
    private DynmapPlugin plugin;

    public DynmapCommandHandler(String cmd, DynmapPlugin p)
    {
        this.cmd = cmd;
        this.plugin = p;
    }

    public String getCommandName()
    {
        return cmd;
    }

    public void processCommand(ICommandSender sender, String[] args)
    {
        plugin.onCommand(sender, cmd, args);
    }
    
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public String getCommandUsage(ICommandSender icommandsender) {
        return "Run /" + cmd + " help for details on using command";
    }

    @Override
    public int compareTo(Object o) {
        return super.compareTo((ICommand) o);
    }
}

class DynmapCommand extends DynmapCommandHandler {
    DynmapCommand(DynmapPlugin p) {
        super("dynmap", p);
    }
}
class DmapCommand extends DynmapCommandHandler {
    DmapCommand(DynmapPlugin p) {
        super("dmap", p);
    }
}
class DmarkerCommand extends DynmapCommandHandler {
    DmarkerCommand(DynmapPlugin p) {
        super("dmarker", p);
    }
}
class DynmapExpCommand extends DynmapCommandHandler {
    DynmapExpCommand(DynmapPlugin p) {
        super("dynmapexp", p);
    }
}

