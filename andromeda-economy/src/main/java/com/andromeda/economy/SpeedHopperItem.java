package com.andromeda.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.List;

public final class SpeedHopperItem {
    public static final String SHOP_ID = "andromeda:speed_hopper";

    private SpeedHopperItem() {}

    public static ItemStack createItem(int count) {
        ItemStack stack = new ItemStack(Items.HOPPER, count);
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("Speed Hopper").withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Transfers 10 items at double speed").withStyle(ChatFormatting.GRAY)
        )));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        output.putBoolean("ae_speed_hopper", true);
        BlockItem.setBlockEntityData(stack, BlockEntityType.HOPPER, output);
        return stack;
    }

    public static boolean isSpeedHopper(ItemStack stack) {
        if (stack.getItem() != Items.HOPPER) return false;
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return false;
        return data.getUnsafe().getBooleanOr("ae_speed_hopper", false);
    }
}
