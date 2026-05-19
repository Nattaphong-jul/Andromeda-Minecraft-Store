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
 * 54-slot (large chest) sell GUI.
 *
 * Slots 0–44  : sell area — 5 full rows, player places items here freely
 * Slots 45–52 : glass padding (bottom row)
 * Slot  53    : ✔ Confirm Sale (Green Dye) — bottom-right corner
 */
public class SellGui extends ChestMenu {

    private static final int SELL_SLOTS   = 45;   // 5 rows × 9
    private static final int SLOT_CONFIRM = 53;   // last slot in 54-slot chest

    private final SimpleContainer inv;

    private SellGui(int syncId, Inventory playerInv, SimpleContainer inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv = inv;
        ItemStack pad = glass();
        for (int i = SELL_SLOTS; i < SLOT_CONFIRM; i++) inv.setItem(i, pad.copy());
        inv.setItem(SLOT_CONFIRM, makeConfirm());
    }

    public static void open(ServerPlayer player) {
        SimpleContainer inv = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new SellGui(syncId, playerInv, inv, player),
            Component.literal("Sell Items")
        ));
    }

    private static ItemStack glass() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        ShopGui.markDisplay(s);
        return s;
    }

    private static ItemStack makeConfirm() {
        ItemStack s = new ItemStack(Items.GREEN_DYE);
        s.set(DataComponents.CUSTOM_NAME,
            Component.literal("✔ Confirm Sale").withStyle(ChatFormatting.GREEN));
        ShopGui.markDisplay(s);
        return s;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;
        if (slotId >= SELL_SLOTS && slotId < 54) {
            if (slotId == SLOT_CONFIRM) processSale(sp);
            return;
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
     * Shift-click routing:
     *   Sell area (0–44)         → player inventory/hotbar (54–89)
     *   Player inventory (54–89) → sell area (0–44)
     *   Bottom row (45–53)       → blocked
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        Slot slotObj = this.slots.get(slot);
        if (!slotObj.hasItem()) return ItemStack.EMPTY;

        ItemStack stack    = slotObj.getItem();
        ItemStack original = stack.copy();

        if (slot < SELL_SLOTS) {
            if (!this.moveItemStackTo(stack, 54, 90, true)) return ItemStack.EMPTY;
        } else if (slot >= 54) {
            if (!this.moveItemStackTo(stack, 0, SELL_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slotObj.set(ItemStack.EMPTY);
        else slotObj.setChanged();

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        return original;
    }
}
