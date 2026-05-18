package com.andromeda.economy.command;

import com.andromeda.economy.gui.ShopGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class ShopCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : new String[]{"shop", "sh"}) {
            dispatcher.register(Commands.literal(name)
                .executes(ctx -> open(ctx.getSource(), ""))
                .then(Commands.argument("search", StringArgumentType.greedyString())
                    .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "search"))))
            );
        }
    }

    private static int open(CommandSourceStack source, String search) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;
        ShopGui.open(player, search);
        return 1;
    }
}
