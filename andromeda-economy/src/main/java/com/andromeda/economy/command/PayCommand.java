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

import java.util.Locale;

public class PayCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", StringArgumentType.word())
                // Suggest only online player names — no selectors
                .suggests((ctx, builder) -> {
                    String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                    ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                        String name = p.getName().getString();
                        if (name.toLowerCase(Locale.ROOT).startsWith(prefix))
                            builder.suggest(name);
                    });
                    return builder.buildFuture();
                })
                // Use word argument so players can type "10m", "1.5k", "500", etc.
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sender)) return 0;
                        String raw = StringArgumentType.getString(ctx, "amount");
                        double amount;
                        try {
                            amount = EconomyUtils.parseAmount(raw);
                        } catch (NumberFormatException e) {
                            sender.sendSystemMessage(Component.literal(
                                "Invalid amount: '" + raw + "'. Use a number, e.g. 500, 10k, 2.5m"
                            ).withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        if (amount <= 0) {
                            sender.sendSystemMessage(Component.literal("Amount must be positive.").withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        return pay(sender, StringArgumentType.getString(ctx, "player"), amount);
                    })))
        );
    }

    private static int pay(ServerPlayer sender, String targetName, double amount) {
        PlayerData target = AndromedaEconomy.db.getPlayerByName(targetName);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("Player not found: " + targetName).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target.uuid.equals(sender.getStringUUID())) {
            sender.sendSystemMessage(Component.literal("You cannot pay yourself.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!AndromedaEconomy.db.transferBalance(sender.getStringUUID(), target.uuid, amount)) {
            sender.sendSystemMessage(Component.literal("Insufficient balance.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String amtStr = EconomyUtils.format(amount) + " THB";
        sender.sendSystemMessage(Component.literal("Sent " + amtStr + " to " + target.username + ".").withStyle(ChatFormatting.GREEN));

        ServerPlayer online = sender.level().getServer().getPlayerList().getPlayerByName(target.username);
        if (online != null) {
            online.sendSystemMessage(Component.literal(sender.getName().getString() + " sent you " + amtStr + ".").withStyle(ChatFormatting.GREEN));
            AndromedaEconomy.playMoneySound(online);
            AndromedaEconomy.hud.update(online);
        }

        AndromedaEconomy.hud.update(sender);
        return 1;
    }
}
