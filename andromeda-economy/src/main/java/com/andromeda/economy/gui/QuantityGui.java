package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.PlayerData;
import com.andromeda.economy.data.PriceManager;
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
import java.util.Map;

/**
 * 27-slot quantity selector opened after clicking an item in the shop.
 *
 * Slot 4  (row 1 centre) : preview of the item + price per unit
 * Slots 9,11,13,15,17    : buy ×1, ×8, ×16, ×32, ×64 (actual item icons)
 * Slot 22 (row 3 centre) : Cancel (returns to shop page)
 * All other slots        : glass padding
 */
public class QuantityGui extends ChestMenu {

    private static final int[] QTY_SLOTS  = {9, 11, 13, 15, 17};
    private static final int[] QUANTITIES = {1,  8, 16, 32, 64};
    private static final int SLOT_PREVIEW = 4;
    private static final int SLOT_CANCEL  = 22;

    private final SimpleContainer inv;
    private final String itemId;
    private final PriceManager.PriceEntry entry;
    // Stored so Cancel can return the player to the exact shop page they came from
    private final List<Map.Entry<String, PriceManager.PriceEntry>> shopItems;
    private final int shopPage;
    private long lastBuyMs = 0; // debounce: prevents rapid double-clicks from buying twice

    private QuantityGui(int syncId, Inventory playerInv, SimpleContainer inv,
                        String itemId, PriceManager.PriceEntry entry,
                        List<Map.Entry<String, PriceManager.PriceEntry>> shopItems, int shopPage) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, inv, 3);
        this.inv       = inv;
        this.itemId    = itemId;
        this.entry     = entry;
        this.shopItems = shopItems;
        this.shopPage  = shopPage;
        populate();
    }

    public static void open(ServerPlayer player, Map.Entry<String, PriceManager.PriceEntry> e,
                            List<Map.Entry<String, PriceManager.PriceEntry>> shopItems, int shopPage) {
        SimpleContainer inv = new SimpleContainer(27);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) ->
                new QuantityGui(syncId, playerInv, inv, e.getKey(), e.getValue(), shopItems, shopPage),
            Component.literal("How many to buy?")
        ));
    }

    // -------------------------------------------------------------------------

    private void populate() {
        ItemStack pad = glass();
        for (int i = 0; i < 27; i++) inv.setItem(i, pad.copy());

        // Preview (centre of top row)
        ItemStack preview = ShopGui.createStack(itemId, 1);
        preview.set(DataComponents.CUSTOM_NAME,
            Component.literal(ShopGui.displayName(itemId)).withStyle(ChatFormatting.WHITE));
        preview.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Price: " + EconomyUtils.compact(entry.price) + " THB each")
                .withStyle(ChatFormatting.GREEN)
        )));
        ShopGui.markDisplay(preview); // prevent Mixin from injecting a second price
        inv.setItem(SLOT_PREVIEW, preview);

        // Quantity buttons — actual item icons with stack-count badge
        for (int i = 0; i < QTY_SLOTS.length; i++) {
            int qty = QUANTITIES[i];
            ItemStack btn = ShopGui.createStack(itemId, qty);
            btn.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("x" + qty).withStyle(ChatFormatting.WHITE),
                Component.literal("Price: " + EconomyUtils.compact(entry.price * qty) + " THB")
                    .withStyle(ChatFormatting.GREEN)
            )));
            ShopGui.markDisplay(btn); // prevent Mixin from injecting a second price
            inv.setItem(QTY_SLOTS[i], btn);
        }

        // Close button
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.set(DataComponents.CUSTOM_NAME,
            Component.literal("Close").withStyle(ChatFormatting.RED));
        inv.setItem(SLOT_CANCEL, cancel);
    }

    private static ItemStack glass() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        ShopGui.markDisplay(s);
        return s;
    }

    // -------------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            if (slotId == QTY_SLOTS[i]) {
                long now = System.currentTimeMillis();
                if (now - lastBuyMs < 200) return; // ignore rapid repeat clicks
                lastBuyMs = now;
                attemptPurchase(sp, QUANTITIES[i]);
                return;
            }
        }
        if (slotId == SLOT_CANCEL) {
            sp.closeContainer();
            ShopGui.openPage(sp, shopItems, shopPage); // return to the shop page
            return;
        }
    }

    private void attemptPurchase(ServerPlayer sp, int qty) {
        double cost = entry.price * qty;
        PlayerData data = AndromedaEconomy.db.getPlayer(sp.getStringUUID());

        // Insufficient balance — stay in GUI, play villager no
        if (data == null || data.balance < cost) {
            sp.sendSystemMessage(Component.literal("Insufficient balance.").withStyle(ChatFormatting.RED));
            AndromedaEconomy.playSound(sp, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        AndromedaEconomy.db.setBalance(sp.getStringUUID(), data.balance - cost);
        AndromedaEconomy.db.addSpend(sp.getStringUUID(), cost);

        // Give items in full stacks
        ItemStack template = ShopGui.createStack(itemId, 1);
        int maxStack = template.getMaxStackSize();
        int remaining = qty;
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack stack = ShopGui.createStack(itemId, give);
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
            remaining -= give;
        }

        sp.sendSystemMessage(Component.literal(
            "Purchased x" + qty + " " + ShopGui.displayName(itemId)
            + " for " + EconomyUtils.format(cost) + " THB"
        ).withStyle(ChatFormatting.GREEN));

        AndromedaEconomy.playMoneySound(sp);
        AndromedaEconomy.hud.update(sp);
        // Stay in the GUI so the player can buy more without reopening the shop
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
