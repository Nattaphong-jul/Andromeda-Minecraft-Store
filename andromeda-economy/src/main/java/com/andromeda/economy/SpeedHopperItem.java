package com.andromeda.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class SpeedHopperItem {

    public static final String FLAG = "ae_speed_hopper";

    private SpeedHopperItem() {}

    /**
     * Creates a Speed Hopper item.
     *
     * Uses BLOCK_ENTITY_DATA so the flag transfers automatically to the block entity
     * when placed. Call chain: TypedEntityData.loadInto → BlockEntity.loadCustomOnly
     * → BlockEntity.loadAdditional → our Mixin reads the flag.
     *
     * Sets MAX_STACK_SIZE = 64 to prevent BLOCK_ENTITY_DATA from capping stacks to 1.
     */
    public static ItemStack create(int count) {
        ItemStack stack = new ItemStack(Items.HOPPER, count);

        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("Speed Hopper")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.MAX_STACK_SIZE, 64); // override the auto-1 cap from BLOCK_ENTITY_DATA

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FLAG, true);
        stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityType.HOPPER, tag));

        return stack;
    }

    public static ItemStack create() { return create(1); }
}
