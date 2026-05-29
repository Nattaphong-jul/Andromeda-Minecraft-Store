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
 * Makes tagged hoppers transfer 10 items per cycle instead of 1.
 *
 * Root cause of the previous failure:
 *   The flag was stored in CUSTOM_DATA on the item. MC only copies
 *   BLOCK_ENTITY_DATA into the block entity on placement; CUSTOM_DATA is
 *   ignored. So ae_speedHopper was always false and the speed code never ran.
 *   Fix: SpeedHopperItem now stores the flag in BLOCK_ENTITY_DATA.
 *
 * How it works:
 *   pushItemsTick decrements cooldown each tick. When cooldown reaches 0 it
 *   calls tryMoveItems, which calls ejectItems once and then setCooldown(8).
 *   At the TAIL of pushItemsTick, cooldownTime == MOVE_ITEM_SPEED (8) iff
 *   items were just moved. We then call ejectItems 9 more times for a total
 *   of 10 items moved per 8-tick cycle.
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

    @Override public boolean ae$isSpeedHopper() { return ae_speedHopper; }
    @Override public int     ae$getCooldown()   { return cooldownTime; }
    @Override public void    ae$setCooldown(int v) { cooldownTime = v; }

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

    @Inject(method = "pushItemsTick", at = @At("TAIL"), remap = false)
    private static void ae$pushItemsTick(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, CallbackInfo ci) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;
        // cooldownTime == MOVE_ITEM_SPEED (8) only when ejectItems/suckInItems
        // just succeeded this tick inside tryMoveItems.
        if (sh.ae$getCooldown() != HopperBlockEntity.MOVE_ITEM_SPEED) return;
        // Push up to 9 more items — total 10 per 8-tick cycle.
        for (int i = 1; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
        }
    }
}
