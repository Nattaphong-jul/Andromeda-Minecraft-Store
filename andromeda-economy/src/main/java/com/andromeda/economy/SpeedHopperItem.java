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

    /** NBT key written into the block entity when the hopper is placed. */
    public static final String FLAG = "ae_speed_hopper";

    private SpeedHopperItem() {}

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.HOPPER);

        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("Speed Hopper")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // BLOCK_ENTITY_DATA uses TypedEntityData in MC 26.1.2.
        // When the item is placed, MC calls TypedEntityData.loadInto(blockEntity, holderLookup)
        // which merges the tag into the block entity via loadAdditional — our Mixin reads it there.
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FLAG, true);
        stack.set(DataComponents.BLOCK_ENTITY_DATA,
            TypedEntityData.of(BlockEntityType.HOPPER, tag));

        return stack;
    }
}
