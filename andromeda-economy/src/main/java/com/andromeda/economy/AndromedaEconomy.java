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
import com.andromeda.economy.gui.EnderChestGui;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ClientboundPlayChannelEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.EnderChestBlock;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
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
    public static BitcoinPriceService bitcoinPrice;

    public static final Map<UUID, Consumer<String>> PENDING_CHAT = new HashMap<>();

    /**
     * Players who have the client mod installed.
     * For these players we skip lore modification (keeping items clean for stacking)
     * and send prices via packet instead, letting the tooltip Mixin display them.
     */
    public static final Set<UUID> CLIENT_MOD_PLAYERS = new HashSet<>();

    /** Cached total sell-value of each player's ender chest (real + extended slots). */
    public static final Map<UUID, Double> enderChestAssets = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        db         = new DatabaseManager();
        prices     = new PriceManager();
        mobRewards = new MobRewardManager();
        hud        = new ScoreboardHud();
        bitcoinPrice = new BitcoinPriceService();
        prices.initBitcoin(BitcoinPriceService.FALLBACK_PRICE); // seed before API call

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
            setupRankTeams(server);
            // Fetch live Bitcoin price immediately on start
            bitcoinPrice.fetchAndApply(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            db.ensurePlayer(player.getStringUUID(), player.getGameProfile().name());
            hud.update(player);
            // canSend() is NOT reliable here — channel negotiation happens after JOIN fires.
            // We detect the client mod via ClientboundPlayChannelEvents.REGISTER below.
            // For now just run the server-side fallback; it will be undone if client mod present.
            updateInventoryPrices(player);
        });

        // Fires on the SERVER when the CLIENT sends its channel-registration packet.
        // This is the reliable moment to know the client has the mod installed.
        ClientboundPlayChannelEvents.REGISTER.register((handler, packetSender, server, channels) -> {
            if (!channels.contains(PriceMapPayload.TYPE.id())) return;
            ServerPlayer player = handler.player;
            CLIENT_MOD_PLAYERS.add(player.getUUID());
            // Undo the lore that updateInventoryPrices may have just added, then send prices
            stripPriceLore(player);
            sendPriceMap(player);
            LOGGER.debug("Client mod detected for {} — switched to tooltip mode", player.getGameProfile().name());
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
            if (entity instanceof ServerPlayer victim) {
                // PvP: killer steals 10 % of victim's balance
                if (src.getEntity() instanceof ServerPlayer killer && !killer.getUUID().equals(victim.getUUID())) {
                    handlePvpKill(killer, victim);
                }
                return;
            }
            // Mob kill reward
            if (src.getEntity() instanceof ServerPlayer killer) {
                MobRewardManager.handleKill(killer, entity);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            // Refresh Bitcoin price every 15 minutes (18 000 ticks)
            if (tick % 18_000 == 0 && tick > 0) bitcoinPrice.fetchAndApply(server);
            if (tick % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    hud.updatePingOnly(player);
                }
            }
            if (tick % 100 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (CLIENT_MOD_PLAYERS.contains(player.getUUID())) {
                        // Keep inventory clean for client-mod players so picked-up items
                        // with leftover price lore get cleared within 5 s
                        stripPriceLore(player);
                    } else {
                        updateInventoryPrices(player);
                    }
                }
            }
        });

        // Intercept ender chest block use and open our custom 54-slot GUI
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof EnderChestBlock) {
                EnderChestGui.open(sp);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("Andromeda Economy initialised.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hybrid client-mod helpers

    /**
     * Sends SELL prices (already discounted correctly) to a client-mod player.
     * Using sell prices means the client never needs to know about NO_DISCOUNT —
     * gold and Bitcoin arrive with their full price, regular items at 85%.
     */
    public static void sendPriceMap(ServerPlayer player) {
        Map<String, Double> data = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            double sell = prices.getSellPrice(id.toString());
            if (sell > 0) data.put(id.toString(), sell);
        }
        ServerPlayNetworking.send(player, new PriceMapPayload(data));
    }

    /** Pushes a fresh price map to every connected client-mod player. */
    public static void broadcastPriceMap(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (CLIENT_MOD_PLAYERS.contains(player.getUUID())) sendPriceMap(player);
        }
    }

    /**
     * Removes price lore we previously added so the client sees clean items.
     * We identify our lore by checking for "THB" in the first lore line
     * (all our lore ends with "THB x1").
     */
    public static void stripPriceLore(ServerPlayer player) {
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

    /**
     * Creates one scoreboard team per rank on the server.
     * Each team has a coloured prefix (abbreviation) that shows above the player's head.
     * Players are moved between teams by ScoreboardHud.update() whenever balance changes.
     */
    private static void setupRankTeams(MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        for (Map.Entry<String, net.minecraft.ChatFormatting> e : RankManager.RANKS.entrySet()) {
            String rank     = e.getKey();
            String teamName = RankManager.teamName(rank);
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) team = scoreboard.addPlayerTeam(teamName);
            team.setPlayerPrefix(RankManager.overheadPrefix(rank));
            team.setColor(net.minecraft.ChatFormatting.RESET);  // don't recolour the player name
            team.setNameTagVisibility(Team.Visibility.ALWAYS);
        }
        LOGGER.info("[Andromeda] Rank teams created.");
    }

    /** Steals 5 % of the victim's balance and gives it to the killer. Server-side only. */
    private static void handlePvpKill(ServerPlayer killer, ServerPlayer victim) {
        PlayerData victimData = db.getPlayer(victim.getStringUUID());
        if (victimData == null || victimData.balance < 1) return;

        double stolen = Math.floor(victimData.balance * 0.05);
        if (stolen < 1) return;

        db.setBalance(victim.getStringUUID(), victimData.balance - stolen);
        db.addBalance(killer.getStringUUID(), stolen);

        String amtStr = EconomyUtils.compact(stolen) + " THB";

        victim.sendSystemMessage(
            Component.literal("☠ ").withStyle(ChatFormatting.RED)
                .append(Component.literal(killer.getName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" stole ").withStyle(ChatFormatting.RED))
                .append(Component.literal(amtStr).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" from you!").withStyle(ChatFormatting.RED))
        );
        killer.sendSystemMessage(
            Component.literal("☠ You stole ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(amtStr).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" from " + victim.getName().getString() + "!").withStyle(ChatFormatting.GREEN))
        );

        playMoneySound(killer);
        hud.update(killer);
        hud.update(victim);
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

            String loreText = EconomyUtils.compact(sellPrice) + " THB";
            ItemLore existing = stack.get(DataComponents.LORE);
            if (existing != null && !existing.lines().isEmpty()
                    && existing.lines().get(0).getString().equals(loreText)) continue;

            stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(loreText).withStyle(ChatFormatting.GREEN)
            )));
        }
    }
}
