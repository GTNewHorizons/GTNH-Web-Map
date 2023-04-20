package org.dynmap.forge;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

import java.util.Arrays;
import java.util.HashMap;

public class GwmCommand extends CommandBase {

    final static HashMap<String,GwmSubCommand> subCommands = new HashMap<>();
    private final DynmapPlugin dynmapPlugin;

    public GwmCommand(DynmapPlugin dynmapPlugin) {

        this.dynmapPlugin = dynmapPlugin;
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


}
