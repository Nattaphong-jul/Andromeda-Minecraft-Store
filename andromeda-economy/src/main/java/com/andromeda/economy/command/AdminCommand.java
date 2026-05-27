package com.andromeda.economy.command;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.PlayerData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Locale;

public class AdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("admin")
            .then(Commands.literal("pay")
                .then(Commands.argument("username", StringArgumentType.word())
                    .suggests((ctx, b) -> {
                        String prefix = b.getRemaining().toLowerCase(Locale.ROOT);
                        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                            if (p.getName().getString().toLowerCase(Locale.ROOT).startsWith(prefix))
                                b.suggest(p.getName().getString());
                        });
                        return b.buildFuture();
                    })
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> pay(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "username"),
                            StringArgumentType.getString(ctx, "amount"))))))
        );
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

        // Find target — check online first, then database
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(username);
        PlayerData db = online != null
            ? AndromedaEconomy.db.getPlayer(online.getStringUUID())
            : AndromedaEconomy.db.getPlayerByName(username);

        if (db == null) {
            source.sendFailure(Component.literal("Player '" + username + "' not found."));
            return 0;
        }

        AndromedaEconomy.db.addBalance(db.uuid, amount);

        // Notify target if online
        if (online != null) {
            online.sendSystemMessage(
                Component.literal("[Admin] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("You received ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(EconomyUtils.compact(amount) + " THB").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" from an admin.").withStyle(ChatFormatting.WHITE))
            );
            AndromedaEconomy.hud.update(online);
        }

        // Confirm to sender
        String displayName = online != null ? online.getName().getString() : db.username;
        source.sendSuccess(() ->
            Component.literal("[Admin] Paid ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(EconomyUtils.compact(amount) + " THB").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" to ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(".").withStyle(ChatFormatting.YELLOW)),
            true);
        return 1;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static boolean isOp(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        return player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }
}
