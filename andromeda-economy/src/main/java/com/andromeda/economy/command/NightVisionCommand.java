package com.andromeda.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class NightVisionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nightvision").executes(ctx -> toggle(ctx.getSource())));
        dispatcher.register(Commands.literal("nv").executes(ctx -> toggle(ctx.getSource())));
    }

    private static int toggle(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

        boolean turningOn = !player.hasEffect(MobEffects.NIGHT_VISION);

        if (turningOn) {
            // Infinite duration (-1), amplifier 255, not ambient, no particles
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 255, false, false));
        } else {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }

        player.sendSystemMessage(
            Component.literal("Night Vision ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(turningOn ? "ON" : "OFF")
                    .withStyle(turningOn ? ChatFormatting.GREEN : ChatFormatting.RED))
        );

        return 1;
    }
}
