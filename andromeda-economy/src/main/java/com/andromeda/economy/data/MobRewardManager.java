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

    private static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();
    static {
        // ── Common overworld ─────────────────────────────────────────────────
        DEFAULTS.put("minecraft:zombie",            1_000.0);
        DEFAULTS.put("minecraft:husk",              1_000.0);
        DEFAULTS.put("minecraft:drowned",           1_500.0);
        DEFAULTS.put("minecraft:skeleton",          1_000.0);
        DEFAULTS.put("minecraft:stray",             1_000.0);
        DEFAULTS.put("minecraft:spider",            1_000.0);
        DEFAULTS.put("minecraft:cave_spider",       2_000.0);
        DEFAULTS.put("minecraft:creeper",           2_500.0);
        DEFAULTS.put("minecraft:witch",             2_500.0);
        DEFAULTS.put("minecraft:slime",             1_000.0);
        DEFAULTS.put("minecraft:silverfish",          500.0);
        DEFAULTS.put("minecraft:endermite",           500.0);
        DEFAULTS.put("minecraft:phantom",           3_000.0);
        // ── Mid-tier ─────────────────────────────────────────────────────────
        DEFAULTS.put("minecraft:enderman",          5_000.0);
        DEFAULTS.put("minecraft:pillager",          2_000.0);
        DEFAULTS.put("minecraft:vindicator",        3_000.0);
        DEFAULTS.put("minecraft:evoker",           15_000.0);
        DEFAULTS.put("minecraft:ravager",          20_000.0);
        DEFAULTS.put("minecraft:guardian",          5_000.0);
        DEFAULTS.put("minecraft:shulker",          10_000.0);
        // ── Nether ───────────────────────────────────────────────────────────
        DEFAULTS.put("minecraft:blaze",            10_000.0);
        DEFAULTS.put("minecraft:wither_skeleton",  10_000.0);
        DEFAULTS.put("minecraft:ghast",             5_000.0);
        DEFAULTS.put("minecraft:magma_cube",        2_000.0);
        DEFAULTS.put("minecraft:piglin_brute",      8_000.0);
        DEFAULTS.put("minecraft:zombified_piglin",  3_000.0);
        DEFAULTS.put("minecraft:hoglin",            5_000.0);
        DEFAULTS.put("minecraft:zoglin",            7_000.0);
        // ── Bosses ───────────────────────────────────────────────────────────
        DEFAULTS.put("minecraft:elder_guardian",   50_000.0);
        DEFAULTS.put("minecraft:wither",          500_000.0);
        DEFAULTS.put("minecraft:ender_dragon",  2_000_000.0);
        // ── New / trial chambers ─────────────────────────────────────────────
        DEFAULTS.put("minecraft:breeze",           10_000.0);
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

        AndromedaEconomy.db.addBalance(player.getStringUUID(), reward);
        AndromedaEconomy.db.incrementKills(player.getStringUUID());

        player.sendSystemMessage(
            Component.literal("You earned ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(EconomyUtils.format(reward)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" THB for killing " + mobName).withStyle(ChatFormatting.WHITE))
        );

        AndromedaEconomy.playMoneySound(player);

        AndromedaEconomy.hud.update(player);
    }
}
