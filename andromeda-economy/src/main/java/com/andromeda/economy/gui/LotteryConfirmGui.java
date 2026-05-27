package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.data.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Ticket purchase confirm page — 27-slot (3-row) chest, items centred on the middle row.
 *
 * Row 2 (slots 9–17):
 *   Slot 11 : Red glass  — Cancel
 *   Slot 13 : Paper      — ticket number + price lore  (centre)
 *   Slot 15 : Lime glass — Confirm
 * All others : black glass
 */
public class LotteryConfirmGui extends ChestMenu {

    private static final int SLOT_CANCEL  = 11;
    private static final int SLOT_TICKET  = 13;
    private static final int SLOT_CONFIRM = 15;

    private final SimpleContainer inv;
    private final String ticketNumber;
    private long lastClickMs = 0;

    private LotteryConfirmGui(int syncId, Inventory playerInv, SimpleContainer inv, String ticketNumber) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, inv, 3);
        this.inv = inv;
        this.ticketNumber = ticketNumber;
        populate();
    }

    public static void open(ServerPlayer player, String ticketNumber) {
        SimpleContainer inv = new SimpleContainer(27);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new LotteryConfirmGui(syncId, playerInv, inv, ticketNumber),
            Component.literal("Confirm Ticket")
        ));
    }

    private void populate() {
        ItemStack glass = blackGlass();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass.copy());

        ItemStack cancelBtn = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        cancelBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Cancel").withStyle(ChatFormatting.RED));
        ShopGui.markDisplay(cancelBtn);
        inv.setItem(SLOT_CANCEL, cancelBtn);

        ItemStack ticket = new ItemStack(Items.PAPER);
        ticket.set(DataComponents.CUSTOM_NAME,
            Component.literal(ticketNumber).withStyle(ChatFormatting.WHITE));
        ticket.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Price: " + EconomyUtils.compact(AndromedaEconomy.lottery.getTicketPrice()) + " THB")
                .withStyle(ChatFormatting.GREEN)
        )));
        ShopGui.markDisplay(ticket);
        inv.setItem(SLOT_TICKET, ticket);

        ItemStack confirmBtn = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        confirmBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Confirm").withStyle(ChatFormatting.GREEN));
        ShopGui.markDisplay(confirmBtn);
        inv.setItem(SLOT_CONFIRM, confirmBtn);
    }

    private static ItemStack blackGlass() {
        ItemStack s = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        ShopGui.markDisplay(s);
        return s;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;

        if (slotId == SLOT_CANCEL) {
            sp.closeContainer();
            LotteryGui.open(sp);
            return;
        }
        if (slotId == SLOT_CONFIRM) {
            long now = System.currentTimeMillis();
            if (now - lastClickMs < 400) return;
            lastClickMs = now;
            attemptPurchase(sp);
        }
    }

    private void attemptPurchase(ServerPlayer sp) {
        LotteryManager lottery = AndromedaEconomy.lottery;
        double price = lottery.getTicketPrice();

        if (lottery.getTicketCount(sp.getStringUUID()) >= LotteryManager.MAX_TICKETS) {
            sp.sendSystemMessage(Component.literal("You already have the maximum 2 tickets.").withStyle(ChatFormatting.RED));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        PlayerData data = AndromedaEconomy.db.getPlayer(sp.getStringUUID());
        if (data == null || data.balance < price) {
            sp.sendSystemMessage(Component.literal("Insufficient balance.").withStyle(ChatFormatting.RED));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        AndromedaEconomy.db.setBalance(sp.getStringUUID(), data.balance - price);
        AndromedaEconomy.db.addSpend(sp.getStringUUID(), price);
        lottery.buyTicket(sp.getStringUUID(), ticketNumber);

        AndromedaEconomy.playSound(sp, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        AndromedaEconomy.hud.update(sp);

        sp.closeContainer();
        LotteryGui.open(sp);
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
