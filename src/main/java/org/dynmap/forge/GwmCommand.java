package org.dynmap.forge;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.world.biome.BiomeGenBase;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

public class GwmCommand extends CommandBase {

    final static HashMap<String,GwmSubCommand> subCommands = new HashMap<>();
    private final DynmapPlugin dynmapPlugin;

    public GwmCommand(DynmapPlugin dynmapPlugin) {

        this.dynmapPlugin = dynmapPlugin;

        registerSubCommand(new BiomeDumpSubCommand());
    }

    public static void registerSubCommand(GwmSubCommand subCommand) {
        subCommands.put(subCommand.name, subCommand);
    }
    @Override
    public String getCommandName() {
        return "gwm";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "See docs";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {

        if(args.length == 0)
            return;

        GwmSubCommand sub = subCommands.get(args[0]);
        if(sub != null) {
            String[] argsCopy = new String[args.length-1];
            for(int i= 0; i < argsCopy.length;i++)
                argsCopy[i]=args[i+1];
            sub.process(sender, argsCopy);
        }

    }


    static class BiomeDumpSubCommand extends GwmSubCommand {

        protected BiomeDumpSubCommand() {
            super("biomedump");
        }

        @Override
        protected void process(ICommandSender sender, String[] args) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter("gwm-biomes-dump.txt"));
                BiomeGenBase[] list = BiomeGenBase.getBiomeGenArray();
                for(int i = 0; i < list.length; i++) {
                    BiomeGenBase bb = list[i];
                    if(bb != null) {
                        String id = bb.biomeName;

                        int grassColor = (bb.getBiomeGrassColor(8, 128, 8) & 0xFFFFFF) | 0x01000000;
                        int foliageColor = (bb.getBiomeFoliageColor(8, 128, 8) & 0xFFFFFF) | 0x01000000;
                        int waterColor = bb.waterColorMultiplier;
                        writer.write(String.format("biome:gwm_id=%s,grassColorMult=%X,foliageColorMult=%X,waterColorMult=%X\n", id, grassColor, foliageColor, waterColor));
                    }
                }
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
