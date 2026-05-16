package com.andromeda.economy;

import com.andromeda.economy.command.*;
import com.andromeda.economy.data.*;
import com.andromeda.economy.hud.ScoreboardHud;
import com.andromeda.economy.network.PriceMapPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class AndromedaEconomy implements ModInitializer {
    public static final String MOD_ID = "andromeda_economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DatabaseManager db;
    public static PriceManager prices;
    public static MobRewardManager mobRewards;
    public static ScoreboardHud hud;
    public static RegistryAccess registryAccess;

    public static final Map<UUID, Consumer<String>> PENDING_CHAT = new HashMap<>();

    /**
     * Players who have the client mod installed.
     * For these players we skip lore modification (keeping items clean for stacking)
     * and send prices via packet instead, letting the tooltip Mixin display them.
     */
    public static final Set<UUID> CLIENT_MOD_PLAYERS = new HashSet<>();

    @Override
    public void onInitialize() {
        db         = new DatabaseManager();
        prices     = new PriceManager();
        mobRewards = new MobRewardManager();
        hud        = new ScoreboardHud();

        // Register the S2C price-map payload so the server can send it to clients
        PayloadTypeRegistry.clientboundPlay().register(PriceMapPayload.TYPE, PriceMapPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ShopCommand.register(dispatcher);
            SellCommand.register(dispatcher);
            PayCommand.register(dispatcher);
            BalanceCommand.register(dispatcher);
            NightVisionCommand.register(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            registryAccess = server.registryAccess();
            prices.addEnchantedBooks(server);
            prices.addPotions(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            db.ensurePlayer(player.getStringUUID(), player.getGameProfile().name());
            hud.update(player);

            // Check if this client also has the mod installed.
            // When AndromedaEconomyClient registers the PriceMapPayload receiver,
            // Fabric announces that channel to the server during the join handshake —
            // so canSend() is reliable here.
            if (ServerPlayNetworking.canSend(player, PriceMapPayload.TYPE)) {
                CLIENT_MOD_PLAYERS.add(player.getUUID());
                sendPriceMap(player);     // push price data to client
                stripPriceLore(player);   // remove any existing NBT lore so items stack cleanly
                LOGGER.debug("Client mod detected for {}", player.getGameProfile().name());
            } else {
                updateInventoryPrices(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUUID();
            PENDING_CHAT.remove(uuid);
            CLIENT_MOD_PLAYERS.remove(uuid);
            hud.remove(handler.player);
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Consumer<String> handler = PENDING_CHAT.remove(sender.getUUID());
            if (handler != null) {
                String text = message.decoratedContent().getString();
                sender.level().getServer().execute(() -> handler.accept(text));
                return false;
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity entity, DamageSource src) -> {
            if (entity instanceof ServerPlayer) return;
            if (src.getEntity() instanceof ServerPlayer killer) {
                MobRewardManager.handleKill(killer, entity);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            if (tick % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    hud.updatePingOnly(player);
                }
            }
            // Only update lore for players WITHOUT the client mod
            if (tick % 100 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!CLIENT_MOD_PLAYERS.contains(player.getUUID())) {
                        updateInventoryPrices(player);
                    }
                }
            }
        });

        LOGGER.info("Andromeda Economy initialised.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hybrid client-mod helpers

    /** Sends the complete item→price map to a player who has the client mod. */
    public static void sendPriceMap(ServerPlayer player) {
        Map<String, Double> data = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            double buy = prices.getBuyPrice(id.toString());
            if (buy > 0) data.put(id.toString(), buy);
        }
        ServerPlayNetworking.send(player, new PriceMapPayload(data));
    }

    /**
     * Removes price lore we previously added so the client sees clean items.
     * We identify our lore by checking for "THB" in the first lore line
     * (all our lore ends with "THB x1").
     */
    private static void stripPriceLore(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) continue;
            if (lore.lines().get(0).getString().contains("THB")) {
                // Only strip if it's our single-line price lore
                if (lore.lines().size() == 1) stack.remove(DataComponents.LORE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sound helpers

    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
            Holder.direct(sound), SoundSource.MASTER,
            player.getX(), player.getY(), player.getZ(),
            volume, pitch, 0L
        ));
    }

    public static void playMoneySound(ServerPlayer player) {
        playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback lore update (used for players WITHOUT the client mod)

    public static void updateInventoryPrices(ServerPlayer player) {
        // Client-mod players use the tooltip Mixin instead — skip lore for them
        if (CLIENT_MOD_PLAYERS.contains(player.getUUID())) return;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) continue;

            double sellPrice = prices.getSellPrice(id.toString());
            if (sellPrice <= 0) continue;

            String priceStr = EconomyUtils.compact(sellPrice) + " THB ";
            String lorePlain = priceStr + "x1";
            ItemLore existing = stack.get(DataComponents.LORE);
            if (existing != null && !existing.lines().isEmpty()
                    && existing.lines().get(0).getString().equals(lorePlain)) continue;

            stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(priceStr).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("x1").withStyle(ChatFormatting.WHITE))
            )));
        }
    }
}
