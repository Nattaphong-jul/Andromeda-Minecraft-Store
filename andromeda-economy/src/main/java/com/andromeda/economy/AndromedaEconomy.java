package com.andromeda.economy;

import com.andromeda.economy.command.*;
import com.andromeda.economy.data.*;
import com.andromeda.economy.hud.ScoreboardHud;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public void onInitialize() {
        db         = new DatabaseManager();
        prices     = new PriceManager();
        mobRewards = new MobRewardManager();
        hud        = new ScoreboardHud();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ShopCommand.register(dispatcher);
            SellCommand.register(dispatcher);
            PayCommand.register(dispatcher);
            BalanceCommand.register(dispatcher);
            NightVisionCommand.register(dispatcher);
        });

        // Capture registry access and add dynamic shop entries once the server is fully started
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            registryAccess = server.registryAccess();
            prices.addEnchantedBooks(server);
            prices.addPotions(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            db.ensurePlayer(player.getStringUUID(), player.getGameProfile().name());
            hud.update(player);
            updateInventoryPrices(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING_CHAT.remove(handler.player.getUUID());
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
            // Refresh ping every 1 s (20 ticks). Note: Minecraft only measures latency
            // via keep-alive packets (~every 20 s), so the displayed number won't change
            // faster than that — but we update immediately when it does change.
            if (tick % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    hud.updatePingOnly(player);
                }
            }
            // Re-apply sell-price lore every 5 s to catch newly picked-up items
            if (tick % 100 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    updateInventoryPrices(player);
                }
            }
        });

        LOGGER.info("Andromeda Economy initialised.");
    }

    /** Plays a sound directly to one player (other players do not hear it). */
    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(
            Holder.direct(sound), SoundSource.MASTER,
            player.getX(), player.getY(), player.getZ(),
            volume, pitch, 0L
        ));
    }

    /** XP-orb ping — played when a player receives money (sell, kill, /pay). */
    public static void playMoneySound(ServerPlayer player) {
        playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    /** Sets sell-price lore on every priced item in the player's inventory. */
    public static void updateInventoryPrices(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) continue;
            double sellPrice = prices.getSellPrice(id.toString());
            if (sellPrice <= 0) continue;

            // Unit price so all stacks of the same item share identical lore → they merge.
            // Format: green price, then white "x1" to signal per-item value.
            String priceStr = EconomyUtils.compact(sellPrice) + " THB ";
            String lorePlain = priceStr + "x1"; // plain text used for change-detection
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
