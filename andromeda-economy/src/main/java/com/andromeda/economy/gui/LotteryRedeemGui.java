package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.data.LotteryManager.PlayerLotteryData;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redeem page — 54-slot chest.
 *
 * Displays all tickets the player holds this round.
 * Winning tickets (during result window) are clickable to claim prizes.
 * Slot 49 : Close
 * Others  : black glass or ticket items starting from slot 0
 */
public class LotteryRedeemGui extends ChestMenu {

    private static final int SLOT_CLOSE = 49;

    private final SimpleContainer inv;
    private final String playerUuid;

    private LotteryRedeemGui(int syncId, Inventory playerInv, SimpleContainer inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv = inv;
        this.playerUuid = player.getStringUUID();
        populate(player);
    }

    public static void open(ServerPlayer player) {
        SimpleContainer inv = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new LotteryRedeemGui(syncId, playerInv, inv, player),
            Component.literal("Redeem Tickets")
        ));
    }

    private void populate(ServerPlayer player) {
        ItemStack glass = blackGlass();
        for (int i = 0; i < 54; i++) inv.setItem(i, glass.copy());

        ItemStack closeBtn = new ItemStack(Items.BARRIER);
        closeBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Close").withStyle(ChatFormatting.RED));
        inv.setItem(SLOT_CLOSE, closeBtn);

        LotteryManager lottery = AndromedaEconomy.lottery;
        PlayerLotteryData data = lottery.getPlayerData(player.getStringUUID());
        if (data == null || data.numbers.isEmpty()) return;

        String[] winNums = lottery.getCurrentWinningNumbers();
        boolean resultWindow = lottery.isResultWindowActive();

        for (int i = 0; i < data.numbers.size(); i++) {
            inv.setItem(i, buildTicketStack(data, i, winNums, resultWindow));
        }
    }

    private static ItemStack buildTicketStack(PlayerLotteryData data, int idx,
                                               String[] winNums, boolean resultWindow) {
        String number = data.numbers.get(idx);
        ItemStack ticket = new ItemStack(Items.PAPER);
        ticket.set(DataComponents.CUSTOM_NAME,
            Component.literal("#" + number).withStyle(ChatFormatting.WHITE));

        List<Component> lore = new ArrayList<>();
        if (!resultWindow) {
            lore.add(Component.literal("Draw pending...").withStyle(ChatFormatting.GRAY));
        } else {
            boolean isWinner = false;
            for (int t = 0; t < 3; t++) {
                if (winNums[t].equals(number)) {
                    isWinner = true;
                    if (data.claimedTiers[t]) {
                        lore.add(Component.literal("Claimed (Tier " + (t + 1) + ")")
                            .withStyle(ChatFormatting.GRAY));
                    } else {
                        int count = Collections.frequency(data.numbers, number);
                        double prize = count * LotteryManager.PRIZES[t];
                        lore.add(Component.literal(
                            "Winner! Tier " + (t + 1) + " — Click to claim " + EconomyUtils.compact(prize) + " THB")
                            .withStyle(ChatFormatting.GREEN));
                    }
                }
            }
            if (!isWinner) {
                lore.add(Component.literal("No match this round").withStyle(ChatFormatting.RED));
            }
        }
        ticket.set(DataComponents.LORE, new ItemLore(lore));
        ShopGui.markDisplay(ticket);
        return ticket;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;

        if (slotId == SLOT_CLOSE) {
            sp.closeContainer();
            LotteryGui.open(sp);
            return;
        }

        LotteryManager lottery = AndromedaEconomy.lottery;
        if (!lottery.isResultWindowActive()) return;

        PlayerLotteryData data = lottery.getPlayerData(sp.getStringUUID());
        if (data == null || slotId < 0 || slotId >= data.numbers.size()) return;

        String ticketNumber = data.numbers.get(slotId);
        double prize = lottery.claimPrize(sp.getStringUUID(), ticketNumber);
        if (prize <= 0) return;

        AndromedaEconomy.db.addBalance(sp.getStringUUID(), prize);
        AndromedaEconomy.playSound(sp, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        sp.sendSystemMessage(Component.literal(
            "Claimed " + EconomyUtils.compact(prize) + " THB lottery prize!")
            .withStyle(ChatFormatting.GREEN));
        AndromedaEconomy.hud.update(sp);

        // Refresh ticket display in-place
        String[] winNums = lottery.getCurrentWinningNumbers();
        for (int i = 0; i < data.numbers.size(); i++) {
            inv.setItem(i, buildTicketStack(data, i, winNums, true));
        }
        broadcastChanges();
    }

    private static ItemStack blackGlass() {
        ItemStack s = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        ShopGui.markDisplay(s);
        return s;
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
