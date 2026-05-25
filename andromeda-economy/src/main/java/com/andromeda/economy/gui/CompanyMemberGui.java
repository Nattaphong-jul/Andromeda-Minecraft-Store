package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.CompanyData;
import com.andromeda.economy.data.CompanyManager;
import com.andromeda.economy.data.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import java.util.Map;

/**
 * My Company page — 54-slot chest.
 *
 * Slots 0+   : owner skull then member skulls, each with name + share %
 * Slot  45   : Back (returns to company list)
 * Slots 46–52: black glass
 * Slot  53   : Leave (non-owners only)
 */
public class CompanyMemberGui extends ChestMenu {

    private static final int SLOT_BACK  = 45;
    private static final int SLOT_LEAVE = 53;

    private final SimpleContainer inv;
    private final String playerUUID;

    private CompanyMemberGui(int syncId, Inventory playerInv, SimpleContainer inv,
                              String playerUUID, MinecraftServer server, CompanyData company) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv = inv;
        this.playerUUID = playerUUID;
        populate(server, company);
    }

    public static void open(ServerPlayer player) {
        CompanyData company = AndromedaEconomy.company.getByMember(player.getStringUUID());
        if (company == null) {
            player.sendSystemMessage(Component.literal("You are not in a company.").withStyle(ChatFormatting.RED));
            return;
        }
        SimpleContainer inv = new SimpleContainer(54);
        MinecraftServer server = player.level().getServer();
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) ->
                new CompanyMemberGui(syncId, playerInv, inv, player.getStringUUID(), server, company),
            Component.literal(company.name)
        ));
    }

    private void populate(MinecraftServer server, CompanyData company) {
        ItemStack glass = CompanyGui.blackGlass();
        for (int i = 0; i < 54; i++) inv.setItem(i, glass.copy());

        // Build ordered list: owner first, then members
        List<Map.Entry<String, Integer>> slots = new ArrayList<>();
        // Owner entry uses -1 as a sentinel for "auto-calculated"
        slots.add(Map.entry(company.ownerUUID, -1));
        for (var e : company.memberShares.entrySet()) slots.add(e);

        int maxSlots = Math.min(slots.size(), SLOT_BACK);
        for (int i = 0; i < maxSlots; i++) {
            var entry = slots.get(i);
            String uuid = entry.getKey();
            boolean isOwner = uuid.equals(company.ownerUUID);
            int pct = isOwner ? company.ownerShare() : entry.getValue();

            PlayerData data = AndromedaEconomy.db.getPlayer(uuid);
            String name = data != null ? data.username : "Unknown";

            ItemStack skull = CompanyGui.playerSkull(uuid, server);
            skull.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(isOwner ? ChatFormatting.GOLD : ChatFormatting.WHITE));
            skull.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(isOwner ? "Owner" : "Member").withStyle(ChatFormatting.GRAY),
                Component.literal("Share: " + pct + "%").withStyle(ChatFormatting.YELLOW)
            )));
            ShopGui.markDisplay(skull);
            inv.setItem(i, skull);
        }

        // Company stats in slot after members (if space)
        if (slots.size() < SLOT_BACK) {
            ItemStack statsItem = new ItemStack(Items.GOLD_INGOT);
            statsItem.set(DataComponents.CUSTOM_NAME,
                Component.literal("Company Revenue").withStyle(ChatFormatting.GOLD));
            statsItem.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(EconomyUtils.compact(company.revenue) + " THB").withStyle(ChatFormatting.GREEN)
            )));
            ShopGui.markDisplay(statsItem);
            inv.setItem(slots.size(), statsItem);
        }

        // Back button
        ItemStack back = new ItemStack(Items.BARRIER);
        back.set(DataComponents.CUSTOM_NAME, Component.literal("Back").withStyle(ChatFormatting.RED));
        ShopGui.markDisplay(back);
        inv.setItem(SLOT_BACK, back);

        // Leave button (non-owners only)
        boolean isOwner = company.isOwner(playerUUID);
        if (!isOwner) {
            ItemStack leave = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            leave.set(DataComponents.CUSTOM_NAME, Component.literal("Leave Company").withStyle(ChatFormatting.RED));
            ShopGui.markDisplay(leave);
            inv.setItem(SLOT_LEAVE, leave);
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;
        if (slotId == SLOT_BACK)  { sp.closeContainer(); CompanyGui.open(sp); return; }
        if (slotId == SLOT_LEAVE) {
            CompanyData company = AndromedaEconomy.company.getByMember(sp.getStringUUID());
            if (company != null && !company.isOwner(sp.getStringUUID())) {
                sp.closeContainer();
                CompanyLeaveGui.open(sp, company.name);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
