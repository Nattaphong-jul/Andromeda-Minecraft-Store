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
 * Speed Hopper — transfers 10 items per eject cycle.
 *
 * ── How the flag reaches the block entity ────────────────────────────────────
 * SpeedHopperItem.create() stores the flag in DataComponents.BLOCK_ENTITY_DATA
 * (TypedEntityData). When placed, BlockItem.place() triggers:
 *   TypedEntityData.loadInto(blockEntity, registries)
 *     → BlockEntity.loadCustomOnly(ValueInput)         [final]
 *       → BlockEntity.loadAdditional(ValueInput)       [our @Inject]
 *
 * DataComponents.MAX_STACK_SIZE = 64 prevents BLOCK_ENTITY_DATA from capping
 * the item's max stack to 1.
 *
 * ── Why previous injection points failed ─────────────────────────────────────
 * - TAIL on pushItemsTick: HTH modifies cooldownTime ≠ MOVE_ITEM_SPEED before check
 * - AFTER INVOKE setCooldown inside tryMoveItems: HTH cancels tryMoveItems at HEAD,
 *   so its body (including the setCooldown call) never executes
 * - AFTER INVOKE tryMoveItems inside pushItemsTick: confirmed valid but cooldown
 *   check was still failing in certain scenarios with HTH active
 *
 * ── Correct approach (mirrors HopperTheHedgehog) ─────────────────────────────
 * @Inject at HEAD of tryMoveItems with priority 2000 (runs AFTER HTH, priority 1000).
 * - If HTH already ran (cir.isCancelled()): just push 9 more items for Speed Hoppers
 * - If nothing else ran: push 10 items ourselves, set cooldown, cancel with true
 * - If neither condition applies (nothing to eject): return without cancel
 *   so vanilla handles suck-in from containers above
 */
@Mixin(value = HopperBlockEntity.class, priority = 2000)
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

    /** Called when block is placed (via TypedEntityData.loadInto → loadCustomOnly) and on chunk load. */
    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void ae$load(ValueInput input, CallbackInfo ci) {
        ae_speedHopper = input.getBooleanOr(SpeedHopperItem.FLAG, false);
        com.andromeda.economy.AndromedaEconomy.LOGGER.info(
            "[SpeedHopperDEBUG] loadAdditional fired — flag={}", ae_speedHopper);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void ae$save(ValueOutput output, CallbackInfo ci) {
        if (ae_speedHopper) {
            output.putBoolean(SpeedHopperItem.FLAG, true);
            com.andromeda.economy.AndromedaEconomy.LOGGER.info(
                "[SpeedHopperDEBUG] saveAdditional wrote flag=true");
        }
    }

    // ── Speed logic ───────────────────────────────────────────────────────────

    /**
     * Fires at HEAD of tryMoveItems (priority 2000 = runs after HTH at 1000).
     *
     * Case A — another mod already cancelled (e.g. HTH handled the transfer):
     *   Push 9 more items for Speed Hoppers on top of what the other mod did.
     *
     * Case B — nothing else ran yet:
     *   Push up to 10 items ourselves. If any moved, set cooldown and cancel.
     *   If nothing moved, do NOT cancel — vanilla handles suck-in via BooleanSupplier.
     */
    @Inject(
        method = "tryMoveItems",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void ae$onTryMoveItems(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, BooleanSupplier bs,
            CallbackInfoReturnable<Boolean> cir) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;

        com.andromeda.economy.AndromedaEconomy.LOGGER.info(
            "[SpeedHopperDEBUG] tryMoveItems fired on SPEED hopper at {} — cancelled={}",
            pos, cir.isCancelled());

        if (cir.isCancelled()) {
            // Another mod handled this tick — add 9 extra ejects for Speed Hoppers
            for (int i = 0; i < 9; i++) {
                if (!ejectItems(level, pos, hopper)) break;
            }
            return;
        }

        // Nothing else ran — handle eject ourselves
        boolean moved = false;
        for (int i = 0; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
            moved = true;
        }
        if (!moved) return; // nothing ejected — let vanilla handle suck-in

        sh.ae$setCooldown(HopperBlockEntity.MOVE_ITEM_SPEED);
        cir.setReturnValue(true);
        cir.cancel();
    }
}
