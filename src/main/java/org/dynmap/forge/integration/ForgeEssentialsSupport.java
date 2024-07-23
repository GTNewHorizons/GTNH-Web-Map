package org.dynmap.forge.integration;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fe.event.world.SignEditEvent;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.forge.ForgeWorld;
import org.dynmap.forge.GwmConfig;

public class ForgeEssentialsSupport extends DynmapCommonAPIListener {
    DynmapCommonAPI api;
    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        this.api = api;
        if(GwmConfig.useForgeEssentialsSignHook)
            MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onSignChanged(SignEditEvent event){
        if (api == null)
            return;

        EntityPlayerMP player = event.editor;
        if(player == null)
            return;

        World world = player.getEntityWorld();
        if(world == null)
            return;

        api.processSignChange(0, ForgeWorld.getWorldName(world), event.x, event.y, event.z, event.text, player.getDisplayName());
    }
}
