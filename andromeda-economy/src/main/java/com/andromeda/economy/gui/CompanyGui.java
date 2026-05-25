package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.CompanyData;
import com.andromeda.economy.data.PlayerData;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Main company list — 54-slot chest.
 *
 * Slots 0–44 : company skulls (owner's head), sorted by revenue
 * Slot  45   : Close
 * Slots 46–52: black glass
 * Slot  53   : My Company (if player is in one)
 */
public class CompanyGui extends ChestMenu {

    private static final int MAX_LIST  = 45;
    private static final int SLOT_CLOSE = 45;
    private static final int SLOT_MINE  = 53;

    private final SimpleContainer inv;
    private final List<CompanyData> companies;
    private final String playerUUID;

    private CompanyGui(int syncId, Inventory playerInv, SimpleContainer inv,
                       List<CompanyData> companies, String playerUUID, MinecraftServer server) {
        super(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6);
        this.inv = inv;
        this.companies = companies;
        this.playerUUID = playerUUID;
        populate(server);
    }

    public static void open(ServerPlayer player) {
        List<CompanyData> companies = AndromedaEconomy.company.allSortedByRevenue();
        SimpleContainer inv = new SimpleContainer(54);
        MinecraftServer server = player.level().getServer();
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) ->
                new CompanyGui(syncId, playerInv, inv, companies, player.getStringUUID(), server),
            Component.literal("Companies")
        ));
    }

    private void populate(MinecraftServer server) {
        ItemStack glass = blackGlass();
        for (int i = 0; i < 54; i++) inv.setItem(i, glass.copy());

        int limit = Math.min(companies.size(), MAX_LIST);
        for (int i = 0; i < limit; i++) {
            CompanyData c = companies.get(i);
            ItemStack skull = playerSkull(c.ownerUUID, server);
            skull.set(DataComponents.CUSTOM_NAME,
                Component.literal(c.name).withStyle(ChatFormatting.GOLD));
            skull.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Revenue: " + EconomyUtils.compact(c.revenue) + " THB")
                    .withStyle(ChatFormatting.GREEN),
                Component.literal("Members: " + (c.memberShares.size() + 1))
                    .withStyle(ChatFormatting.GRAY),
                Component.literal("Click to apply").withStyle(ChatFormatting.YELLOW)
            )));
            ShopGui.markDisplay(skull);
            inv.setItem(i, skull);
        }

        ItemStack closeBtn = new ItemStack(Items.BARRIER);
        closeBtn.set(DataComponents.CUSTOM_NAME, Component.literal("Close").withStyle(ChatFormatting.RED));
        ShopGui.markDisplay(closeBtn);
        inv.setItem(SLOT_CLOSE, closeBtn);

        CompanyData mine = AndromedaEconomy.company.getByMember(playerUUID);
        if (mine != null) {
            ItemStack myBtn = new ItemStack(Items.BOOK);
            myBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("My Company: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(mine.name).withStyle(ChatFormatting.GOLD)));
            ShopGui.markDisplay(myBtn);
            inv.setItem(SLOT_MINE, myBtn);
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;
        if (slotId == SLOT_CLOSE) { sp.closeContainer(); return; }
        if (slotId == SLOT_MINE)  { sp.closeContainer(); CompanyMemberGui.open(sp); return; }
        if (slotId < 0 || slotId >= MAX_LIST || slotId >= companies.size()) return;

        CompanyData clicked = companies.get(slotId);
        CompanyData mine    = AndromedaEconomy.company.getByMember(sp.getStringUUID());

        if (mine != null && mine.name.equalsIgnoreCase(clicked.name)) {
            sp.closeContainer();
            CompanyMemberGui.open(sp);
        } else if (mine != null) {
            sp.sendSystemMessage(Component.literal(
                "Leave your current company before applying to another."
            ).withStyle(ChatFormatting.RED));
        } else {
            sp.closeContainer();
            CompanyApplyGui.open(sp, clicked.name);
        }
    }

    // ── Shared skull helper (also used by CompanyMemberGui) ───────────────────

    static ItemStack playerSkull(String uuid, MinecraftServer server) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        try {
            ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(uuid));
            GameProfile profile;
            if (online != null) {
                profile = online.getGameProfile();
            } else {
                PlayerData data = AndromedaEconomy.db.getPlayer(uuid);
                String name = data != null ? data.username : "Unknown";
                profile = new GameProfile(UUID.fromString(uuid), name);
            }
            skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        } catch (Exception ignored) {}
        return skull;
    }

    static ItemStack blackGlass() {
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
