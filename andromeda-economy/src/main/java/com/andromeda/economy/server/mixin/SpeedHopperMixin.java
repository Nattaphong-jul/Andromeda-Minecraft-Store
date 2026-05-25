package com.andromeda.economy.server.mixin;

import com.andromeda.economy.IAeSpeedHopper;
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
 * Adds "speed hopper" behaviour to {@link HopperBlockEntity}.
 *
 * A speed hopper is a vanilla hopper item carrying {@code ae_speed_hopper=true}
 * block-entity NBT (set by {@link com.andromeda.economy.SpeedHopperItem}).
 * When placed, BlockItem copies that flag into the BE via loadAdditional.
 *
 * Changes:
 *  – cooldown halved  (4 ticks instead of 8)
 *  – up to 10 items pushed per operation (9 extra ejectItems calls)
 */
@Mixin(HopperBlockEntity.class)
public abstract class SpeedHopperMixin implements IAeSpeedHopper {

    @Unique private boolean ae_speedHopper = false;

    @Shadow(remap = false) private int cooldownTime;

    @Shadow(remap = false)
    private static boolean ejectItems(Level level, BlockPos pos, HopperBlockEntity hopper) {
        throw new AssertionError();
    }

    // ── Interface ─────────────────────────────────────────────────────────────

    @Override
    public boolean ae$isSpeedHopper() { return ae_speedHopper; }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void ae$load(ValueInput input, CallbackInfo ci) {
        ae_speedHopper = input.getBooleanOr("ae_speed_hopper", false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void ae$save(ValueOutput output, CallbackInfo ci) {
        if (ae_speedHopper) output.putBoolean("ae_speed_hopper", true);
    }

    // ── Half cooldown ─────────────────────────────────────────────────────────

    @Inject(method = "setCooldown", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae$setCooldown(int cooldown, CallbackInfo ci) {
        if (!ae_speedHopper || cooldown <= 0) return;
        this.cooldownTime = cooldown / 2;
        ci.cancel();
    }

    // ── Extra item transfers ──────────────────────────────────────────────────

    @Inject(method = "tryMoveItems", at = @At("RETURN"), remap = false)
    private static void ae$afterTryMoveItems(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity blockEntity, BooleanSupplier operator,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return; // nothing moved
        if (!((IAeSpeedHopper) blockEntity).ae$isSpeedHopper()) return;
        // Vanilla already pushed/pulled 1 item; push up to 9 more.
        for (int i = 1; i < 10; i++) {
            if (!ejectItems(level, pos, blockEntity)) break;
        }
    }
}
