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

/**
 * Leave-company confirmation — 27-slot chest (3 rows).
 *
 * Row 2:
 *   Slot 11 : Cancel (red glass)
 *   Slot 13 : Company skull
 *   Slot 15 : Confirm Leave (lime glass)
 */
public class CompanyLeaveGui extends ChestMenu {

    private static final int SLOT_CANCEL  = 11;
    private static final int SLOT_INFO    = 13;
    private static final int SLOT_CONFIRM = 15;

    private final SimpleContainer inv;
    private final String companyName;
    private long lastClickMs = 0;

    private CompanyLeaveGui(int syncId, Inventory playerInv, SimpleContainer inv,
                             String companyName, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, syncId, playerInv, inv, 3);
        this.inv = inv;
        this.companyName = companyName;
        populate(player);
    }

    public static void open(ServerPlayer player, String companyName) {
        SimpleContainer inv = new SimpleContainer(27);
        player.openMenu(new SimpleMenuProvider(
            (syncId, playerInv, p) -> new CompanyLeaveGui(syncId, playerInv, inv, companyName, player),
            Component.literal("Leave " + companyName + "?")
        ));
    }

    private void populate(ServerPlayer player) {
        ItemStack glass = CompanyGui.blackGlass();
        for (int i = 0; i < 27; i++) inv.setItem(i, glass.copy());

        ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel").withStyle(ChatFormatting.RED));
        ShopGui.markDisplay(cancel);
        inv.setItem(SLOT_CANCEL, cancel);

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
        confirm.set(DataComponents.CUSTOM_NAME,
            Component.literal("Confirm Leave").withStyle(ChatFormatting.GREEN));
        ShopGui.markDisplay(confirm);
        inv.setItem(SLOT_CONFIRM, confirm);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput type, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;
        if (slotId == SLOT_CANCEL) { sp.closeContainer(); CompanyMemberGui.open(sp); return; }
        if (slotId == SLOT_CONFIRM) {
            long now = System.currentTimeMillis();
            if (now - lastClickMs < 400) return;
            lastClickMs = now;
            attemptLeave(sp);
        }
    }

    private void attemptLeave(ServerPlayer sp) {
        CompanyData company = AndromedaEconomy.company.getByMember(sp.getStringUUID());
        if (company == null) {
            sp.sendSystemMessage(Component.literal("You are not in a company.").withStyle(ChatFormatting.RED));
            sp.closeContainer();
            return;
        }
        if (company.isOwner(sp.getStringUUID())) {
            sp.sendSystemMessage(Component.literal(
                "You own this company. Transfer ownership or disband it."
            ).withStyle(ChatFormatting.RED));
            sp.closeContainer();
            return;
        }
        AndromedaEconomy.company.removeMember(company, sp.getStringUUID());
        sp.sendSystemMessage(
            Component.literal("You left ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(company.name).withStyle(ChatFormatting.GOLD))
        );
        AndromedaEconomy.hud.update(sp);
        sp.closeContainer();
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
