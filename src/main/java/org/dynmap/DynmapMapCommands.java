package org.dynmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import org.dynmap.common.DynmapCommandSender;
import org.dynmap.common.DynmapPlayer;
import org.dynmap.forge.ForgeWorld;
import org.dynmap.hdmap.HDLighting;
import org.dynmap.hdmap.HDMap;
import org.dynmap.hdmap.HDPerspective;
import org.dynmap.hdmap.HDShader;

/**
 * Handler for world and map edit commands (via /dmap)
 */
public class DynmapMapCommands {

    private boolean checkIfActive(DynmapCore core, DynmapCommandSender sender) {
        if((!core.getPauseFullRadiusRenders()) || (!core.getPauseUpdateRenders())) {
            sender.sendMessage("Cannot edit map data while rendering active - run '/dynmap pause all' to pause rendering");
            return true;
        }
        return false;
    }
    
    public boolean processCommand(DynmapCommandSender sender, String cmd, String commandLabel, String[] args, DynmapCore core) {
        /* Re-parse args - handle doublequotes */
        args = DynmapCore.parseArgs(args, sender);
        if(args.length < 1)
            return false;
        cmd = args[0];
        boolean rslt = false;

        if(cmd.equalsIgnoreCase("help")) {
            core.printCommandHelp(sender, "dmap", (args.length > 1)?args[1]:"");
            return true;
        }
        else if(cmd.equalsIgnoreCase("worldlist")) {
            rslt = handleWorldList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("perspectivelist")) {
            rslt = handlePerspectiveList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("shaderlist")) {
            rslt = handleShaderList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("lightinglist")) {
            rslt = handleLightingList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("maplist")) {
            rslt = handleMapList(sender, args, core);
        }
        else if(cmd.equalsIgnoreCase("blocklist")) {
            rslt = handleBlockList(sender, args, core);
        }
        /* Other commands are edits - must be paused to run these */
        else if(checkIfActive(core, sender)) {
            rslt = true;
        }
        else {
            if(cmd.equalsIgnoreCase("worldset")) {
                rslt = handleWorldSet(sender, args, core);
            }
            else if(cmd.equalsIgnoreCase("mapdelete")) {
                rslt = handleMapDelete(sender, args, core);
            }
            else if(cmd.equalsIgnoreCase("worldreset")) {
                rslt = handleWorldReset(sender, args, core);
            }
            else if(cmd.equalsIgnoreCase("mapset")) {
                rslt = handleMapSet(sender, args, core, false);
            }
            else if(cmd.equalsIgnoreCase("mapadd")) {
                rslt = handleMapSet(sender, args, core, true);
            }
            else if(cmd.equalsIgnoreCase("enableonly")){
                rslt = handleEnableOnly(sender, args, core, true);
            }
            else if(cmd.equalsIgnoreCase("exploremode")){
                rslt = handleExploreMode(sender, args, core);
            }
            else if(cmd.equalsIgnoreCase("setorder")){
                rslt = handleEnableOnly(sender, args, core, false);
            }
            else if(cmd.equalsIgnoreCase("disableworlds")){
                rslt = handleDisableWorlds(sender, args, core);
            }
            if(rslt)
                sender.sendMessage("If you are done editing map data, run '/dynmap pause none' to resume rendering");
        }
        return rslt;
    }

    private boolean handleExploreMode(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;

        if(checkIfActive(core, sender)) {
            return true;
        }

        Set<String> wnames = new HashSet<>();
        if(args.length > 1) {
            for(int i = 1; i < args.length; i++)
                wnames.add(DynmapWorld.normalizeWorldName(args[i]));
        } else {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer ws  = server.worldServerForDimension(0);
            if(ws != null) {
                wnames.add(ForgeWorld.getWorldName(ws));
            }
        }
        boolean changed = false;

        Collection<DynmapWorld> enabledWorlds = new ArrayList<>(core.getMapManager().getWorlds());
        for (DynmapWorld w : enabledWorlds) {
            String wname = w.getName();
            boolean toBeEnabled = wnames.contains(wname);
            changed |= core.setWorldEnable(wname, toBeEnabled);

            if(!toBeEnabled) {
                core.setWorldEnableOnVisit(wname, true);
            }
        }
        if(changed) {
            for (DynmapWorld w : enabledWorlds) {
                core.refreshWorld(w.getName());
            }
        }
        return true;
    }

