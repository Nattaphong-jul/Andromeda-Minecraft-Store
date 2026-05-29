package com.andromeda.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class AmethystPickaxe {

    public static final String MARKER = "ae_amethyst_pickaxe";

    private AmethystPickaxe() {}

    public static ItemStack create() {
        ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);

        pick.set(DataComponents.CUSTOM_NAME,
            Component.literal("Amethyst Pickaxe")
                .withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(false)));

        if (AndromedaEconomy.registryAccess != null) {
            Registry<Enchantment> reg = AndromedaEconomy.registryAccess
                .lookup(Registries.ENCHANTMENT).orElse(null);
            if (reg != null) {
                ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                addEnch(m, reg, "minecraft:efficiency",  5);
                addEnch(m, reg, "minecraft:unbreaking",  3);
                addEnch(m, reg, "minecraft:fortune",     3);
                addEnch(m, reg, "minecraft:mending",     1);
                pick.set(DataComponents.ENCHANTMENTS, m.toImmutable());
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        pick.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        return pick;
    }

    public static boolean is(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.NETHERITE_PICKAXE) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return false;
        return data.copyTag().contains(MARKER); // contains() returns boolean
    }

    private static void addEnch(ItemEnchantments.Mutable m, Registry<Enchantment> reg, String id, int level) {
        Enchantment ench = reg.getValue(Identifier.parse(id));
        if (ench != null) m.set(reg.wrapAsHolder(ench), level);
    }
}
