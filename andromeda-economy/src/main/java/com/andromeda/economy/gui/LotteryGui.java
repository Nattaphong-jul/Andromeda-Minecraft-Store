package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.data.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
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

import java.util.Random;

/**
 * Main lottery page — 54-slot chest.
 *
 * Slots 0–35  : cosmetic paper ticket items (rows 1–4)
 * Slots 36–44 : black glass filler (row 5)
 * Slot  45    : Lottery Result button
 * Slots 46–48 : black glass
 * Slot  49    : Close
 * Slots 50–52 : black glass
 * Slot  53    : Redeem button
 */
public class LotteryGui extends ChestMenu {

    private static final int TICKET_AREA = 36; // slots 0–35
    private static final int SLOT_CLOSE  = 45; // bottom-left corner
    private static final int SLOT_RESULT = 48;
    private static final int SLOT_REDEEM = 49; // adjacent to result

    private final SimpleContainer inv;

    private LotteryGui(int syncId, Inventory playerInv, SimpleContainer inv) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv = inv;
        populate();
    }

    public static void open(ServerPlayer player) {
        SimpleContainer inv = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new LotteryGui(syncId, playerInv, inv),
            Component.literal("Lottery")
        ));
    }

    private void populate() {
        Random rng = new Random();

        // Rows 1–4: cosmetic tickets
        for (int i = 0; i < TICKET_AREA; i++) {
            String num = String.format("%06d", rng.nextInt(1_000_000));
            ItemStack ticket = new ItemStack(Items.PAPER);
            ticket.set(DataComponents.CUSTOM_NAME,
                Component.literal("#" + num).withStyle(ChatFormatting.WHITE));
            ShopGui.markDisplay(ticket);
            inv.setItem(i, ticket);
        }

        // Row 5 + bottom row base: black glass
        ItemStack glass = blackGlass();
        for (int i = TICKET_AREA; i < 54; i++) inv.setItem(i, glass.copy());

        // Close button — bottom-left corner
        ItemStack closeBtn = new ItemStack(Items.BARRIER);
        closeBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Close").withStyle(ChatFormatting.RED));
        inv.setItem(SLOT_CLOSE, closeBtn);

        // Result + Redeem together (centre of bottom row)
        ItemStack resultBtn = new ItemStack(Items.WRITTEN_BOOK);
        resultBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Lottery Result").withStyle(ChatFormatting.YELLOW));
        ShopGui.markDisplay(resultBtn);
        inv.setItem(SLOT_RESULT, resultBtn);

        ItemStack redeemBtn = new ItemStack(Items.CHEST);
        redeemBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Redeem").withStyle(ChatFormatting.GREEN));
        ShopGui.markDisplay(redeemBtn);
        inv.setItem(SLOT_REDEEM, redeemBtn);
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

        if (slotId == SLOT_CLOSE)  { sp.closeContainer(); return; }
        if (slotId == SLOT_RESULT) { sp.closeContainer(); LotteryResultGui.open(sp); return; }
        if (slotId == SLOT_REDEEM) { sp.closeContainer(); LotteryRedeemGui.open(sp); return; }

        if (slotId >= 0 && slotId < TICKET_AREA) {
            handleTicketClick(sp);
        }
    }

    private void handleTicketClick(ServerPlayer sp) {
        LotteryManager lottery = AndromedaEconomy.lottery;

        if (lottery.isResultWindowActive()) {
            sp.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("Lottery is in result phase").withStyle(ChatFormatting.RED)));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (lottery.getTicketCount(sp.getStringUUID()) >= LotteryManager.MAX_TICKETS) {
            sp.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("You already have the maximum 5 tickets").withStyle(ChatFormatting.RED)));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        PlayerData data = AndromedaEconomy.db.getPlayer(sp.getStringUUID());
        if (data == null || data.balance < lottery.getTicketPrice()) {
            sp.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal("Insufficient funds").withStyle(ChatFormatting.RED)));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String assignedNumber = String.format("%06d", new Random().nextInt(1_000_000));
        sp.closeContainer();
        LotteryConfirmGui.open(sp, assignedNumber);
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
