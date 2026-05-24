package com.andromeda.economy.command;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.gui.LotteryGui;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
                .then(Commands.literal("status")
                    .executes(ctx -> status(ctx.getSource())))
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
        sendStatus(source);
        return 1;
    }

    private static int skipResult(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        if (!player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) return 0;
        AndromedaEconomy.lottery.forceEndResult(source.getServer());
        source.sendSuccess(() -> Component.literal("[Lottery] Result window skipped. New round started.")
            .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        if (!player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) return 0;
        sendStatus(source);
        return 1;
    }

    private static void sendStatus(CommandSourceStack source) {
        LotteryManager lottery = AndromedaEconomy.lottery;
        String[] win = lottery.getCurrentWinningNumbers();

        Component header = Component.literal("--- Lottery Status ---").withStyle(ChatFormatting.GOLD);
        Component round  = Component.literal("Round: ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal("#" + lottery.getCurrentRoundId()).withStyle(ChatFormatting.YELLOW));
        Component phase  = Component.literal("Phase: ").withStyle(ChatFormatting.WHITE)
            .append(lottery.isResultWindowActive()
                ? Component.literal("Result Window").withStyle(ChatFormatting.GREEN)
                : Component.literal("Active (buying open)").withStyle(ChatFormatting.AQUA));

        source.sendSuccess(() -> header, false);
        source.sendSuccess(() -> round,  false);
        source.sendSuccess(() -> phase,  false);

        if (lottery.isResultWindowActive()) {
            source.sendSuccess(() -> Component.literal("Winning numbers:").withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal("  1st Prize: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("#" + win[0]).withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("  2nd Prize: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("#" + win[1]).withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("  3rd Prize: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("#" + win[2]).withStyle(ChatFormatting.GOLD)), false);
        } else {
            long ticks = lottery.getTicksUntilDraw(source.getServer());
            long days  = ticks / 24000;
            long hours = (ticks % 24000) / 1000;
            source.sendSuccess(() -> Component.literal("Draw in: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(days + "d " + hours + "h").withStyle(ChatFormatting.YELLOW)), false);
        }
    }
}
