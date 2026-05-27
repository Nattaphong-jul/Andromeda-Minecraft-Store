package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.LotteryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.List;

/**
 * Lottery Result page — 54-slot chest.
 *
 * Always shows the most recent (previous-round) winning numbers.
 * When the claim window is open they are highlighted as claimable.
 * Slot 11 : 1st Prize
 * Slot 13 : 2nd Prize
 * Slot 15 : 3rd Prize
 * Slot 45 : Close
 * All others : black glass
 */
public class LotteryResultGui extends ChestMenu {

    private static final int[] RESULT_SLOTS = {11, 13, 15};
    private static final int SLOT_CLOSE = 45;

    private final SimpleContainer inv;

    private LotteryResultGui(int syncId, Inventory playerInv, SimpleContainer inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv = inv;
        populate(player);
    }

    public static void open(ServerPlayer player) {
        SimpleContainer inv = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new LotteryResultGui(syncId, playerInv, inv, player),
            Component.literal("Lottery Result")
        ));
    }

    private void populate(ServerPlayer player) {
        ItemStack glass = blackGlass();
        for (int i = 0; i < 54; i++) inv.setItem(i, glass.copy());

        LotteryManager lottery = AndromedaEconomy.lottery;
        boolean claimOpen = lottery.isClaimWindowOpen(player.level().getServer());
        String[] numbers = lottery.getPreviousWinningNumbers();
        boolean hasPrevious = lottery.getPreviousRoundId() > 0;

        String[] tierLabels = {"1st", "2nd", "3rd"};
        for (int t = 0; t < 3; t++) {
            String num = (numbers[t] == null || numbers[t].isEmpty()) ? "???" : numbers[t];
            ItemStack book = new ItemStack(Items.BOOK);
            book.set(DataComponents.CUSTOM_NAME,
                Component.literal(tierLabels[t] + " Prize — #" + num).withStyle(ChatFormatting.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Prize: " + EconomyUtils.compact(LotteryManager.PRIZES[t]) + " THB")
                .withStyle(ChatFormatting.GREEN));
            if (!hasPrevious) {
                lore.add(Component.literal("No results yet").withStyle(ChatFormatting.GRAY));
            } else if (claimOpen) {
                lore.add(Component.literal("Round #" + lottery.getPreviousRoundId() + " — Claimable now!")
                    .withStyle(ChatFormatting.AQUA));
            } else {
                lore.add(Component.literal("Round #" + lottery.getPreviousRoundId() + " — Previous Round")
                    .withStyle(ChatFormatting.GRAY));
            }
            book.set(DataComponents.LORE, new ItemLore(lore));
            ShopGui.markDisplay(book);
            inv.setItem(RESULT_SLOTS[t], book);
        }

        ItemStack closeBtn = new ItemStack(Items.BARRIER);
        closeBtn.set(DataComponents.CUSTOM_NAME,
            Component.literal("Close").withStyle(ChatFormatting.RED));
        inv.setItem(SLOT_CLOSE, closeBtn);
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
        if (slotId == SLOT_CLOSE) {
            sp.closeContainer();
            LotteryGui.open(sp);
        }
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
