package com.andromeda.economy.command;

import com.andromeda.economy.gui.LotteryGui;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class LotteryCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"lottery", "lot"}) {
            dispatcher.register(Commands.literal(name)
                .executes(ctx -> open(ctx.getSource()))
            );
        }
    }

    private static int open(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        LotteryGui.open(player);
        return 1;
    }
}
