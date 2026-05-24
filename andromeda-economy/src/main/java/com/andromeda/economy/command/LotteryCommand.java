package com.andromeda.economy.command;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.gui.LotteryGui;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class LotteryCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"lottery", "lot"}) {
            dispatcher.register(Commands.literal(name)
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("forcedraw")
                    .executes(ctx -> forceDraw(ctx.getSource())))
                .then(Commands.literal("skipresult")
                    .executes(ctx -> skipResult(ctx.getSource())))
            );
        }
    }

    private static int open(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        LotteryGui.open(player);
        return 1;
    }

    private static int forceDraw(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        if (!player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) return 0;
        AndromedaEconomy.lottery.forceDraw(source.getServer());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "[Lottery] Draw forced."), true);
        return 1;
    }

    private static int skipResult(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        if (!player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) return 0;
        AndromedaEconomy.lottery.forceEndResult(source.getServer());
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
            "[Lottery] Result window skipped. New round started."), true);
        return 1;
    }
}
