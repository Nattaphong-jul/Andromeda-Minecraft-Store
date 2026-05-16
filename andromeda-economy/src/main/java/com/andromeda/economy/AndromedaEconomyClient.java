package com.andromeda.economy;

import com.andromeda.economy.client.PriceCache;
import com.andromeda.economy.network.PriceMapPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side entrypoint — only runs when the mod is installed on the client.
 *
 * What it does:
 *  • Registers a receiver for the PriceMapPayload the server sends on join.
 *  • The PriceCache is populated with item → buy-price data.
 *  • ItemStackTooltipMixin then reads that cache to render prices dynamically,
 *    without ever touching item NBT → items stack perfectly, price reflects stack count.
 */
@Environment(EnvType.CLIENT)
public class AndromedaEconomyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // NOTE: PayloadTypeRegistry.clientboundPlay().register() is intentionally called
        // only in AndromedaEconomy.onInitialize() (the common/main entrypoint).
        // That entrypoint runs on both client and server, so the codec is registered once.
        // Registering it here too would double-register and crash on client launch.

        // Handle incoming price maps from the server
        ClientPlayNetworking.registerGlobalReceiver(PriceMapPayload.TYPE, (payload, context) ->
            PriceCache.update(payload.prices()));

        // Clear cache on disconnect so stale prices aren't shown on the next server
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PriceCache.clear());
    }
}
