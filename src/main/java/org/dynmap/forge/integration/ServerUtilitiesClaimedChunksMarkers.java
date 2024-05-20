package org.dynmap.forge.integration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.forge.ForgeWorld;
import org.dynmap.forge.GwmCommand;
import org.dynmap.forge.GwmConfig;
import org.dynmap.forge.GwmSubCommand;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.events.chunks.ChunkModifiedEvent;
import serverutils.lib.EnumTeamStatus;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.math.ChunkDimPos;

import java.util.HashMap;

public class ServerUtilitiesClaimedChunksMarkers extends DynmapCommonAPIListener {

    static ServerUtilitiesClaimedChunksMarkers INSTANCE;
    MarkerSet markerSet;
    MarkerAPI markerAPI;

    boolean updateNeeded = true;

    HashMap<Integer, String> dimensionIdToWorldName = new HashMap<>();

    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        GwmCommand.registerSubCommand(new UpdateSUClaimsGwmSubCommand("updatesuclaims"));
        markerAPI = api.getMarkerAPI();
        markerSet = markerAPI.createMarkerSet("suclaims", "Claimed Chunks (SU)", null, false);
        markerSet.setHideByDefault(true);
        MinecraftServer server = MinecraftServer.getServer();

        for(WorldServer ws : server.worldServers){
            dimensionIdToWorldName.put(ws.provider.dimensionId, ForgeWorld.getWorldName(ws));
        }
        INSTANCE = this;
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    void update(){
        for(AreaMarker m : markerSet.getAreaMarkers())
            if(m != null)
                m.deleteMarker();

        if(ClaimedChunks.instance == null)
            return;

        for(ClaimedChunk cc : ClaimedChunks.instance.getAllChunks()){
            ChunkDimPos pos = cc.getPos();

            String worldId = dimensionIdToWorldName.get(pos.dim);

            if(worldId == null)
                continue;

            double[] x = new double[]{pos.posX*16, pos.posX*16+16};
            double[] z = new double[]{pos.posZ*16, pos.posZ*16+16};

            ForgeTeam team = cc.getTeam();
            String teamName = team.getId();
//            String title = team.getTitle().getUnformattedText();
//            if (title != null && !teamName.equals(""))
//                teamName = "[" + title + "] " + teamName;
            String label = "Claimed by <b>"+ teamName +"</b>";
            String desc = team.getDesc();
            if(desc != null && !desc.equals(""))
                label += "<br/><i>" + team.getDesc() + "</i>";
            label += "<br/><b>Founder: </b>" + team.owner.getName();
            if(team.players != null){
                for(java.util.Map.Entry<ForgePlayer, EnumTeamStatus> p : team.players.entrySet()){
                    label += "<br/>" +p.getValue()+ ": " +  p.getKey().getName();
                }
            }

            AreaMarker am = markerSet.createAreaMarker(null, label, true, worldId, x,z, false);

            if(GwmConfig.boostServerUtilitiesClaimsMarkers)
                am.setBoostFlag(true);

            am.setLineStyle(0,0,0);
            am.setFillStyle(0.2, team.getColor().getColor().rgb());
        }

        updateNeeded = false;
    }

    @SubscribeEvent
    public void onServerUtilitiesChunkModifiedEvent(ChunkModifiedEvent.Claim event){
        updateNeeded = true;
    }
    @SubscribeEvent
    public void onServerUtilitiesChunkModifiedEvent(ChunkModifiedEvent.Unclaimed event){
        updateNeeded = true;
    }
//    @SubscribeEvent
//    public void onServerUtilitiesChunkModifiedEvent(ChunkModifiedEvent event){
//        updateNeeded = true;
//    }
    @SubscribeEvent
    public void doUpdates(TickEvent.ServerTickEvent event){
        if(updateNeeded && event.phase == TickEvent.Phase.START)
            update();
    }

    class UpdateSUClaimsGwmSubCommand extends GwmSubCommand {

        protected UpdateSUClaimsGwmSubCommand(String name) {
            super(name);
        }

        @Override
        protected void process(ICommandSender sender, String[] args) {
            update();
        }
    }
}
