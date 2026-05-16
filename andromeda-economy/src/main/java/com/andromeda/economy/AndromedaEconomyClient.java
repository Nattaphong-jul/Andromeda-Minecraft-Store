package com.andromeda.economy;

import com.andromeda.economy.client.PriceCache;
import com.andromeda.economy.network.PriceMapPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

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
        // Register the S2C payload type so the channel is announced to the server on join.
        // This is what ServerPlayNetworking.canSend() checks — if this registration exists
        // on the client, the server knows the client has the mod and can send prices.
        PayloadTypeRegistry.clientboundPlay().register(PriceMapPayload.TYPE, PriceMapPayload.CODEC);

        // Handle incoming price maps from the server
        ClientPlayNetworking.registerGlobalReceiver(PriceMapPayload.TYPE, (payload, context) ->
            PriceCache.update(payload.prices()));

        // Clear cache when disconnecting so stale data isn't used on the next server
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PriceCache.clear());
    }
}
