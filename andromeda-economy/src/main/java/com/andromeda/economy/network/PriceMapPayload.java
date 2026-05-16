package com.andromeda.economy.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client payload carrying the full item price map.
 * Sent once when the player joins (if they have the client mod installed).
 * The client caches it and uses it for tooltip rendering without touching item NBT.
 */
public record PriceMapPayload(Map<String, Double> prices) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PriceMapPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("andromeda_economy", "prices"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PriceMapPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.prices().size());
            payload.prices().forEach((id, price) -> {
                buf.writeUtf(id);
                buf.writeDouble(price);
            });
        },
        buf -> {
            int size = buf.readVarInt();
            Map<String, Double> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) map.put(buf.readUtf(), buf.readDouble());
            return new PriceMapPayload(map);
        }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
