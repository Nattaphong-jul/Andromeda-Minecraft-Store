package com.andromeda.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central rank utility shared by the HUD, overhead team system, and tab list Mixin.
 *
 * Rank tiers (by balance in THB):
 *   Unemployed  < 100K        – red
 *   Salary Man  100K–10M      – white
 *   Anutin      10M–100M      – blue
 *   CEO         100M–1B       – green
 *   MrBeast     1B–10B        – cyan
 *   CK          10B–500B      – light purple
 *   Elon Musk   > 500B        – gold
 */
public final class RankManager {

    private RankManager() {}

    // ── Rank data ─────────────────────────────────────────────────────────────

    public static final Map<String, ChatFormatting> RANKS = new LinkedHashMap<>();
    static {
        RANKS.put("Unemployed", ChatFormatting.RED);
        RANKS.put("Salary Man", ChatFormatting.WHITE);
        RANKS.put("Anutin",     ChatFormatting.BLUE);
        RANKS.put("CEO",        ChatFormatting.GREEN);
        RANKS.put("MrBeast",    ChatFormatting.AQUA);
        RANKS.put("CK",         ChatFormatting.LIGHT_PURPLE);
        RANKS.put("Elon Musk",  ChatFormatting.GOLD);
    }

    public static String rankName(double balance) {
        if (balance >= 500_000_000_000.0) return "Elon Musk";
        if (balance >= 10_000_000_000.0)  return "CK";
        if (balance >= 1_000_000_000.0)   return "MrBeast";
        if (balance >= 100_000_000.0)     return "CEO";
        if (balance >= 10_000_000.0)      return "Anutin";
        if (balance >= 100_000.0)         return "Salary Man";
        return "Unemployed";
    }

    public static ChatFormatting rankColor(String rank) {
        return RANKS.getOrDefault(rank, ChatFormatting.WHITE);
    }

    public static String rankAbbrev(String rank) {
        return switch (rank) {
            case "Unemployed" -> "UEP";
            case "Salary Man" -> "SM";
            case "Anutin"     -> "AN";
            case "CEO"        -> "CEO";
            case "MrBeast"    -> "MrB";
            case "CK"         -> "CK";
            case "Elon Musk"  -> "EM";
            default           -> "?";
        };
    }

    /** Scoreboard team name (must be ≤ 16 chars and unique per rank). */
    public static String teamName(String rank) {
        return "ae_" + rankAbbrev(rank).toLowerCase();
    }

    // ── Component builders ────────────────────────────────────────────────────

    /** Prefix shown BEFORE the player name in the overhead: "CK " in rank colour. */
    public static Component overheadPrefix(String rank) {
        return Component.literal(rankAbbrev(rank) + " ").withStyle(rankColor(rank));
    }

    /**
     * Display name shown in the tab list: "Player1 [Anutin]"
     * — white player name, coloured bracket+rank.
     */
    public static Component tabDisplayName(String playerName, String rank) {
        return Component.literal(playerName).withStyle(ChatFormatting.WHITE)
            .append(Component.literal(" [" + rank + "]").withStyle(rankColor(rank)));
    }

    // ── Per-player total wealth cache (balance + ender chest assets) ──────────

    private static final Map<UUID, Double> WEALTH_CACHE = new ConcurrentHashMap<>();

    /** Call with balance + enderChestAssets so tab-list Mixin uses the correct rank. */
    public static void cacheWealth(UUID uuid, double totalWealth) {
        WEALTH_CACHE.put(uuid, totalWealth);
    }

    public static double getCachedWealth(UUID uuid) {
        return WEALTH_CACHE.getOrDefault(uuid, 0.0);
    }

    public static void removeCache(UUID uuid) {
        WEALTH_CACHE.remove(uuid);
    }
}