    private boolean handleWorldList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldlist"))
            return true;
        Set<String> wnames = null;
        if(args.length > 1) {
            wnames = new HashSet<String>();
            for(int i = 1; i < args.length; i++)
                wnames.add(DynmapWorld.normalizeWorldName(args[i]));
        }
        /* Get active worlds */
        for(DynmapWorld w : core.getMapManager().getWorlds()) {
            if((wnames != null) && (wnames.contains(w.getName()) == false)) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("world ").append(w.getName()).append(": loaded=").append(w.isLoaded()).append(", enabled=").append(w.isEnabled());
            sb.append(", title=").append(w.getTitle());
            
            DynmapLocation loc = w.getCenterLocation();
            if(loc != null) {
                sb.append(", center=").append(loc.x).append("/").append(loc.y).append("/").append(loc.z);
            }
            sb.append(", extrazoomout=").append(w.getExtraZoomOutLevels()).append(", sendhealth=").append(w.sendhealth);
            sb.append(", sendposition=").append(w.sendposition);
            sb.append(", protected=").append(w.is_protected);
            if(w.tileupdatedelay > 0) {
                sb.append(", tileupdatedelay=").append(w.tileupdatedelay);
            }
            sender.sendMessage(sb.toString());
        }
        /* Get disabled worlds */
        for(String wn : core.getMapManager().getDisabledWorlds()) {
            if((wnames != null) && (wnames.contains(wn) == false)) {
                continue;
            }
            sender.sendMessage("world " + wn + ": isenabled=false");
        }
        
