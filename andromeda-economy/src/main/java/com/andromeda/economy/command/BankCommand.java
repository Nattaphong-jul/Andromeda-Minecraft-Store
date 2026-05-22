package com.andromeda.economy.command;

import com.andromeda.economy.gui.EnderChestGui;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class BankCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bank")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                // Open the ender chest GUI without a nearby block (no animation, any location)
                EnderChestGui.open(player);
                return 1;
            })
        );
    }
}
