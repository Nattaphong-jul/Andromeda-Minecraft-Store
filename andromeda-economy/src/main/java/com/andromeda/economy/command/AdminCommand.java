package com.andromeda.economy.command;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.PlayerData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class AdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("admin")
            .then(Commands.literal("pay")
                .then(Commands.argument("username", StringArgumentType.word())
                    .suggests(AdminCommand::suggestPlayers)
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> pay(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "username"),
                            StringArgumentType.getString(ctx, "amount"))))))
            .then(Commands.literal("deduct")
                .then(Commands.argument("username", StringArgumentType.word())
                    .suggests(AdminCommand::suggestPlayers)
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> deduct(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "username"),
                            StringArgumentType.getString(ctx, "amount"))))))
        );
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
            if (p.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix))
                b.suggest(p.getName().getString());
        });
        return b.buildFuture();
    }

    // ── /admin pay ────────────────────────────────────────────────────────────

    private static int pay(CommandSourceStack source, String username, String amountStr) {
        if (!isOp(source)) {
            source.sendFailure(Component.literal("You don't have permission to use this command."));
            return 0;
        }

        double amount;
        try {
            amount = EconomyUtils.parseAmount(amountStr);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(
                "Invalid amount '" + amountStr + "'. Use a number with optional suffix k/m/b/t (e.g. 1b, 500k)."));
            return 0;
        }
        if (amount <= 0) {
            source.sendFailure(Component.literal("Amount must be greater than 0."));
            return 0;
        }

        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(username);
        PlayerData db = online != null
            ? AndromedaEconomy.db.getPlayer(online.getStringUUID())
            : AndromedaEconomy.db.getPlayerByName(username);

        if (db == null) {
            source.sendFailure(Component.literal("Player '" + username + "' not found."));
            return 0;
        }

        AndromedaEconomy.db.addBalance(db.uuid, amount);

        if (online != null) {
            online.sendSystemMessage(
                Component.literal("[Admin] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("You received ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(EconomyUtils.compact(amount) + " THB").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" from an admin.").withStyle(ChatFormatting.WHITE))
            );
            AndromedaEconomy.hud.update(online);
        }

        String displayName = online != null ? online.getName().getString() : db.username;
        source.sendSuccess(() ->
            Component.literal("[Admin] Paid ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(EconomyUtils.compact(amount) + " THB").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" to ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(".").withStyle(ChatFormatting.YELLOW)),
            false);
        return 1;
    }

    // ── /admin deduct ─────────────────────────────────────────────────────────

    private static int deduct(CommandSourceStack source, String username, String amountStr) {
        if (!isOp(source)) {
            source.sendFailure(Component.literal("You don't have permission to use this command."));
            return 0;
        }

        double amount;
        try {
            amount = EconomyUtils.parseAmount(amountStr);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal(
                "Invalid amount '" + amountStr + "'. Use a number with optional suffix k/m/b/t (e.g. 1b, 500k)."));
            return 0;
        }
        if (amount <= 0) {
            source.sendFailure(Component.literal("Amount must be greater than 0."));
            return 0;
        }

        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(username);
        PlayerData db = online != null
            ? AndromedaEconomy.db.getPlayer(online.getStringUUID())
            : AndromedaEconomy.db.getPlayerByName(username);

        if (db == null) {
            source.sendFailure(Component.literal("Player '" + username + "' not found."));
            return 0;
        }

        double newBalance = Math.max(0, db.balance - amount);
        double actualDeducted = db.balance - newBalance;
        AndromedaEconomy.db.setBalance(db.uuid, newBalance);

        if (online != null) {
            online.sendSystemMessage(
                Component.literal("[Admin] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(EconomyUtils.compact(actualDeducted) + " THB").withStyle(ChatFormatting.RED))
                    .append(Component.literal(" has been deducted from your balance.").withStyle(ChatFormatting.WHITE))
            );
            AndromedaEconomy.hud.update(online);
        }

        String displayName = online != null ? online.getName().getString() : db.username;
        double finalDeducted = actualDeducted;
        source.sendSuccess(() ->
            Component.literal("[Admin] Deducted ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(EconomyUtils.compact(finalDeducted) + " THB").withStyle(ChatFormatting.RED))
                .append(Component.literal(" from ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(". New balance: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(EconomyUtils.compact(newBalance) + " THB").withStyle(ChatFormatting.GREEN)),
            false);
        return 1;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean isOp(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        return player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }
}
