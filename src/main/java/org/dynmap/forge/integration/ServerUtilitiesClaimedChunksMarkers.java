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
import org.dynmap.utils.GreedyRectangleMesher;
import serverutils.ServerUtilitiesConfig;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.events.chunks.ChunkModifiedEvent;
import serverutils.lib.EnumTeamStatus;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.math.ChunkDimPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerUtilitiesClaimedChunksMarkers extends DynmapCommonAPIListener {
    private static final int CHUNK_SIZE = 16;

    static ServerUtilitiesClaimedChunksMarkers INSTANCE;
    MarkerSet markerSet;
    MarkerAPI markerAPI;

    boolean updateNeeded = true;

    HashMap<Integer, String> dimensionIdToWorldName = new HashMap<>();

    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        if(!ServerUtilitiesConfig.world.chunk_claiming)
            return;

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
        if(!ClaimedChunks.isActive())
            return;

        for(AreaMarker m : markerSet.getAreaMarkers())
            if(m != null)
                m.deleteMarker();

        if(ClaimedChunks.instance == null)
            return;

        int claimId = 1;
        List<Claimer> claimers = buildClaimers();

        for(Claimer claimer : claimers) {
            for(ClaimRectangle rectangle : claimer.rectangles) {
                double[] x = new double[]{rectangle.x1 * CHUNK_SIZE, rectangle.x2 * CHUNK_SIZE};
                double[] z = new double[]{rectangle.z1 * CHUNK_SIZE, rectangle.z2 * CHUNK_SIZE};

                AreaMarker am = markerSet.createAreaMarker("c_" + (claimId++), claimer.label, true, rectangle.worldId, x, z, false);

                if(GwmConfig.boostServerUtilitiesClaimsMarkers)
                    am.setBoostFlag(true);

                am.setLineStyle(0,0,0);
                am.setFillStyle(0.2, claimer.fillColor);
            }
        }

        updateNeeded = false;
    }

    private List<Claimer> buildClaimers() {
        HashMap<ClaimGroupKey, ClaimGroup> claimsByGroup = new HashMap<>();

        for(ClaimedChunk cc : ClaimedChunks.instance.getAllChunks()){
            ChunkDimPos pos = cc.getPos();

            String worldId = dimensionIdToWorldName.get(pos.dim);

            if(worldId == null)
                continue;

            ForgeTeam team = cc.getTeam();
            ClaimGroupKey key = new ClaimGroupKey(worldId, team.getId());
            ClaimGroup group = claimsByGroup.computeIfAbsent(key,
                    ignored -> new ClaimGroup(worldId, team.getId(), buildLabel(team), team.getColor().getColor().rgb()));
            group.chunks.add(GreedyRectangleMesher.pack(pos.posX, pos.posZ));
        }

        HashMap<String, Claimer> claimersById = new HashMap<>();
        for(ClaimGroup group : claimsByGroup.values()) {
            Claimer claimer = claimersById.computeIfAbsent(group.teamId,
                    ignored -> new Claimer(group.teamId, group.label, group.fillColor));
            for(GreedyRectangleMesher.Rectangle rectangle : GreedyRectangleMesher.mesh(group.chunks)) {
                claimer.rectangles.add(new ClaimRectangle(group.worldId, rectangle.x1, rectangle.x2, rectangle.y1, rectangle.y2));
            }
        }

        return new ArrayList<>(claimersById.values());
    }

    private static String buildLabel(ForgeTeam team) {
        String teamName = team.getId();
//        String title = team.getTitle().getUnformattedText();
//        if (title != null && !teamName.equals(""))
//            teamName = "[" + title + "] " + teamName;
        String label = "Claimed by <b>" + teamName + "</b>";
        String desc = team.getDesc();
        if(desc != null && !desc.equals(""))
            label += "<br/><i>" + team.getDesc() + "</i>";

        if(team.owner != null)
            label += "<br/><b>Founder: </b>" + team.owner.getName();

        List<ForgePlayer> members = team.getMembers();
        if(members != null){
            for(ForgePlayer m : members){
                if(m != team.owner)
                    label += "<br/>Member: " + m.getName();
            }
        }

        if(team.players != null){
            for(Map.Entry<ForgePlayer, EnumTeamStatus> p : team.players.entrySet()){
                label += "<br/>" + p.getValue() + ": " +  p.getKey().getName();
            }
        }
        return label;
    }

    private static class ClaimGroupKey {
        final String worldId;
        final String teamId;

        private ClaimGroupKey(String worldId, String teamId) {
            this.worldId = worldId;
            this.teamId = teamId;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj)
                return true;
            if(!(obj instanceof ClaimGroupKey))
                return false;

            ClaimGroupKey other = (ClaimGroupKey) obj;
            return worldId.equals(other.worldId) && teamId.equals(other.teamId);
        }

        @Override
        public int hashCode() {
            return 31 * worldId.hashCode() + teamId.hashCode();
        }
    }

    private static class ClaimGroup {
        final String worldId;
        final String teamId;
        final String label;
        final int fillColor;
        final HashSet<Long> chunks = new HashSet<>();

        private ClaimGroup(String worldId, String teamId, String label, int fillColor) {
            this.worldId = worldId;
            this.teamId = teamId;
            this.label = label;
            this.fillColor = fillColor;
        }
    }

    private static class Claimer {
        final String id;
        final String label;
        final int fillColor;
        final List<ClaimRectangle> rectangles = new ArrayList<>();

        private Claimer(String id, String label, int fillColor) {
            this.id = id;
            this.label = label;
            this.fillColor = fillColor;
        }
    }

    private static class ClaimRectangle {
        final String worldId;
        final int x1;
        final int x2;
        final int z1;
        final int z2;

        private ClaimRectangle(String worldId, int x1, int x2, int z1, int z2) {
            this.worldId = worldId;
            this.x1 = x1;
            this.x2 = x2;
            this.z1 = z1;
            this.z2 = z2;
        }
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