        return true;
    }

    private boolean handleEnableOnly(DynmapCommandSender sender, String[] args, DynmapCore core, boolean enableDisable) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;

        if(checkIfActive(core, sender)) {
            return true;
        }

        Set<String> wnames = new HashSet<>();
        if(args.length > 1) {
            for(int i = 1; i < args.length; i++)
                wnames.add(DynmapWorld.normalizeWorldName(args[i]));
        } else {
            if(enableDisable) {
                sender.sendMessage("No args provided - all current worlds will be disabled");
            }
            else {
                sender.sendMessage("No args provided - order will be unaffected");
            }
        }

        boolean changed = false;
        if (enableDisable) {
            Collection<DynmapWorld> enabledWorlds = new ArrayList<>(core.getMapManager().getWorlds());
            for (DynmapWorld w : enabledWorlds) {
                String wname = w.getName();
                boolean toBeEnabled = wnames.contains(wname);
                changed |= core.setWorldEnable(wname, toBeEnabled);
            }

            Collection<String> disabledWorlds = new ArrayList<>(core.getMapManager().getDisabledWorlds());
            for (String wname : disabledWorlds) {
                boolean toBeEnabled = wnames.contains(wname);
                changed |= core.setWorldEnable(wname, toBeEnabled);
                if(toBeEnabled) {
                    core.refreshWorld(wname);
                }
            }
        }

        int numSet = 0;
        for(int i = 1; i < args.length; i++){
            String wname = args[i];

            if(core.getWorld(wname) != null)
                changed |= core.setWorldOrder(wname, numSet++);
        }

        if(changed) {
            for (DynmapWorld w : core.getMapManager().getWorlds()) {
                core.refreshWorld(w.getName());
            }
        }

        if(enableDisable) {
            sender.sendMessage("Number of enabled worlds: " + numSet);
        }
        else {
            sender.sendMessage("Number of reordered worlds: " + numSet);
        }

        return true;
    }

    private boolean handleDisableWorlds(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;

        if(checkIfActive(core, sender)) {
            return true;
        }

        Set<String> wnames = new HashSet<>();
        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) {
                String wname = DynmapWorld.normalizeWorldName(args[i]);
                if(core.getWorld(wname) != null)
                    wnames.add(wname);
            }
        } else {
            sender.sendMessage("No args provided - no worlds will be affected");
            return false;
        }

        if(wnames.size() <= 0){
            sender.sendMessage("Found no enabled worlds to disable");
            return false;
        }

        boolean changed = false;
        for(String wname : wnames){
            changed |= core.setWorldEnable(wname, false);
        }

        if(changed){
            for(String wname : wnames) {
                core.refreshWorld(wname);
            }

            sender.sendMessage("Disabled " + wnames.size() + " worlds");
        }

        return true;
    }
    private boolean handleWorldSet(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldset"))
            return true;

        if(args.length < 3) {
            sender.sendMessage("World name and setting:newvalue required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        /* Test if render active - quit if so */
        if(checkIfActive(core, sender)) {
            return true;
        }
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        
        boolean did_update = false;
        for(int i = 2; i < args.length; i++) {
            String[] tok = args[i].split(":");  /* Split at colon */
            if(tok.length != 2) {
                sender.sendMessage("Syntax error: " + args[i]);
                return false;
            }
            if(tok[0].equalsIgnoreCase("enabled")) {
                did_update |= core.setWorldEnable(wname, !tok[1].equalsIgnoreCase("false"));
            }
            else if(tok[0].equalsIgnoreCase("title")) {
                if(w == null) {
                    sender.sendMessage("Cannot set extrazoomout on disabled or undefined world");
                    return true;
                }
                w.setTitle(tok[1]);
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("sendposition")) {
                if(w == null) {
                    sender.sendMessage("Cannot set sendposition on disabled or undefined world");
                    return true;
                }
                w.sendposition = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("sendhealth")) {
                if(w == null) {
                    sender.sendMessage("Cannot set sendhealth on disabled or undefined world");
                    return true;
                }
                w.sendhealth = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("protected")) {
                if(w == null) {
                    sender.sendMessage("Cannot set protected on disabled or undefined world");
                    return true;
                }
                w.is_protected = tok[1].equals("true");
                core.updateWorldConfig(w);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("extrazoomout")) {  /* Extrazoomout setting */
                if(w == null) {
                    sender.sendMessage("Cannot set extrazoomout on disabled or undefined world");
                    return true;
                }
                int exo = -1;
                try {
                    exo = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                if((exo < 0) || (exo > 32)) {
                    sender.sendMessage("Invalid value for extrazoomout: " + tok[1]);
                    return true;
                }
                did_update |= core.setWorldZoomOut(wname, exo);
            }
            else if(tok[0].equalsIgnoreCase("tileupdatedelay")) {  /* tileupdatedelay setting */
                if(w == null) {
                    sender.sendMessage("Cannot set tileupdatedelay on disabled or undefined world");
                    return true;
                }
                int tud = -1;
                try {
                    tud = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                did_update |= core.setWorldTileUpdateDelay(wname, tud);
            }
            else if(tok[0].equalsIgnoreCase("center")) {    /* Center */
                if(w == null) {
                    sender.sendMessage("Cannot set center on disabled or undefined world");
                    return true;
                }
                boolean good = false;
                DynmapLocation loc = null;
                try {
                    String[] toks = tok[1].split("/");
                    if(toks.length == 3) {
                        double x = 0, y = 0, z = 0;
                        x = Double.valueOf(toks[0]);
                        y = Double.valueOf(toks[1]);
                        z = Double.valueOf(toks[2]);
                        loc = new DynmapLocation(wname, x, y, z);
                       good = true;
                    }
                    else if(tok[1].equalsIgnoreCase("default")) {
                        good = true;
                    }
                    else if(tok[1].equalsIgnoreCase("here")) {
                        if(sender instanceof DynmapPlayer) {
                            loc = ((DynmapPlayer)sender).getLocation();
                            good = true;
                        }
                        else {
                            sender.sendMessage("Setting center to 'here' requires player");
                            return true;
                        }
                    }
                } catch (NumberFormatException nfx) {}
                if(!good) {
                    sender.sendMessage("Center value must be formatted x/y/z or be set to 'default' or 'here'");
                    return true;
                }
                did_update |= core.setWorldCenter(wname, loc);
            }
            else if(tok[0].equalsIgnoreCase("order")) {
                if(w == null) {
                    sender.sendMessage("Cannot set order on disabled or undefined world");
                    return true;
                }
                int order = -1;
                try {
                    order = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {}
                if(order < 1) {
                    sender.sendMessage("Order value must be number from 1 to number of worlds");
                    return true;
                }
                did_update |= core.setWorldOrder(wname, order-1);
            }
        }
        /* If world updatd, refresh it */
        if(did_update) {
            sender.sendMessage("Refreshing configuration for world " + wname);
            core.refreshWorld(wname);
        }
        
        return true;
    }
    
    private boolean handleMapList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.maplist"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World name is required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        if(w == null) { 
            sender.sendMessage("Only loaded world can be listed");
            return true;
        }
        List<MapType> maps = w.maps;
        for(MapType mt : maps) {
            if(mt instanceof HDMap) {
                HDMap hdmt = (HDMap)mt;
                StringBuilder sb = new StringBuilder();
                sb.append("map ").append(mt.getName()).append(": prefix=").append(hdmt.getPrefix()).append(", title=").append(hdmt.getTitle());
                sb.append(", perspective=").append(hdmt.getPerspective().getName()).append(", shader=").append(hdmt.getShader().getName());
                sb.append(", lighting=").append(hdmt.getLighting().getName()).append(", mapzoomin=").append(hdmt.getMapZoomIn()).append(", mapzoomout=").append(hdmt.getMapZoomOutLevels());
                sb.append(", img-format=").append(hdmt.getImageFormatSetting()).append(", icon=").append(hdmt.getIcon());
                sb.append(", append-to-world=").append(hdmt.getAppendToWorld()).append(", boostzoom=").append(hdmt.getBoostZoom());
                sb.append(", protected=").append(hdmt.isProtected());
                if(hdmt.tileupdatedelay > 0) {
                    sb.append(", tileupdatedelay=").append(hdmt.tileupdatedelay);
                }
                sender.sendMessage(sb.toString());
            }
        }
        
        return true;
    }

    private boolean handleMapDelete(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.mapdelete"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World:map name required");
            return true;
        }
        for(int i = 1; i < args.length; i++) {
            String world_map_name = args[i];
            String[] tok = world_map_name.split(":");
            if(tok.length != 2) {
                sender.sendMessage("Invalid world:map name: " + world_map_name);
                return true;
            }
            String wname = tok[0];
            String mname = tok[1];
            DynmapWorld w = core.getWorld(wname);   /* Try to get world */
            if(w == null) {
                sender.sendMessage("Cannot delete maps from disabled or unloaded world: " + wname);
                return true;
            }
            List<MapType> maps = new ArrayList<MapType>(w.maps);
            boolean done = false;
            for(int idx = 0; (!done) && (idx < maps.size()); idx++) {
                MapType mt = maps.get(idx);
                if(mt.getName().equals(mname)) {
                    w.maps.remove(mt);
                    done = true;
                }
            }
            /* If done, save updated config for world */
            if(done) {
                if(core.updateWorldConfig(w)) {
                    sender.sendMessage("Refreshing configuration for world " + wname);
                    core.refreshWorld(wname);
                }
            }
        }
        
        return true;
    }
    
    private boolean handleWorldReset(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.worldreset"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World name required");
            return true;
        }
        String wname = args[1]; /* Get world name */
        
        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        /* If not loaded, cannot reset */
        if(w == null) {
            sender.sendMessage("Cannot reset world that is not loaded or enabled");
            return true;
        }
        ConfigurationNode cn = null;
        if(args.length > 2) {
            cn = core.getTemplateConfigurationNode(args[2]);
        }
        else {  /* Else get default */
            cn = core.getDefaultTemplateConfigurationNode(w);
        }
        if(cn == null) {
            sender.sendMessage("Cannot load template");
            return true;
        }
        ConfigurationNode cfg = w.saveConfiguration();  /* Get configuration */
        cfg.extend(cn);    /* And apply template */

        /* And set world config */
        if(core.replaceWorldConfig(wname, cfg)) {
            sender.sendMessage("Reset configuration for world " + wname);
            core.refreshWorld(wname);
        }
        
        return true;
    }
    
    private boolean handleMapSet(DynmapCommandSender sender, String[] args, DynmapCore core, boolean isnew) {
        if(!core.checkPlayerPermission(sender, isnew?"dmap.mapadd":"dmap.mapset"))
            return true;
        if(args.length < 2) {
            sender.sendMessage("World:map name required");
            return true;
        }
        String world_map_name = args[1];
        String[] tok = world_map_name.split(":");
        if(tok.length != 2) {
            sender.sendMessage("Invalid world:map name: " + world_map_name);
            return true;
        }
        String wname = tok[0];
        String mname = tok[1];

        DynmapWorld w = core.getWorld(wname);   /* Try to get world */
        if(w == null) {
            sender.sendMessage("Cannot update maps from disabled or unloaded world: " + wname);
            return true;
        }
        HDMap mt = null;
        /* Find the map */
        for(MapType map : w.maps) {
            if(map instanceof HDMap) {
                if(map.getName().equals(mname)) {
                    mt = (HDMap)map;
                    break;
                }
            }
        }
        /* If new, make default map instance */
        if(isnew) {
            if(mt != null) {
                sender.sendMessage("Map " + mname + " already exists on world " + wname);
                return true;
            }
            ConfigurationNode cn = new ConfigurationNode();
            cn.put("name", mname);
            mt = new HDMap(core, cn);
            if(mt.getName() != null) {
                w.maps.add(mt); /* Add to end, by default */
            }
            else {
                sender.sendMessage("Map " + mname + " not valid");
                return true;
            }
        }
        else {
            if(mt == null) {
                sender.sendMessage("Map " + mname + " not found on world " + wname);
                return true;
            }
        }
        boolean did_update = isnew;
        for(int i = 2; i < args.length; i++) {
            tok = args[i].split(":", 2);  /* Split at colon */
            if(tok.length < 2) {
                String[] newtok = new String[2];
                newtok[0] = tok[0];
                newtok[1] = "";
                tok = newtok;
            }
            if(tok[0].equalsIgnoreCase("prefix")) {
                /* Check to make sure prefix is unique */
                for(MapType map : w.maps){
                    if(map == mt) continue;
                    if(map instanceof HDMap) {
                        if(((HDMap)map).getPrefix().equals(tok[1])) {
                            sender.sendMessage("Prefix " + tok[1] + " already in use");
                            return true;
                        }
                    }
                }
                did_update |= mt.setPrefix(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("title")) {
                did_update |= mt.setTitle(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("icon")) {
                did_update |= mt.setIcon(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("mapzoomin")) {
                int mzi = -1;
                try {
                    mzi = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if((mzi < 0) || (mzi > 32)) {
                    sender.sendMessage("Invalid mapzoomin value: " + tok[1]);
                    return true;
                }
                did_update |= mt.setMapZoomIn(mzi);
            }
            else if(tok[0].equalsIgnoreCase("mapzoomout")) {
                int mzi = -1;
                try {
                    mzi = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if((mzi < 0) || (mzi > 32)) {
                    sender.sendMessage("Invalid mapzoomout value: " + tok[1]);
                    return true;
                }
                did_update |= mt.setMapZoomOut(mzi);
            }
            else if(tok[0].equalsIgnoreCase("boostzoom")) {
                int mzi = -1;
                try {
                    mzi = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if((mzi < 0) || (mzi > 3)) {
                    sender.sendMessage("Invalid boostzoom value: " + tok[1]);
                    return true;
                }
                did_update |= mt.setBoostZoom(mzi);
            }
            else if(tok[0].equalsIgnoreCase("tileupdatedelay")) {
                int tud = -1;
                try {
                    tud = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                did_update |= mt.setTileUpdateDelay(tud);
            }
            else if(tok[0].equalsIgnoreCase("perspective")) {
                if(MapManager.mapman != null) {
                    HDPerspective p = MapManager.mapman.hdmapman.perspectives.get(tok[1]);
                    if(p == null) {
                        sender.sendMessage("Perspective not found: " + tok[1]);
                        return true;
                    }
                    did_update |= mt.setPerspective(p);
                }
            }
            else if(tok[0].equalsIgnoreCase("shader")) {
                if(MapManager.mapman != null) {
                    HDShader s = MapManager.mapman.hdmapman.shaders.get(tok[1]);
                    if(s == null) {
                        sender.sendMessage("Shader not found: " + tok[1]);
                        return true;
                    }
                    did_update |= mt.setShader(s);
                }
            }
            else if(tok[0].equalsIgnoreCase("lighting")) {
                if(MapManager.mapman != null) {
                    HDLighting l = MapManager.mapman.hdmapman.lightings.get(tok[1]);
                    if(l == null) {
                        sender.sendMessage("Lighting not found: " + tok[1]);
                        return true;
                    }
                    did_update |= mt.setLighting(l);
                }
            }
            else if(tok[0].equalsIgnoreCase("img-format")) {
                if((!tok[1].equals("default")) && (MapType.ImageFormat.fromID(tok[1]) == null)) {
                    sender.sendMessage("Image format not found: " + tok[1]);
                    return true;
                }
                did_update |= mt.setImageFormatSetting(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("order")) {
                int idx = -1;
                try {
                    idx = Integer.valueOf(tok[1]);
                } catch (NumberFormatException nfx) {
                }
                if(idx < 1) {
                    sender.sendMessage("Invalid order position: " + tok[1]);
                    return true;
                }
                idx--;
                /* Remove and insert at position */
                w.maps.remove(mt);
                if(idx < w.maps.size())
                    w.maps.add(idx, mt);
                else
                    w.maps.add(mt);
                did_update = true;
            }
            else if(tok[0].equalsIgnoreCase("append-to-world")) {
                did_update |= mt.setAppendToWorld(tok[1]);
            }
            else if(tok[0].equalsIgnoreCase("protected")) {
                did_update |= mt.setProtected(Boolean.parseBoolean(tok[1]));
            }
        }
        if(did_update) {
            if(core.updateWorldConfig(w)) {
                sender.sendMessage("Refreshing configuration for world " + wname);
                core.refreshWorld(wname);
            }
        }

        return true;
    }
    
    private boolean handlePerspectiveList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.perspectivelist"))
            return true;
        if(MapManager.mapman != null) {
            StringBuilder sb = new StringBuilder();
            for(HDPerspective p : MapManager.mapman.hdmapman.perspectives.values()) {
                sb.append(p.getName()).append(' ');
            }
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleShaderList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.shaderlist"))
            return true;
        if(MapManager.mapman != null) {
            StringBuilder sb = new StringBuilder();
            for(HDShader p : MapManager.mapman.hdmapman.shaders.values()) {
                sb.append(p.getName()).append(' ');
            }
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleLightingList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.lightinglist"))
            return true;
        if(MapManager.mapman != null) {
            StringBuilder sb = new StringBuilder();
            for(HDLighting p : MapManager.mapman.hdmapman.lightings.values()) {
                sb.append(p.getName()).append(' ');
            }
            sender.sendMessage(sb.toString());
        }
        return true;
    }

    private boolean handleBlockList(DynmapCommandSender sender, String[] args, DynmapCore core) {
        if(!core.checkPlayerPermission(sender, "dmap.blklist"))
            return true;
        Map<String, Integer> map = core.getServer().getBlockUniqueIDMap();
        TreeSet<String> keys = new TreeSet<String>(map.keySet());
        for (String k : keys) {
            sender.sendMessage(k + ": " + map.get(k));
        }
        return true;
    }
}
