package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 54-slot ender chest (6 rows instead of the vanilla 3).
 *
 * Slots 0–26 : the player's real ender chest inventory (synced on open/close)
 * Slots 27–53: extended storage, persisted per-player in SQLite
 *
 * Vanilla ender chest is intercepted server-side via UseBlockCallback in
 * AndromedaEconomy — no client mod required.
 */
public class EnderChestGui extends ChestMenu {

    private static final int REAL_SLOTS = 27;
    private static final int EXT_SLOTS  = 27;

    private final SimpleContainer combined;
    private final ServerPlayer owner;

    private EnderChestGui(int syncId, Inventory playerInv, SimpleContainer combined, ServerPlayer owner) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, combined, 6);
        this.combined = combined;
        this.owner    = owner;
    }

    public static void open(ServerPlayer player) {
        SimpleContainer combined = new SimpleContainer(REAL_SLOTS + EXT_SLOTS);

        // Copy the real 27-slot ender chest into the top half
        var realInv = player.getEnderChestInventory();
        for (int i = 0; i < REAL_SLOTS; i++) {
            combined.setItem(i, realInv.getItem(i).copy());
        }

        // Load the extra 27 slots from the database
        SimpleContainer extra = new SimpleContainer(EXT_SLOTS);
        if (AndromedaEconomy.registryAccess != null) {
            AndromedaEconomy.db.loadEnderChestExt(
                player.getStringUUID(), extra, AndromedaEconomy.registryAccess);
        }
        for (int i = 0; i < EXT_SLOTS; i++) {
            combined.setItem(REAL_SLOTS + i, extra.getItem(i));
        }

        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new EnderChestGui(syncId, playerInv, combined, player),
            Component.translatable("container.enderchest")
        ));

        realInv.startOpen(player); // trigger the block open animation
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!(player instanceof ServerPlayer sp)) return;

        // Write top 27 slots back to the real ender chest inventory
        var realInv = sp.getEnderChestInventory();
        for (int i = 0; i < REAL_SLOTS; i++) {
            realInv.setItem(i, combined.getItem(i));
        }

        // Persist the bottom 27 slots to the database
        if (AndromedaEconomy.registryAccess != null) {
            SimpleContainer extra = new SimpleContainer(EXT_SLOTS);
            for (int i = 0; i < EXT_SLOTS; i++) {
                extra.setItem(i, combined.getItem(REAL_SLOTS + i));
            }
            AndromedaEconomy.db.saveEnderChestExt(
                sp.getStringUUID(), extra, AndromedaEconomy.registryAccess);
        }

        sp.getEnderChestInventory().stopOpen(sp); // trigger the block close animation
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    /**
     * Shift-click:
     *   Real ender chest (0–26) or extended (27–53) → player inventory/hotbar (54–89)
     *   Player inventory (54–89)                    → extended storage first (27–53),
     *                                                  then real ender chest (0–26)
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        Slot slotObj = this.slots.get(slot);
        if (!slotObj.hasItem()) return ItemStack.EMPTY;

        ItemStack stack    = slotObj.getItem();
        ItemStack original = stack.copy();

        if (slot < REAL_SLOTS + EXT_SLOTS) {
            // Ender chest area → player inventory
            if (!this.moveItemStackTo(stack, 54, 90, true)) return ItemStack.EMPTY;
        } else if (slot >= 54) {
            // Player inventory → try extended first, then real ender chest
            if (!this.moveItemStackTo(stack, REAL_SLOTS, REAL_SLOTS + EXT_SLOTS, false)
                    && !this.moveItemStackTo(stack, 0, REAL_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slotObj.set(ItemStack.EMPTY);
        else slotObj.setChanged();

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        return original;
    }
}
