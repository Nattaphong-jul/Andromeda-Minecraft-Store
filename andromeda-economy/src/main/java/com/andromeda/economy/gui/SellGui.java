package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * 27-slot sell GUI.
 *
 * Slots 0–17 : sell area (player places items freely)
 * Slots 18–25: empty, all interaction blocked
 * Slot 26    : ✔ Confirm Sale (Green Dye) — bottom-right corner
 */
public class SellGui extends ChestMenu {

    private static final int SELL_SLOTS   = 18;
    private static final int SLOT_CONFIRM = 26;

    private final SimpleContainer inv;

    private SellGui(int syncId, Inventory playerInv, SimpleContainer inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, inv, 3);
        this.inv = inv;
        // Fill slots 18-25 with glass so the blocked area is visually clear
        ItemStack pad = glass();
        for (int i = SELL_SLOTS; i < SLOT_CONFIRM; i++) inv.setItem(i, pad.copy());
        inv.setItem(SLOT_CONFIRM, makeConfirm());
    }

    public static void open(ServerPlayer player) {
        SimpleContainer inv = new SimpleContainer(27);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new SellGui(syncId, playerInv, inv, player),
            Component.literal("Sell Items")
        ));
    }

    private static ItemStack glass() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        return s;
    }

    private static ItemStack makeConfirm() {
        ItemStack s = new ItemStack(Items.GREEN_DYE);
        s.set(DataComponents.CUSTOM_NAME,
            Component.literal("✔ Confirm Sale").withStyle(ChatFormatting.GREEN));
        return s;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;

        if (slotId >= SELL_SLOTS && slotId < 27) {
            if (slotId == SLOT_CONFIRM) processSale(sp);
            return; // block all other bottom-row clicks
        }

        super.clicked(slotId, button, type, clicker);
    }

    private void processSale(ServerPlayer sp) {
        double total = 0.0;
        boolean hadSellable = false;

        for (int i = 0; i < SELL_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            double sellPrice = id != null ? AndromedaEconomy.prices.getSellPrice(id.toString()) : -1;

            if (sellPrice > 0) {
                total += sellPrice * stack.getCount();
                inv.setItem(i, ItemStack.EMPTY);
                hadSellable = true;
            } else {
                stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("No sell value").withStyle(ChatFormatting.GRAY)
                )));
            }
        }

        if (!hadSellable) {
            sp.sendSystemMessage(Component.literal("No sellable items found.").withStyle(ChatFormatting.RED));
            return;
        }

        AndromedaEconomy.db.addBalance(sp.getStringUUID(), total);
        sp.sendSystemMessage(Component.literal(
            "Sold items for " + EconomyUtils.format(total) + " THB"
        ).withStyle(ChatFormatting.GREEN));
        AndromedaEconomy.playMoneySound(sp);
        AndromedaEconomy.hud.update(sp);
        sp.closeContainer();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (int i = 0; i < SELL_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            inv.setItem(i, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    /**
     * Shift-click behaviour:
     *   Sell area (0-17)        -> move to player inventory/hotbar (27-62)
     *   Player inventory (27-62) -> move to sell area (0-17)
     *   Bottom row (18-26)       -> blocked
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        Slot slotObj = this.slots.get(slot);
        if (!slotObj.hasItem()) return ItemStack.EMPTY;

        ItemStack stack    = slotObj.getItem();
        ItemStack original = stack.copy();

        if (slot < SELL_SLOTS) {
            // Sell area -> player inventory + hotbar
            if (!this.moveItemStackTo(stack, 27, 63, true)) return ItemStack.EMPTY;
        } else if (slot >= 27) {
            // Player inventory -> sell area only
            if (!this.moveItemStackTo(stack, 0, SELL_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY; // bottom row
        }

        if (stack.isEmpty()) slotObj.set(ItemStack.EMPTY);
        else slotObj.setChanged();

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        return original;
    }
}
