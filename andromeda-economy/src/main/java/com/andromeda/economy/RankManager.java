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
 *   Unemployed  < 100K         – white
 *   Salary Man  100K–10M       – white
 *   Anutin      10M–100M       – blue
 *   CEO         100M–1B        – dark green
 *   MrBeast     1B–10B         – aqua
 *   CK          10B–100B       – light purple
 *   Jensen Huang 100B–500B     – green
 *   Elon Musk   500B–1T        – gold
 *   FED         1T–2T          – red
 *   Cheater     ≥ 2T           – black
 */
public final class RankManager {

    private RankManager() {}

    // ── Rank data ─────────────────────────────────────────────────────────────

    public static final Map<String, ChatFormatting> RANKS = new LinkedHashMap<>();
    static {
        RANKS.put("Unemployed",   ChatFormatting.WHITE);
        RANKS.put("Salary Man",   ChatFormatting.WHITE);
        RANKS.put("Anutin",       ChatFormatting.BLUE);
        RANKS.put("CEO",          ChatFormatting.DARK_GREEN);
        RANKS.put("MrBeast",      ChatFormatting.AQUA);
        RANKS.put("CK",           ChatFormatting.LIGHT_PURPLE);
        RANKS.put("Jensen Huang", ChatFormatting.GREEN);
        RANKS.put("Elon Musk",    ChatFormatting.GOLD);
        RANKS.put("FED",          ChatFormatting.RED);
        RANKS.put("Cheater",      ChatFormatting.BLACK);
    }

    public static String rankName(double balance) {
        if (balance >= 2_000_000_000_000.0) return "Cheater";      // ≥ 2T
        if (balance >= 1_000_000_000_000.0) return "FED";          // 1T – 2T
        if (balance >= 500_000_000_000.0)   return "Elon Musk";    // 500B – 1T
        if (balance >= 100_000_000_000.0)   return "Jensen Huang"; // 100B – 500B
        if (balance >= 10_000_000_000.0)    return "CK";           // 10B  – 100B
        if (balance >= 1_000_000_000.0)     return "MrBeast";      // 1B   – 10B
        if (balance >= 100_000_000.0)       return "CEO";          // 100M – 1B
        if (balance >= 10_000_000.0)        return "Anutin";       // 10M  – 100M
        if (balance >= 100_000.0)           return "Salary Man";   // 100K – 10M
        return "Unemployed";                                        // < 100K
    }

    public static ChatFormatting rankColor(String rank) {
        return RANKS.getOrDefault(rank, ChatFormatting.WHITE);
    }

    public static String rankAbbrev(String rank) {
        return switch (rank) {
            case "Unemployed"   -> "UEP";
            case "Salary Man"   -> "SM";
            case "Anutin"       -> "AN";
            case "CEO"          -> "CEO";
            case "MrBeast"      -> "MrB";
            case "CK"           -> "CK";
            case "Jensen Huang" -> "JH";
            case "Elon Musk"    -> "EM";
            case "FED"          -> "FED";
            case "Cheater"      -> "CHT";
            default             -> "?";
        };
    }

    /** Scoreboard team name (must be ≤ 16 chars and unique per rank). */
    public static String teamName(String rank) {
        return "ae_" + rankAbbrev(rank).toLowerCase();
    }

    // ── Component builders ────────────────────────────────────────────────────

    /**
     * Returns the overhead nametag prefix for a player.
     * Uses the full rank name if it fits (prefix + space + playerName ≤ 24 chars),
     * otherwise falls back to the short abbreviation.
     *
     * Examples at threshold 24:
     *   "Anutin Player1"          →  "Anutin " prefix  (14 chars total ✓)
     *   "Jensen Huang Player123"  →  "JH " prefix       (25 chars total → abbreviate)
     *   "CEO VeryLongPlayerName"  →  "CEO " prefix      (22 chars total ✓)
     */
    public static Component overheadPrefix(String rank, String playerName) {
        String full   = rank + " ";
        String abbrev = rankAbbrev(rank) + " ";
        String chosen = (full.length() + playerName.length() <= 24) ? full : abbrev;
        return Component.literal(chosen).withStyle(rankColor(rank));
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
