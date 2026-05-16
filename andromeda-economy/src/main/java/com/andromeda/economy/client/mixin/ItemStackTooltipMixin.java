package com.andromeda.economy.client.mixin;

import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.client.PriceCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Client-side Mixin that injects a sell-price line into item tooltips.
 *
 * Logic:
 *  1. Skip if PriceCache not yet populated (not connected to Andromeda server).
 *  2. Skip if item carries the "ae_shop" CustomData flag — that means it is a
 *     temporary shop display item, which already shows the buy price in its lore.
 *  3. Remove any leftover price lore from old mod versions ("Sell: X THB", "X THB")
 *     so stale lore from chests/dropped items doesn't clutter the tooltip.
 *  4. Inject the current sell price at index 1 (right below the item name).
 *
 * Critically, this never writes to the ItemStack's data components — it only
 * modifies the temporary tooltip list — so item stacking is never affected.
 */
@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void andromeda$addPriceLine(
            Item.TooltipContext context,
            net.minecraft.world.entity.player.Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> cir) {

        if (!PriceCache.isLoaded()) return;

        ItemStack self = (ItemStack) (Object) this;

        // Shop display items carry this flag — they already show buy price, skip them
        CustomData customData = self.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("ae_shop")) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(self.getItem());
        if (id == null) return;

        double sellPrice = PriceCache.getSellPrice(id.toString());
        if (sellPrice <= 0) return;

        double total = sellPrice * self.getCount();
        String newPriceText = EconomyUtils.compact(total) + " THB";

        List<Component> lines = cir.getReturnValue();

        // Remove stale price lore injected by older versions of the mod so it
        // doesn't appear alongside the fresh price line we're about to add.
        lines.removeIf(c -> {
            String s = c.getString();
            return s.startsWith("Sell: ") && s.endsWith(" THB")   // old "Sell: 1,000.00 THB" format
                || s.endsWith(" THB") && !s.contains(" ")          // old plain "100K THB" lore (single token)
                || s.equals(newPriceText);                          // prevent duplicates
        });

        // Insert directly below the item name (index 0)
        int insertAt = Math.min(1, lines.size());
        lines.add(insertAt, Component.literal(newPriceText).withStyle(ChatFormatting.GREEN));
    }
}
