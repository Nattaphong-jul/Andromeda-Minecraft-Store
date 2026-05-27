package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.data.LotteryManager.PlayerLotteryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
 * Slots 0..N-1 : previous-round tickets (claimable during claim window)
 * Slots N..M   : current-round tickets (draw pending)
 * Slot 45      : Close (bottom-left corner)
 * All others   : black glass
 */
public class LotteryRedeemGui extends ChestMenu {

    private static final int SLOT_CLOSE = 45;

    private final SimpleContainer inv;
    private final String playerUuid;
    /** Number of previous-round ticket slots (0-based from slot 0). */
    private int prevTicketCount = 0;

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
        MinecraftServer server = player.level().getServer();
        boolean claimOpen = lottery.isClaimWindowOpen(server);

        int slot = 0;

        // Previous-round tickets (claimable if claim window is open)
        if (claimOpen) {
            PlayerLotteryData prevData = lottery.getPreviousRoundData(player.getStringUUID());
            if (prevData != null && !prevData.numbers.isEmpty()) {
                String[] prevWinNums = lottery.getPreviousWinningNumbers();
                int prevRoundId = lottery.getPreviousRoundId();
                for (int i = 0; i < prevData.numbers.size(); i++) {
                    inv.setItem(slot++, buildPrevTicket(prevData, i, prevWinNums, prevRoundId));
                }
            }
        }
        prevTicketCount = slot;

        // Current-round tickets (draw pending — not claimable yet)
        PlayerLotteryData currData = lottery.getPlayerData(player.getStringUUID());
        if (currData != null && !currData.numbers.isEmpty()) {
            int currRoundId = lottery.getCurrentRoundId();
            for (int i = 0; i < currData.numbers.size(); i++) {
                inv.setItem(slot++, buildCurrentTicket(currData.numbers.get(i), currRoundId));
            }
        }
    }

    /** Builds a previous-round ticket item with win/lose/claimed lore. */
    private static ItemStack buildPrevTicket(PlayerLotteryData data, int idx,
                                              String[] winNums, int roundId) {
        String number = data.numbers.get(idx);
        ItemStack ticket = new ItemStack(Items.PAPER);
        ticket.set(DataComponents.CUSTOM_NAME,
            Component.literal("#" + number).withStyle(ChatFormatting.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Round #" + roundId).withStyle(ChatFormatting.GRAY));

        boolean isWinner = false;
        for (int t = 0; t < 3; t++) {
            if (winNums[t] != null && winNums[t].equals(number)) {
                isWinner = true;
                if (data.claimedTiers[t]) {
                    lore.add(Component.literal("Claimed (Tier " + (t + 1) + ")")
                        .withStyle(ChatFormatting.GRAY));
                } else {
                    int count = Collections.frequency(data.numbers, number);
                    double prize = count * LotteryManager.PRIZES[t];
                    lore.add(Component.literal(
                        "★ Tier " + (t + 1) + " — Click to claim " + EconomyUtils.compact(prize) + " THB")
                        .withStyle(ChatFormatting.GREEN));
                }
            }
        }
        if (!isWinner) {
            lore.add(Component.literal("No match this round").withStyle(ChatFormatting.RED));
        }

        ticket.set(DataComponents.LORE, new ItemLore(lore));
        ShopGui.markDisplay(ticket);
        return ticket;
    }

    /** Builds a current-round ticket item (draw pending). */
    private static ItemStack buildCurrentTicket(String number, int roundId) {
        ItemStack ticket = new ItemStack(Items.PAPER);
        ticket.set(DataComponents.CUSTOM_NAME,
            Component.literal("#" + number).withStyle(ChatFormatting.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Round #" + roundId).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Draw pending...").withStyle(ChatFormatting.GRAY));

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

        // Only previous-round slots are clickable for claiming
        if (slotId < 0 || slotId >= prevTicketCount) return;

        LotteryManager lottery = AndromedaEconomy.lottery;
        MinecraftServer server = sp.level().getServer();
        if (!lottery.isClaimWindowOpen(server)) return;

        PlayerLotteryData prevData = lottery.getPreviousRoundData(sp.getStringUUID());
        if (prevData == null || slotId >= prevData.numbers.size()) return;

        String ticketNumber = prevData.numbers.get(slotId);
        double prize = lottery.claimPrize(sp.getStringUUID(), ticketNumber, server);
        if (prize <= 0) return;

        AndromedaEconomy.db.addBalance(sp.getStringUUID(), prize);
        AndromedaEconomy.playSound(sp, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        sp.sendSystemMessage(Component.literal(
            "Claimed " + EconomyUtils.compact(prize) + " THB lottery prize!")
            .withStyle(ChatFormatting.GREEN));
        AndromedaEconomy.hud.update(sp);

        // Refresh previous-round ticket display in-place
        String[] prevWinNums = lottery.getPreviousWinningNumbers();
        int prevRoundId = lottery.getPreviousRoundId();
        for (int i = 0; i < prevData.numbers.size(); i++) {
            inv.setItem(i, buildPrevTicket(prevData, i, prevWinNums, prevRoundId));
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
