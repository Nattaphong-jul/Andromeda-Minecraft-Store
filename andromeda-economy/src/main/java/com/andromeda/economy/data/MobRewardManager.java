package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.io.*;
import java.util.*;

public class MobRewardManager {
    private static final File FILE = new File("config/andromeda-economy/mob_rewards.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Kill reward balancing rationale:
     *
     * Drop value context (sell at 85%):
     *   rotten flesh 43, arrow 85, bone 255, gunpowder 4.25K, string 425,
     *   spider eye 850, ender pearl 8.5K, blaze rod 42.5K, ghast tear 68K,
     *   phantom membrane 17K, shulker shell 170K, wither skull 425K, nether star 4.25M
     *
     * Formula goal: kill reward ≈ 10–30% of average expected drop sell value,
     * so killing is worthwhile but drops remain the primary income.
     * Bosses are an exception — reward scales with difficulty not just drops.
     */
    private static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();
    static {
        // ── Common overworld ─────────────────────────────────────────────────
        // avg drops ~50–500 THB sell value; reward = small bonus
        DEFAULTS.put("minecraft:zombie",              1_000.0);
        DEFAULTS.put("minecraft:husk",                1_000.0);
        DEFAULTS.put("minecraft:drowned",             2_000.0);  // copper + trident chance
        DEFAULTS.put("minecraft:skeleton",            1_000.0);
        DEFAULTS.put("minecraft:stray",               1_500.0);  // slowness arrows
        DEFAULTS.put("minecraft:bogged",              1_500.0);  // new skeleton variant
        DEFAULTS.put("minecraft:spider",              1_000.0);
        DEFAULTS.put("minecraft:cave_spider",         2_000.0);
        DEFAULTS.put("minecraft:creeper",             3_000.0);  // gunpowder 4.25K sell
        DEFAULTS.put("minecraft:witch",               3_000.0);  // potion drops ~3.4K sell
        DEFAULTS.put("minecraft:slime",               1_500.0);  // slimeball 1.7K sell
        DEFAULTS.put("minecraft:silverfish",            500.0);
        DEFAULTS.put("minecraft:endermite",             500.0);
        DEFAULTS.put("minecraft:phantom",             5_000.0);  // membrane 17K, hard to kill at night
        DEFAULTS.put("minecraft:zombie_villager",     1_000.0);
        DEFAULTS.put("minecraft:piglin",              1_000.0);

        // ── Mid-tier ─────────────────────────────────────────────────────────
        DEFAULTS.put("minecraft:enderman",            5_000.0);  // ender pearl 8.5K sell
        DEFAULTS.put("minecraft:pillager",            3_000.0);  // crossbow chance
        DEFAULTS.put("minecraft:vindicator",          5_000.0);  // harder melee, emerald drop
        DEFAULTS.put("minecraft:evoker",             25_000.0);  // totem 85K sell, always drops
        DEFAULTS.put("minecraft:vex",                 3_000.0);  // evoker minion, no drops
        DEFAULTS.put("minecraft:ravager",            30_000.0);  // very tanky, saddle 17K sell
        DEFAULTS.put("minecraft:guardian",            5_000.0);  // prismarine 4.25K sell
        DEFAULTS.put("minecraft:shulker",            20_000.0);  // shell 170K sell at ~50% chance

        // ── Nether ───────────────────────────────────────────────────────────
        DEFAULTS.put("minecraft:blaze",              10_000.0);  // rod 42.5K sell, ~50% drop
        DEFAULTS.put("minecraft:wither_skeleton",    10_000.0);  // skull 425K sell, ~2.5% drop
        DEFAULTS.put("minecraft:ghast",              10_000.0);  // tear 68K sell, rare drop
        DEFAULTS.put("minecraft:magma_cube",          3_000.0);  // magma cream 8.5K sell
        DEFAULTS.put("minecraft:piglin_brute",       10_000.0);  // dangerous, no unique drops
        DEFAULTS.put("minecraft:zombified_piglin",    3_000.0);  // gold nugget 5.7K sell
        DEFAULTS.put("minecraft:hoglin",              5_000.0);  // leather + porkchop
        DEFAULTS.put("minecraft:zoglin",              8_000.0);  // no drops, dangerous

        // ── Bosses ───────────────────────────────────────────────────────────
        // Rewards intentionally high — one-time or very rare encounters
        DEFAULTS.put("minecraft:elder_guardian",     80_000.0);  // sponge 42.5K sell, temple effort
        DEFAULTS.put("minecraft:wither",            500_000.0);  // nether star 4.25M sell
        DEFAULTS.put("minecraft:ender_dragon",    2_000_000.0);  // dragon egg 42.5M sell (first kill)

        // ── New / trial chambers ─────────────────────────────────────────────
        DEFAULTS.put("minecraft:breeze",             15_000.0);  // breeze rod 2.55K sell, trial chamber
        DEFAULTS.put("minecraft:creaking",           50_000.0);  // pale garden, hard to locate+kill
        DEFAULTS.put("minecraft:warden",            800_000.0);  // hardest overworld mob, no valuable drops
        DEFAULTS.put("minecraft:illusioner",        100_000.0);  // extremely rare spawn

        // ── Penalties (negative = deduct from balance) ────────────────────────
        DEFAULTS.put("minecraft:allay",             -10_000.0); // peaceful helper mob
        DEFAULTS.put("minecraft:bee",                -1_000.0); // pollinator, important for ecosystem
        DEFAULTS.put("minecraft:trader_llama",            0.0); // no reward, no penalty
    }

    private final Map<String, Double> rewards = new HashMap<>();

    public MobRewardManager() {
        if (!FILE.exists()) generate(); else loadFile();
    }

    private void generate() {
        FILE.getParentFile().mkdirs();
        JsonObject root = new JsonObject();
        DEFAULTS.forEach(root::addProperty);
        try (Writer w = new FileWriter(FILE)) { GSON.toJson(root, w); }
        catch (IOException e) { AndromedaEconomy.LOGGER.error("Failed to write mob_rewards.json", e); }
        rewards.putAll(DEFAULTS);
    }

    private void loadFile() {
        try (Reader r = new FileReader(FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) { generate(); return; }
            for (Map.Entry<String, JsonElement> e : root.entrySet())
                rewards.put(e.getKey(), e.getValue().getAsDouble());
        } catch (IOException e) { AndromedaEconomy.LOGGER.error("Failed to read mob_rewards.json", e); }
    }

    public static void handleKill(ServerPlayer player, LivingEntity mob) {
        Identifier mobId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        String mobIdStr = mobId != null ? mobId.toString() : "";
        double reward = AndromedaEconomy.mobRewards.rewards.getOrDefault(mobIdStr, 50.0);
        String mobName = mob.getType().getDescription().getString();

        if (reward == 0) return; // e.g. trader llama — no effect, no message

        if (reward > 0) {
            // ── Normal reward ─────────────────────────────────────────────────
            double earned = AndromedaEconomy.company.distribute(player.getStringUUID(), reward, player.level().getServer());
            AndromedaEconomy.db.addBalance(player.getStringUUID(), earned);
            AndromedaEconomy.db.incrementKills(player.getStringUUID());
            player.sendSystemMessage(
                Component.literal("You earned ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(EconomyUtils.format(reward)).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" THB for killing " + mobName).withStyle(ChatFormatting.WHITE))
            );
            AndromedaEconomy.playMoneySound(player);
        } else {
            // ── Penalty (reward is negative) ──────────────────────────────────
            double penalty = -reward; // positive amount for display
            PlayerData data = AndromedaEconomy.db.getPlayer(player.getStringUUID());
            if (data != null) {
                double newBalance = Math.max(0, data.balance - penalty);
                AndromedaEconomy.db.setBalance(player.getStringUUID(), newBalance);
            }
            // Penalty kills do NOT count toward the kill counter
            player.sendSystemMessage(
                Component.literal("☠ You lost ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(EconomyUtils.format(penalty)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" THB for killing " + mobName + "!").withStyle(ChatFormatting.RED))
            );
            AndromedaEconomy.playSound(player, net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }

        AndromedaEconomy.hud.update(player);
    }
}
