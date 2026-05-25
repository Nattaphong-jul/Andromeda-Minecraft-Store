package com.andromeda.economy.command;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.gui.LotteryGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                .then(Commands.literal("viewresult")
                    .executes(ctx -> viewResult(ctx.getSource())))
                .then(Commands.literal("setnumber")
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                        .then(Commands.argument("number", StringArgumentType.word())
                            .executes(ctx -> setNumber(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "tier"),
                                StringArgumentType.getString(ctx, "number"))))))
            );
        }
    }

    private static int open(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        LotteryGui.open(player);
        return 1;
    }

    private static int forceDraw(CommandSourceStack source) {
        if (!isOp(source)) return 0;
        AndromedaEconomy.lottery.forceDraw(source.getServer());
        sendStatus(source);
        return 1;
    }

    private static int skipResult(CommandSourceStack source) {
        if (!isOp(source)) return 0;
        AndromedaEconomy.lottery.forceEndResult(source.getServer());
        source.sendSuccess(() -> Component.literal("[Lottery] Result window skipped. New round started.")
            .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        if (!isOp(source)) return 0;
        sendStatus(source);
        return 1;
    }

    /** Shows the current round's prize numbers privately to the OP — not visible to other players. */
    private static int viewResult(CommandSourceStack source) {
        if (!isOp(source)) return 0;
        LotteryManager lottery = AndromedaEconomy.lottery;
        String[] win = lottery.getCurrentWinningNumbers();
        source.sendSuccess(() -> Component.literal("[Lottery] Round #" + lottery.getCurrentRoundId() + " prize numbers (private):")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  1st: ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal("#" + win[0]).withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal("  2nd: ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal("#" + win[1]).withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Component.literal("  3rd: ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal("#" + win[2]).withStyle(ChatFormatting.GOLD)), false);
        return 1;
    }

    private static int setNumber(CommandSourceStack source, int tier, String number) {
        if (!isOp(source)) return 0;
        if (!number.matches("\\d{6}")) {
            source.sendFailure(Component.literal("Number must be exactly 6 digits (e.g. 123456)."));
            return 0;
        }
        LotteryManager lottery = AndromedaEconomy.lottery;
        if (lottery.isResultWindowActive()) {
            source.sendFailure(Component.literal("Cannot set numbers during the result window."));
            return 0;
        }
        lottery.setCurrentWinningNumber(tier - 1, number);
        source.sendSuccess(() -> Component.literal("[Lottery] Tier " + tier + " prize set to ")
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("#" + number).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" (only you can see this).").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static void sendStatus(CommandSourceStack source) {
        LotteryManager lottery = AndromedaEconomy.lottery;
        source.sendSuccess(() -> Component.literal("--- Lottery Status ---").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Round: ").withStyle(ChatFormatting.WHITE)
            .append(Component.literal("#" + lottery.getCurrentRoundId()).withStyle(ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("Phase: ").withStyle(ChatFormatting.WHITE)
            .append(lottery.isResultWindowActive()
                ? Component.literal("Result Window").withStyle(ChatFormatting.GREEN)
                : Component.literal("Active (buying open)").withStyle(ChatFormatting.AQUA)), false);
        if (lottery.isResultWindowActive()) {
            String[] win = lottery.getCurrentWinningNumbers();
            source.sendSuccess(() -> Component.literal("Winning numbers:").withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal("  1st: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("#" + win[0]).withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("  2nd: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("#" + win[1]).withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("  3rd: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("#" + win[2]).withStyle(ChatFormatting.GOLD)), false);
        } else {
            long ticks   = lottery.getTicksUntilDraw(source.getServer());
            long total   = ticks / 20;
            long hours   = total / 3600;
            long minutes = (total % 3600) / 60;
            long seconds = total % 60;
            String time  = String.format("%dh %02dm %02ds", hours, minutes, seconds);
            source.sendSuccess(() -> Component.literal("Draw in: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(time).withStyle(ChatFormatting.YELLOW)), false);
        }
    }

    private static boolean isOp(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        return player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }
}
