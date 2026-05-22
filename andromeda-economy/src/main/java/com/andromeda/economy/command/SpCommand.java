package com.andromeda.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /sp — silently toggles spectator mode for OP players.
 * No message is broadcast to other players; only the executing player sees feedback.
 */
public class SpCommand {

    /** Remembers the mode each player was in before entering spectator. */
    private static final Map<UUID, GameType> previousMode = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sp")
            .executes(ctx -> {
                if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                // Silently reject non-OPs without any message
                if (!player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()))) return 0;

                GameType current = player.gameMode();
                if (current == GameType.SPECTATOR) {
                    // Restore previous mode (default to Survival)
                    GameType prev = previousMode.getOrDefault(player.getUUID(), GameType.SURVIVAL);
                    player.setGameMode(prev);
                    player.sendSystemMessage(
                        Component.literal("◀ ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("Spectator OFF").withStyle(ChatFormatting.WHITE))
                    );
                } else {
                    previousMode.put(player.getUUID(), current);
                    player.setGameMode(GameType.SPECTATOR);
                    player.sendSystemMessage(
                        Component.literal("▶ ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("Spectator ON").withStyle(ChatFormatting.WHITE))
                    );
                }
                return 1;
            })
        );
    }
}
