package com.andromeda.economy.server.mixin;

import com.andromeda.economy.IAeSpeedHopper;
import com.andromeda.economy.SpeedHopperItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/**
 * Speed Hopper: transfers 10 items per cycle (both in and out).
 *
 * Root cause of all v1.7.x failures:
 *   SpeedHopperMixin was listed under "server" in the mixin config.
 *   In singleplayer Mixin runs Env=CLIENT and skips the "server" section.
 *   Fix: moved to "mixins" section (all environments).
 */
@Mixin(value = HopperBlockEntity.class, priority = 2000)
public abstract class SpeedHopperMixin implements IAeSpeedHopper {

    @Unique private boolean ae_speedHopper = false;

    @Shadow private int cooldownTime;

    @Shadow
    private static boolean ejectItems(Level level, BlockPos pos, HopperBlockEntity hopper) {
        throw new AssertionError();
    }

    // ── IAeSpeedHopper ────────────────────────────────────────────────────────

    @Override public boolean ae$isSpeedHopper()           { return ae_speedHopper; }
    @Override public void    ae$setSpeedHopper(boolean v) { ae_speedHopper = v; }
    @Override public int     ae$getCooldown()             { return cooldownTime; }
    @Override public void    ae$setCooldown(int v)        { cooldownTime = v; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void ae$load(ValueInput input, CallbackInfo ci) {
        ae_speedHopper = input.getBooleanOr(SpeedHopperItem.FLAG, false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void ae$save(ValueOutput output, CallbackInfo ci) {
        if (ae_speedHopper) output.putBoolean(SpeedHopperItem.FLAG, true);
    }

    // ── Speed logic ───────────────────────────────────────────────────────────

    /**
     * Handles 10 ejects AND 10 suck-ins per cycle for Speed Hoppers.
     *
     * Doing both in one cycle prevents the "only first slot fills" issue:
     * without also speeding up suck-in, items cycle through slot 0 only
     * because they are ejected faster than they fill other slots.
     */
    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private static void ae$onTryMoveItems(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, BooleanSupplier bs,
            CallbackInfoReturnable<Boolean> cir) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;

        if (cir.isCancelled()) {
            // Another mod (e.g. HopperTheHedgehog) already ran — add 9 more ejects
            for (int i = 0; i < 9; i++) {
                if (!ejectItems(level, pos, hopper)) break;
            }
            // Also speed up suck-in
            for (int i = 0; i < 9; i++) {
                if (!bs.getAsBoolean()) break;
            }
            return;
        }

        boolean moved = false;

        // Eject up to 10 items into the container below
        for (int i = 0; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
            moved = true;
        }

        // Suck in up to 10 items from the container above
        for (int i = 0; i < 10; i++) {
            if (!bs.getAsBoolean()) break;
            moved = true;
        }

        if (!moved) return; // nothing happened — let vanilla handle it

        sh.ae$setCooldown(HopperBlockEntity.MOVE_ITEM_SPEED);
        cir.setReturnValue(true);
        cir.cancel();
    }
}
