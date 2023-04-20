package org.dynmap.forge;

import net.minecraft.command.ICommandSender;

public abstract class GwmSubCommand {
    public final String name;

    protected GwmSubCommand(String name) {

        this.name = name;
    }

    protected abstract void process(ICommandSender sender, String[] args);
}
