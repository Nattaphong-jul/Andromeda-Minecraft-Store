package com.andromeda.economy.server.mixin;

import com.andromeda.economy.IAeSpeedHopper;
import com.andromeda.economy.SpeedHopperItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transfers the Speed Hopper flag from the item's CUSTOM_DATA to the newly
 * created block entity when a Speed Hopper is placed.
 *
 * Called AFTER the block and its block entity are in the world, so
 * level.getBlockEntity(pos) is guaranteed to return the HopperBlockEntity.
 */
@Mixin(Block.class)
public class SpeedHopperPlaceMixin {

    @Inject(method = "setPlacedBy", at = @At("TAIL"), remap = false)
    private void ae$onSetPlacedBy(Level level, BlockPos pos, BlockState state,
            LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!(state.getBlock() instanceof HopperBlock)) return;
        if (!SpeedHopperItem.is(stack)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IAeSpeedHopper sh) {
            sh.ae$setSpeedHopper(true);
            level.blockEntityChanged(pos);
        }
    }
}
