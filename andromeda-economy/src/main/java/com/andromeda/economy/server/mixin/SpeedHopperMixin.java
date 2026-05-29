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

/**
 * Adds Speed Hopper behaviour (10 items per eject cycle).
 *
 * ── Why previous approaches failed ──────────────────────────────────────────
 *
 * v1 (TAIL on pushItemsTick, check cooldownTime == MOVE_ITEM_SPEED):
 *   HopperTheHedgehog injects into setCooldown(int) and changes cooldownTime
 *   to its configured TransferSpeed value BEFORE our check. So == 8 was false.
 *
 * v2 (AFTER INVOKE setCooldown inside tryMoveItems):
 *   HTH injects at HEAD of tryMoveItems with cancellable=true and calls
 *   cir.cancel(). The original method body never runs, so the setCooldown
 *   call inside it never executes. Our AFTER INVOKE target is never reached.
 *
 * ── Correct approach ─────────────────────────────────────────────────────────
 *
 * Inject in pushItemsTick AFTER the tryMoveItems CALL SITE (not inside it).
 * The invokevirtual instruction in pushItemsTick always executes even when
 * HTH cancels the body. After it returns, check cooldown > 0: vanilla sets
 * cooldown to 8 on success; HTH may set it to its TransferSpeed config value.
 * Either way, cooldown > 0 means items were moved this tick.
 *
 * Works with or without HopperTheHedgehog.
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
     * Fires in pushItemsTick AFTER the tryMoveItems call site returns.
     *
     * The invokevirtual instruction executes regardless of whether vanilla or
     * HopperTheHedgehog handled tryMoveItems. After it returns, cooldown > 0
     * iff items were moved (vanilla sets 8, HTH sets its TransferSpeed).
     * We then call ejectItems 9 more times for a total of 10 per cycle.
     */
    @Inject(
        method = "pushItemsTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;tryMoveItems:(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/HopperBlockEntity;Ljava/util/function/BooleanSupplier;)Z",
            ordinal = 0,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private static void ae$afterTryMoveItems(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, CallbackInfo ci) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;
        if (sh.ae$getCooldown() <= 0) return; // nothing was moved, skip extra pushes
        for (int i = 1; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
        }
    }
}
