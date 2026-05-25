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
 *
 * Implementation notes:
 *  In MC 26.1.2, setCooldown(8) is called inside tryMoveItems (not pushItemsTick).
 *  We inject at TAIL of pushItemsTick. If cooldownTime == MOVE_ITEM_SPEED (8) at
 *  that point, tryMoveItems just moved items this tick — we halve it and push more.
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

    @Override public boolean ae$isSpeedHopper() { return ae_speedHopper; }
    @Override public int ae$getCooldown()        { return cooldownTime; }
    @Override public void ae$setCooldown(int v)  { cooldownTime = v; }

    // ── NBT persistence ───────────────────────────────────────────────────────

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void ae$load(ValueInput input, CallbackInfo ci) {
        ae_speedHopper = input.getBooleanOr("ae_speed_hopper", false);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void ae$save(ValueOutput output, CallbackInfo ci) {
        if (ae_speedHopper) output.putBoolean("ae_speed_hopper", true);
    }

    // ── Speed logic ───────────────────────────────────────────────────────────

    /**
     * Fires every tick at the end of pushItemsTick.
     *
     * When tryMoveItems moves items, it calls setCooldown(MOVE_ITEM_SPEED=8).
     * At the TAIL of pushItemsTick, cooldownTime == 8 iff items were JUST moved
     * this tick (any other positive value means the hopper was already waiting).
     * We halve it to 4 ticks (double speed) then push up to 9 more items.
     */
    @Inject(method = "pushItemsTick", at = @At("TAIL"), remap = false)
    private static void ae$pushItemsTick(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, CallbackInfo ci) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;
        if (sh.ae$getCooldown() != HopperBlockEntity.MOVE_ITEM_SPEED) return;
        sh.ae$setCooldown(HopperBlockEntity.MOVE_ITEM_SPEED / 2);
        for (int i = 1; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
        }
    }
}
