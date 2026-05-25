package com.andromeda.economy.gui;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.data.CompanyData;
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

import java.util.UUID;

/**
 * Apply-to-company confirmation — 27-slot chest (3 rows).
 *
 * Row 2 (slots 9–17):
 *   Slot 11 : Cancel (red glass)
 *   Slot 13 : Company skull + name
 *   Slot 15 : Apply (lime glass)
 */
public class CompanyApplyGui extends ChestMenu {

    private static final int SLOT_CANCEL  = 11;
    private static final int SLOT_INFO    = 13;
    private static final int SLOT_CONFIRM = 15;

    private final SimpleContainer inv;
    private final String companyName;
    private long lastClickMs = 0;

    private CompanyApplyGui(int syncId, Inventory playerInv, SimpleContainer inv, String companyName,
                             ServerPlayer player) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, inv, 3);
        this.inv = inv;
        this.companyName = companyName;
        populate(player);
    }

    public static void open(ServerPlayer player, String companyName) {
        SimpleContainer inv = new SimpleContainer(27);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new CompanyApplyGui(syncId, playerInv, inv, companyName, player),
            Component.literal("Apply: " + companyName)
        ));
    }

    private void populate(ServerPlayer player) {
        ItemStack glass = CompanyGui.blackGlass();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass.copy());

        ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel").withStyle(ChatFormatting.RED));
        ShopGui.markDisplay(cancel);
        inv.setItem(SLOT_CANCEL, cancel);

        // Company skull
        CompanyData company = AndromedaEconomy.company.getByName(companyName);
        ItemStack info;
        if (company != null) {
            info = CompanyGui.playerSkull(company.ownerUUID, player.level().getServer());
            info.set(DataComponents.CUSTOM_NAME,
                Component.literal(company.name).withStyle(ChatFormatting.GOLD));
        } else {
            info = new ItemStack(Items.PLAYER_HEAD);
            info.set(DataComponents.CUSTOM_NAME, Component.literal(companyName).withStyle(ChatFormatting.GOLD));
        }
        ShopGui.markDisplay(info);
        inv.setItem(SLOT_INFO, info);

        ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
        confirm.set(DataComponents.CUSTOM_NAME, Component.literal("Apply").withStyle(ChatFormatting.GREEN));
        ShopGui.markDisplay(confirm);
        inv.setItem(SLOT_CONFIRM, confirm);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;
        if (slotId == SLOT_CANCEL) { sp.closeContainer(); CompanyGui.open(sp); return; }
        if (slotId == SLOT_CONFIRM) {
            long now = System.currentTimeMillis();
            if (now - lastClickMs < 400) return;
            lastClickMs = now;
            attemptApply(sp);
        }
    }

    private void attemptApply(ServerPlayer sp) {
        String uuid = sp.getStringUUID();
        if (AndromedaEconomy.company.getByMember(uuid) != null) {
            sp.sendSystemMessage(Component.literal("You are already in a company.").withStyle(ChatFormatting.RED));
            sp.closeContainer();
            return;
        }
        CompanyData company = AndromedaEconomy.company.getByName(companyName);
        if (company == null) {
            sp.sendSystemMessage(Component.literal("Company no longer exists.").withStyle(ChatFormatting.RED));
            sp.closeContainer();
            return;
        }
        if (company.pendingApplications.contains(uuid)) {
            sp.sendSystemMessage(Component.literal("Already applied to this company.").withStyle(ChatFormatting.YELLOW));
            sp.closeContainer();
            return;
        }
        company.pendingApplications.add(uuid);
        AndromedaEconomy.company.save();
        sp.sendSystemMessage(
            Component.literal("Application sent to ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
        );
        // Notify owner if online
        ServerPlayer owner = sp.level().getServer().getPlayerList()
            .getPlayer(UUID.fromString(company.ownerUUID));
        if (owner != null) {
            owner.sendSystemMessage(
                Component.literal("[" + company.name + "] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(sp.getName().getString()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" applied. Use /company accept " + sp.getName().getString())
                        .withStyle(ChatFormatting.YELLOW))
            );
        }
        sp.closeContainer();
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
