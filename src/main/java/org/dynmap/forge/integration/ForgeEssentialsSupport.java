package org.dynmap.forge.integration;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fe.event.world.SignEditEvent;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.DynmapCore;
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
        if(api instanceof DynmapCore){
            DynmapCore dc = (DynmapCore)api;

            dc.processSignChange(0, ForgeWorld.getWorldName(event.editor.getEntityWorld()), event.x, event.y, event.z, event.text, event.editor.getDisplayName());
        }
    }
}
