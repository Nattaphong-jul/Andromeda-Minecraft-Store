package com.andromeda.economy.client.mixin;

import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.client.PriceCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Client-side Mixin that appends a sell-price line to item tooltips.
 *
 * Because we inject into the tooltip renderer — NOT the item's NBT — items stay
 * completely clean: identical items always have the same data and stack perfectly.
 * The displayed price dynamically reflects the current stack size (count × unit price).
 */
@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void andromeda$addPriceLine(
            Item.TooltipContext context,
            Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> cir) {

        if (!PriceCache.isLoaded()) return;

        ItemStack self = (ItemStack) (Object) this;

        // Items with existing lore are shop display items (buy price already set by the server).
        // Only inject the sell price on clean inventory items that have no lore.
        if (self.has(net.minecraft.core.component.DataComponents.LORE)) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(self.getItem());
        if (id == null) return;

        double sellPrice = PriceCache.getSellPrice(id.toString());
        if (sellPrice <= 0) return;

        int count = self.getCount();
        double total = sellPrice * count;

        // Show total sell value — no count suffix, the price itself reflects the stack size
        List<Component> lines = cir.getReturnValue();
        lines.add(Component.literal(EconomyUtils.compact(total) + " THB").withStyle(ChatFormatting.GREEN));
    }
}
