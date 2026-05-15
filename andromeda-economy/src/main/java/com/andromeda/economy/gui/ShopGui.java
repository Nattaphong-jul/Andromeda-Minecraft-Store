package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.PriceManager;
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
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Map;

/**
 * 54-slot paginated shop GUI.
 *
 * Slots 0–44 : item listings (up to 45 per page)
 * Slot 45    : ◀ Previous  (leftmost of nav bar)
 * Slots 46–52: glass padding
 * Slot 53    : ▶ Next      (rightmost — index 8 of bottom row)
 *
 * Search removed — use /shop [term] instead.
 */
public class ShopGui extends ChestMenu {

    private static final int SLOT_PREV      = 45;
    private static final int SLOT_NEXT      = 53;
    private static final int ITEMS_PER_PAGE = 45;

    private final SimpleContainer inv;
    private final List<Map.Entry<String, PriceManager.PriceEntry>> items;
    private int page;
    private long lastNavMs = 0;

    private ShopGui(int syncId, Inventory playerInv, SimpleContainer inv,
                    ServerPlayer player,
                    List<Map.Entry<String, PriceManager.PriceEntry>> items,
                    int page) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv   = inv;
        this.items = items;
        this.page   = page;
        populatePage();
    }

    public static void open(ServerPlayer player, String search) {
        List<Map.Entry<String, PriceManager.PriceEntry>> items =
            search.isBlank() ? AndromedaEconomy.prices.getAll()
                             : AndromedaEconomy.prices.search(search);
        openPage(player, items, 0);
    }

    static void openPage(ServerPlayer player,
                         List<Map.Entry<String, PriceManager.PriceEntry>> items,
                         int page) {
        SimpleContainer inv = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new ShopGui(syncId, playerInv, inv, player, items, page),
            Component.literal("Andromeda Shop")
        ));
    }

    private void populatePage() {
        for (int i = 0; i < 45; i++) inv.setItem(i, ItemStack.EMPTY);

        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (start + i) < items.size(); i++) {
            Map.Entry<String, PriceManager.PriceEntry> e = items.get(start + i);
            inv.setItem(i, makeShopStack(e.getKey(), e.getValue()));
        }

        inv.setItem(SLOT_PREV, makeArrow(page > 0, "◀ Previous"));
        inv.setItem(SLOT_NEXT, makeArrow(page < maxPage(), "▶ Next"));

        ItemStack pad = glass();
        for (int s = 46; s <= 52; s++) inv.setItem(s, pad.copy());
    }

    private ItemStack makeShopStack(String shopId, PriceManager.PriceEntry entry) {
        ItemStack stack = createStack(shopId, 1);
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal(displayName(shopId)).withStyle(ChatFormatting.WHITE));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal(EconomyUtils.compact(entry.price) + " THB").withStyle(ChatFormatting.GREEN)
        )));
        return stack;
    }

    static ItemStack createStack(String shopId, int count) {
        if (shopId.startsWith("enchanted_book:") && AndromedaEconomy.registryAccess != null) {
            // format: "enchanted_book:<namespace>:<path>:<level>"
            int lastColon = shopId.lastIndexOf(':');
            int level = Integer.parseInt(shopId.substring(lastColon + 1));
            String enchIdStr = shopId.substring("enchanted_book:".length(), lastColon);
            Registry<Enchantment> reg = AndromedaEconomy.registryAccess
                .lookup(Registries.ENCHANTMENT).orElseThrow();
            Enchantment ench = reg.getValue(Identifier.parse(enchIdStr));
            if (ench != null) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK, count);
                ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                m.set(reg.wrapAsHolder(ench), level);
                book.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
                return book;
            }
        }
        if (AndromedaEconomy.registryAccess != null) {
            net.minecraft.world.item.Item potionItem = potionItemFor(shopId);
            if (potionItem != null) {
                String potionIdStr = shopId.substring(shopId.indexOf(':') + 1);
                Registry<net.minecraft.world.item.alchemy.Potion> reg = AndromedaEconomy.registryAccess
                    .lookup(Registries.POTION).orElseThrow();
                net.minecraft.world.item.alchemy.Potion potion = reg.getValue(Identifier.parse(potionIdStr));
                if (potion != null) {
                    ItemStack pot = new ItemStack(potionItem, count);
                    pot.set(DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(reg.wrapAsHolder(potion)));
                    return pot;
                }
            }
        }
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(shopId)).orElseThrow().value();
        return new ItemStack(item, count);
    }

    static String displayName(String shopId) {
        if (shopId.startsWith("enchanted_book:") && AndromedaEconomy.registryAccess != null) {
            int lastColon = shopId.lastIndexOf(':');
            int level = Integer.parseInt(shopId.substring(lastColon + 1));
            String enchIdStr = shopId.substring("enchanted_book:".length(), lastColon);
            Registry<Enchantment> reg = AndromedaEconomy.registryAccess
                .lookup(Registries.ENCHANTMENT).orElse(null);
            if (reg != null) {
                Enchantment ench = reg.getValue(Identifier.parse(enchIdStr));
                if (ench != null) return ench.description().getString() + " " + EconomyUtils.toRoman(level);
            }
        }
        net.minecraft.world.item.Item potionItem = potionItemFor(shopId);
        if (potionItem != null && AndromedaEconomy.registryAccess != null) {
            String potionIdStr = shopId.substring(shopId.indexOf(':') + 1);
            Registry<net.minecraft.world.item.alchemy.Potion> reg = AndromedaEconomy.registryAccess
                .lookup(Registries.POTION).orElse(null);
            if (reg != null) {
                net.minecraft.world.item.alchemy.Potion potion = reg.getValue(Identifier.parse(potionIdStr));
                if (potion != null) {
                    ItemStack dummy = new ItemStack(potionItem);
                    dummy.set(DataComponents.POTION_CONTENTS,
                        new net.minecraft.world.item.alchemy.PotionContents(reg.wrapAsHolder(potion)));
                    return dummy.getHoverName().getString();
                }
            }
        }
        return EconomyUtils.toDisplayName(shopId);
    }

    /** Returns the matching Item for potion shop prefixes, or null if not a potion. */
    private static net.minecraft.world.item.Item potionItemFor(String shopId) {
        if (shopId.startsWith("lingering_potion:")) return Items.LINGERING_POTION;
        if (shopId.startsWith("splash_potion:"))    return Items.SPLASH_POTION;
        if (shopId.startsWith("potion:"))           return Items.POTION;
        return null;
    }

    private ItemStack makeArrow(boolean active, String label) {
        ItemStack stack = new ItemStack(active ? Items.ARROW : Items.GRAY_DYE);
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal(active
                ? label + "  (page " + (page + 1) + "/" + (maxPage() + 1) + ")"
                : " "
            ).withStyle(ChatFormatting.WHITE));
        return stack;
    }

    private static ItemStack glass() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        return s;
    }

    private int maxPage() {
        return items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;

        if (slotId == SLOT_PREV || slotId == SLOT_NEXT) {
            long now = System.currentTimeMillis();
            if (now - lastNavMs < 400) return; // debounce double-click
            lastNavMs = now;
            boolean turned = false;
            if (slotId == SLOT_PREV && page > 0)        { page--; populatePage(); broadcastChanges(); turned = true; }
            if (slotId == SLOT_NEXT && page < maxPage()) { page++; populatePage(); broadcastChanges(); turned = true; }
            if (turned) AndromedaEconomy.playSound(sp, net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
            return;
        }
        if (slotId >= 0 && slotId < ITEMS_PER_PAGE) {
            int idx = page * ITEMS_PER_PAGE + slotId;
            if (idx >= items.size()) return;
            Map.Entry<String, PriceManager.PriceEntry> entry = items.get(idx);
            sp.closeContainer();
            QuantityGui.open(sp, entry, items, page);
            return;
        }
        // Block nav bar and all other non-item clicks
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
