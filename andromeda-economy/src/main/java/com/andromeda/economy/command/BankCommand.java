package com.andromeda.economy.command;

import com.andromeda.economy.gui.EnderChestGui;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class BankCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"bank", "asset"}) {
            dispatcher.register(Commands.literal(name)
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                    EnderChestGui.open(player);
                    return 1;
                })
            );
        }
    }
}
