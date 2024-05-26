package org.dynmap.forge.integration;

import com.google.common.collect.ImmutableSetMultimap;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.forge.ForgeWorld;
import org.dynmap.forge.GwmCommand;
import org.dynmap.forge.GwmConfig;
import org.dynmap.forge.GwmSubCommand;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ForgeChunkLoaderMarkers extends DynmapCommonAPIListener {
    MarkerAPI markerAPI;
    DynmapCore fullAPI;
    MarkerSet markerSet;
    static ForgeChunkLoaderMarkers INSTANCE;

    HashMap<ForgeChunkManager.Ticket, ArrayList<String>> knownTickets = new HashMap<>();
    HashSet<ForgeChunkManager.Ticket> addedTickets = new HashSet<>();
    HashSet<ForgeChunkManager.Ticket> removedTickets = new HashSet<>();
    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        INSTANCE = this;
        fullAPI = (DynmapCore)api;
        markerAPI = api.getMarkerAPI();

        GwmCommand.registerSubCommand(new UpdateChunksGwmSubCommand("updatechunks"));

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);

        markerSet = markerAPI.createMarkerSet("forgechunks", "Chunk Loading", null, false);
        markerSet.setHideByDefault(true);
    }

    void updateMarkersInt(){
        for(AreaMarker m : markerSet.getAreaMarkers())
            if(m != null)
                m.deleteMarker();

        knownTickets.clear();

        MinecraftServer server = MinecraftServer.getServer();
        if(server.worldServers != null) {
            for (WorldServer world : server.worldServers) {
                ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> loaded = ForgeChunkManager.getPersistentChunksFor(world);
                for(ForgeChunkManager.Ticket ticket : loaded.values()){
                    createMarkersForTicket(ticket);
                }
            }
        }
    }

    private void removeMarkersForTicket(ForgeChunkManager.Ticket ticket){
        if(!knownTickets.containsKey(ticket))
            return;

        ArrayList<String> markerNames = knownTickets.get(ticket);

        for(String markerName : markerNames) {
            AreaMarker m = markerSet.findAreaMarker(markerName);
            if (m != null) {
                m.deleteMarker();
            }
        }

        knownTickets.remove(ticket);
    }
    private void createMarkersForTicket(ForgeChunkManager.Ticket ticket) {
        if(knownTickets.containsKey(ticket))
            return;
        ArrayList<String> markerNames = new ArrayList<>();
        knownTickets.put(ticket, markerNames);

        String worldName = ForgeWorld.getWorldName(ticket.world);

        DynmapWorld world = fullAPI.getWorld(worldName);
        if(world == null || !world.isEnabled())
            return;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        int count = 0;
        for(ChunkCoordIntPair cp : ticket.getChunkList()){
            if(minX > cp.chunkXPos)
                minX = cp.chunkXPos;
            if(maxX < cp.chunkXPos)
                maxX = cp.chunkXPos;
            if(minZ > cp.chunkZPos)
                minZ = cp.chunkZPos;
            if(maxZ < cp.chunkZPos)
                maxZ = cp.chunkZPos;
            count++;
        }
        String label = "<b>MOD: </b>" + ticket.getModId();
        if(ticket.getPlayerName() != null){
            label += "<br /><b>Player: </b> " + ticket.getPlayerName();
        }
        NBTTagCompound modData = ticket.getModData();
        if(modData != null){
            for(Object ent : modData.func_150296_c()){
                if(ent instanceof String){
                    String strKey = (String)ent;
                    net.minecraft.nbt.NBTBase tmp = modData.getTag(strKey);
                    int nbtType = tmp.getId();
                    if(nbtType <= 8 && nbtType != 7)
                        label += "<br /><b>"+strKey+": </b> " + tmp;
                }
            }
        }

        int ticketHash = ticket.hashCode();
        String markerBaseId = worldName + "_fclm_" + ticketHash;

        if((maxX-minX + 1) * (maxZ-minZ+1) == count){
            // Rectangular shape
            double[] x = {minX * 16, maxX * 16 + 16};
            double[] z = {minZ * 16, maxZ * 16 + 16};
            org.dynmap.markers.AreaMarker m = markerSet.createAreaMarker(markerBaseId, label, true, worldName, x, z, false);
            m.setFillStyle(0.4, 0xFFFF00);
            m.setLineStyle(2, 0.5, 0xFFFF00);
            markerNames.add(markerBaseId);
        } else{
            // Arbitrary shape
            int counter = 0;
            for(ChunkCoordIntPair cp : ticket.getChunkList()){
                double[] x = {cp.chunkXPos * 16, cp.chunkXPos * 16 + 16};
                double[] z = {cp.chunkZPos * 16, cp.chunkZPos * 16 + 16};
                String id = markerBaseId + "_" + (counter++);
                org.dynmap.markers.AreaMarker m = markerSet.createAreaMarker(id, label, true, worldName, x, z, false);
                m.setFillStyle(0.4, 0xFFFF00);
                m.setLineStyle(0, 0, 0);
                markerNames.add(id);
            }
        }
    }


    public static void updateMarkers(){
        if(INSTANCE != null)
            INSTANCE.updateMarkersInt();
    }
    @SubscribeEvent
    public void onForceChunk(ForgeChunkManager.ForceChunkEvent event){
        if(GwmConfig.enableChunkLoadingMarkers)
            addedTickets.add(event.ticket);
    }
    @SubscribeEvent
    public void onUnforceChunk(ForgeChunkManager.UnforceChunkEvent event){
        if(GwmConfig.enableChunkLoadingMarkers)
            removedTickets.add(event.ticket);
    }

    @SubscribeEvent
    public void doUpdates(TickEvent.ServerTickEvent event){
        if(!removedTickets.isEmpty()){
            for(ForgeChunkManager.Ticket t : removedTickets)
                removeMarkersForTicket(t);
            removedTickets.clear();
        }
        if(!addedTickets.isEmpty()){
            for(ForgeChunkManager.Ticket t : addedTickets)
                createMarkersForTicket(t);
            addedTickets.clear();
        }
    }
    class UpdateChunksGwmSubCommand extends GwmSubCommand {

        protected UpdateChunksGwmSubCommand(String name) {
            super(name);
        }

        @Override
        protected void process(ICommandSender sender, String[] args) {
            updateMarkers();
            removedTickets.clear();
            addedTickets.clear();
        }
    }
}
