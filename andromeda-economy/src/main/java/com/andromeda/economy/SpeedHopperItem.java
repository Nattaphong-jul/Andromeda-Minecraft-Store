package com.andromeda.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class SpeedHopperItem {

    /** NBT key stored in CustomData on the item AND in the block entity NBT. */
    public static final String FLAG = "ae_speed_hopper";

    private SpeedHopperItem() {}

    /**
     * Creates a Speed Hopper item stack with the given count.
     *
     * Uses CUSTOM_DATA (not BLOCK_ENTITY_DATA) so items remain stackable (max 64).
     * The flag is transferred to the block entity by SpeedHopperPlaceMixin on placement,
     * and persisted via SpeedHopperMixin saveAdditional/loadAdditional.
     */
    public static ItemStack create(int count) {
        ItemStack stack = new ItemStack(Items.HOPPER, count);

        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("Speed Hopper")
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FLAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return stack;
    }

    /** Convenience overload for single items. */
    public static ItemStack create() {
        return create(1);
    }

    /** Returns true if this item is a Speed Hopper. */
    public static boolean is(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.HOPPER) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(FLAG);
    }
}
