package com.andromeda.economy.server.mixin;

import com.andromeda.economy.RankManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side Mixin — overrides the tab-list display name for each player.
 *
 * Reads the balance from {@link RankManager}'s in-memory cache (updated on
 * every balance change) and returns: "PlayerName [RankName]" in the appropriate
 * colour.  No client mod is needed — every vanilla client understands the
 * standard {@code UPDATE_DISPLAY_NAME} player-info packet.
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerTabListMixin {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void andromeda$tabListDisplayName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        double balance = RankManager.getCachedBalance(player.getUUID());
        String rank    = RankManager.rankName(balance);
        cir.setReturnValue(RankManager.tabDisplayName(player.getGameProfile().name(), rank));
    }
}
