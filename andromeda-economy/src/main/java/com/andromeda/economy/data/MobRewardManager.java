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
        DEFAULTS.put("minecraft:zombie",          100.0);
        DEFAULTS.put("minecraft:skeleton",        100.0);
        DEFAULTS.put("minecraft:spider",          100.0);
        DEFAULTS.put("minecraft:creeper",         500.0);
        DEFAULTS.put("minecraft:witch",           500.0);
        DEFAULTS.put("minecraft:enderman",        1_000.0);
        DEFAULTS.put("minecraft:blaze",           2_000.0);
        DEFAULTS.put("minecraft:wither_skeleton", 2_000.0);
        DEFAULTS.put("minecraft:elder_guardian",  10_000.0);
        DEFAULTS.put("minecraft:wither",          100_000.0);
        DEFAULTS.put("minecraft:ender_dragon",    500_000.0);
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
