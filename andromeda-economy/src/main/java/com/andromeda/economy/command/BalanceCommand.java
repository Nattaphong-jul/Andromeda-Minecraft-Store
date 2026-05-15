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
        dispatcher.register(Commands.literal("balance")
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
                        String name = p.getName().getString();
                        if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(name);
                            added.add(name.toLowerCase(Locale.ROOT));
                        }
                    });
                    // Offline players from database
                    for (String name : AndromedaEconomy.db.getAllUsernames()) {
                        if (name.toLowerCase(Locale.ROOT).startsWith(prefix)
                                && !added.contains(name.toLowerCase(Locale.ROOT)))
                            builder.suggest(name);
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer requester)) return 0;
                    String name = StringArgumentType.getString(ctx, "player");
                    PlayerData data = AndromedaEconomy.db.getPlayerByName(name);
                    if (data == null) {
                        requester.sendSystemMessage(Component.literal("Player not found: " + name).withStyle(ChatFormatting.RED));
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
