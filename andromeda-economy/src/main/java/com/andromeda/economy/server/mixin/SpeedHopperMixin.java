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
 * Speed Hopper: transfers 10 items per cycle.
 *
 * ROOT CAUSE of all previous failures:
 *   Every @Inject and @Shadow used remap=false. Fabric Loom remaps compiled
 *   mod bytecode from Mojang names → Intermediary before shipping the JAR.
 *   At runtime, Fabric loads Intermediary-named bytecode. With remap=false,
 *   Mixin matched "loadAdditional" against Intermediary names like "m_11008_"
 *   and found nothing — injections silently never fired.
 *
 *   Fix: remove remap=false everywhere. Loom writes a refmap.json at build
 *   time that maps Mojang names → Intermediary. Mixin uses that refmap at
 *   runtime and correctly resolves all targets.
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
        com.andromeda.economy.AndromedaEconomy.LOGGER.info(
            "[SpeedHopperDEBUG] loadAdditional fired — flag={}", ae_speedHopper);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void ae$save(ValueOutput output, CallbackInfo ci) {
        if (ae_speedHopper) {
            output.putBoolean(SpeedHopperItem.FLAG, true);
            com.andromeda.economy.AndromedaEconomy.LOGGER.info(
                "[SpeedHopperDEBUG] saveAdditional wrote flag=true");
        }
    }

    // ── Speed logic ───────────────────────────────────────────────────────────

    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private static void ae$onTryMoveItems(Level level, BlockPos pos, BlockState state,
            HopperBlockEntity hopper, BooleanSupplier bs,
            CallbackInfoReturnable<Boolean> cir) {
        IAeSpeedHopper sh = (IAeSpeedHopper) hopper;
        if (!sh.ae$isSpeedHopper()) return;

        com.andromeda.economy.AndromedaEconomy.LOGGER.info(
            "[SpeedHopperDEBUG] tryMoveItems fired on SPEED hopper at {}", pos);

        if (cir.isCancelled()) {
            for (int i = 0; i < 9; i++) {
                if (!ejectItems(level, pos, hopper)) break;
            }
            return;
        }

        boolean moved = false;
        for (int i = 0; i < 10; i++) {
            if (!ejectItems(level, pos, hopper)) break;
            moved = true;
        }
        if (!moved) return;

        sh.ae$setCooldown(HopperBlockEntity.MOVE_ITEM_SPEED);
        cir.setReturnValue(true);
        cir.cancel();
    }
}
