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
 * Ticket purchase confirm page — 9-slot (1-row) chest.
 *
 * Slot 0 : Red glass  — Cancel
 * Slot 2 : Paper      — ticket preview with assigned number + price lore
 * Slot 4 : Lime glass — Confirm
 * Others : empty
 */
public class LotteryConfirmGui extends ChestMenu {

    private static final int SLOT_CANCEL  = 0;
    private static final int SLOT_TICKET  = 2;
    private static final int SLOT_CONFIRM = 4;

    private final SimpleContainer inv;
    private final String ticketNumber;
    private long lastClickMs = 0;

    private LotteryConfirmGui(int syncId, Inventory playerInv, SimpleContainer inv, String ticketNumber) {
        super(MenuType.GENERIC_9x1, syncId, playerInv, inv, 1);
        this.inv = inv;
        this.ticketNumber = ticketNumber;
        populate();
    }

    public static void open(ServerPlayer player, String ticketNumber) {
        SimpleContainer inv = new SimpleContainer(9);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new LotteryConfirmGui(syncId, playerInv, inv, ticketNumber),
            Component.literal("Confirm Ticket")
        ));
    }

    private void populate() {
        for (int i = 0; i < 9; i++) inv.setItem(i, ItemStack.EMPTY);

        ItemStack cancelBtn = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        cancelBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Cancel").withStyle(ChatFormatting.RED));
        ShopGui.markDisplay(cancelBtn);
        inv.setItem(SLOT_CANCEL, cancelBtn);

        ItemStack ticket = new ItemStack(Items.PAPER);
        ticket.set(DataComponents.CUSTOM_NAME,
            Component.literal("#" + ticketNumber).withStyle(ChatFormatting.WHITE));
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

        if (lottery.isResultWindowActive()) {
            sp.sendSystemMessage(Component.literal("Lottery is in result phase.").withStyle(ChatFormatting.RED));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (lottery.getTicketCount(sp.getStringUUID()) >= LotteryManager.MAX_TICKETS) {
            sp.sendSystemMessage(Component.literal("You already have the maximum 5 tickets.").withStyle(ChatFormatting.RED));
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
