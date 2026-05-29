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
 * Adds Speed Hopper behaviour (10 items per cycle) to tagged HopperBlockEntities.
 *
 * The flag is written to the block entity in two ways:
 *   1. On placement: SpeedHopperPlaceMixin intercepts Block.setPlacedBy and calls
 *      ae$setSpeedHopper(true) directly on the new block entity.
 *   2. On reload: loadAdditional reads ae_speed_hopper from the saved NBT.
 *
 * CUSTOM_DATA on the item is used instead of BLOCK_ENTITY_DATA so that Speed Hopper
 * items remain stackable (max 64 per slot). BLOCK_ENTITY_DATA caps stacks to 1.
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

    // ── Persistence (reload from disk) ────────────────────────────────────────

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
     * Fires at TAIL of pushItemsTick.
     *
     * cooldownTime is MOVE_ITEM_SPEED (8) only when tryMoveItems just succeeded
     * (items were moved). We push 9 more items for a total of 10 per cycle.
     *
     * NOTE: requires HopperTheHedgehog to be REMOVED from the server.
     * HTH changes the cooldown to a value != MOVE_ITEM_SPEED, breaking this check.
     */
    @Inject(method = "pushItemsTick", at = @At("TAIL"), remap = false)
    private static void ae$pushItemsTick(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, CallbackInfo ci) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;
        if (sh.ae$getCooldown() != HopperBlockEntity.MOVE_ITEM_SPEED) return;
        for (int i = 1; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
        }
    }
}
