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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BalanceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"balance", "bal"}) register(dispatcher, name);
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(Commands.literal(name)
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                return show(player, player.getStringUUID(), "Balance");
            })
            .then(Commands.argument("player", StringArgumentType.word())
                // Suggest online players first, then any known offline player from DB
                .suggests((ctx, builder) -> {
                    String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                    Set<String> added = new HashSet<>();
                    // Online players
                    ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                        String pName = p.getName().getString();
                        if (pName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(pName);
                            added.add(pName.toLowerCase(Locale.ROOT));
                        }
                    });
                    // Offline players from database
                    for (String dbName : AndromedaEconomy.db.getAllUsernames()) {
                        if (dbName.toLowerCase(Locale.ROOT).startsWith(prefix)
                                && !added.contains(dbName.toLowerCase(Locale.ROOT)))
                            builder.suggest(dbName);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer requester)) return 0;
                    String target = StringArgumentType.getString(ctx, "player");
                    PlayerData data = AndromedaEconomy.db.getPlayerByName(target);
                    if (data == null) {
                        requester.sendSystemMessage(Component.literal("Player not found: " + target).withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    return show(requester, data.uuid, data.username + "'s balance");
                }))
        );
    }

    private static int show(ServerPlayer requester, String uuid, String label) {
        PlayerData data = AndromedaEconomy.db.getPlayer(uuid);
        if (data == null) {
            requester.sendSystemMessage(Component.literal("No data found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        requester.sendSystemMessage(Component.literal(
            label + ": " + EconomyUtils.format(data.balance) + " THB"
        ).withStyle(ChatFormatting.GOLD));
        return 1;
    }
}
