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
 * Adds Speed Hopper behaviour (10 items per eject cycle) to tagged hoppers.
 *
 * Previous TAIL-on-pushItemsTick approach failed because HopperTheHedgehog
 * injects into setCooldown(int) and changes the value BEFORE our check ran.
 *
 * New approach (same as HTH):
 *   Inject INSIDE tryMoveItems right AFTER the first setCooldown(I)V call
 *   (ordinal 0 = the one that fires after ejectItems succeeds).
 *   At that point we know items were just ejected — no cooldown value check needed.
 *   We call ejectItems 9 more times for a total of 10 per cycle.
 *
 * Works with or without HopperTheHedgehog on the server.
 */
@Mixin(HopperBlockEntity.class)
public abstract class SpeedHopperMixin implements IAeSpeedHopper {

    @Unique private boolean ae_speedHopper = false;

    @Shadow(remap = false) private int cooldownTime;

    @Shadow(remap = false)
    private static boolean ejectItems(Level level, BlockPos pos, HopperBlockEntity hopper) {
        throw new AssertionError();
    }

    // ── IAeSpeedHopper ────────────────────────────────────────────────────────

    @Override public boolean ae$isSpeedHopper()       { return ae_speedHopper; }
    @Override public void    ae$setSpeedHopper(boolean v) { ae_speedHopper = v; }
    @Override public int     ae$getCooldown()         { return cooldownTime; }
    @Override public void    ae$setCooldown(int v)    { cooldownTime = v; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void ae$load(ValueInput input, CallbackInfo ci) {
        ae_speedHopper = input.getBooleanOr(SpeedHopperItem.FLAG, false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void ae$save(ValueOutput output, CallbackInfo ci) {
        if (ae_speedHopper) output.putBoolean(SpeedHopperItem.FLAG, true);
    }

    // ── Speed logic ───────────────────────────────────────────────────────────

    /**
     * Fires inside tryMoveItems immediately AFTER the first setCooldown call
     * (ordinal=0), which only executes when ejectItems just succeeded.
     *
     * No cooldown value check needed — the injection point guarantees items moved.
     * Compatible with HopperTheHedgehog because we hook the setCooldown CALL,
     * not the cooldown VALUE that HTH subsequently overwrites.
     */
    @Inject(
        method = "tryMoveItems",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;setCooldown:(I)V",
            ordinal = 0,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private static void ae$afterEjectCooldown(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, BooleanSupplier bs,
            CallbackInfoReturnable<Boolean> cir) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;
        for (int i = 1; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
        }
    }
}
