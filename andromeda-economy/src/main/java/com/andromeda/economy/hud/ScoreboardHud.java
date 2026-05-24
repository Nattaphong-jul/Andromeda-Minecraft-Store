package com.andromeda.economy.hud;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.RankManager;
import com.andromeda.economy.data.LotteryManager;
import com.andromeda.economy.data.PlayerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-player sidebar HUD.
 *
 * Sidebar layout (descending score):
 *   [Bold Aqua] Andromeda   ← title
 *   <blank>                  ← 8
 *   B Money …                ← 7  (green B, white label, green amount)
 *   A Asset …                ← 6  (yellow A, white label, yellow amount)
 *   ⚔ Kills …                ← 5  (red ⚔, white label, red count)
 *   $ Spend …                ← 4  (magenta $, white label, magenta amount)
 *   ★ Rank …                 ← 3  (gold ★, white label, rank colour)
 *   <blank>                  ← 2
 *   Ping …                   ← 1  (white)
 *
 * Rank is calculated from balance + ender-chest asset value.
 */
public class ScoreboardHud {

    private static final String LINE_SP1     = "ae_s1";
    private static final String LINE_BAL     = "ae_bl";
    private static final String LINE_ASSET   = "ae_at";
    private static final String LINE_KLS     = "ae_kl";
    private static final String LINE_LOTTERY = "ae_lt";
    private static final String LINE_SPEND   = "ae_sd";
    private static final String LINE_RANK    = "ae_rk";
    private static final String LINE_SP2     = "ae_s2";
    private static final String LINE_PING    = "ae_pg";

    private final Map<String, Objective> objectives = new HashMap<>();
    private final Set<String> initialised = new HashSet<>();

    // ─────────────────────────────────────────────────────────────────────────

    private Objective getOrCreate(ServerPlayer player) {
        return objectives.computeIfAbsent(player.getStringUUID(), uuid -> {
            Scoreboard local = new Scoreboard();
            String name = "ae_" + uuid.replace("-", "").substring(0, 13);
            MutableComponent title = Component.literal("Andromeda")
                .withStyle(s -> s.withBold(true).withColor(ChatFormatting.AQUA));
            return local.addObjective(
                name, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        });
    }

    public void update(ServerPlayer player) {
        PlayerData data = AndromedaEconomy.db.getPlayer(player.getStringUUID());
        if (data == null) return;

        double assets      = AndromedaEconomy.enderChestAssets.getOrDefault(player.getUUID(), 0.0);
        double totalWealth = data.balance + assets;

        // Keep wealth cache in sync — Mixin reads from here for tab-list rank
        RankManager.cacheWealth(player.getUUID(), totalWealth);

        Objective obj = getOrCreate(player);
        String uuid = player.getStringUUID();

        int mode = initialised.add(uuid)
            ? ClientboundSetObjectivePacket.METHOD_ADD
            : ClientboundSetObjectivePacket.METHOD_CHANGE;
        player.connection.send(new ClientboundSetObjectivePacket(obj, mode));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));

        // Spacer (9)
        sendLine(player, obj.getName(), LINE_SP1, 9, Component.literal(" "));

        // B Money (8)
        sendLine(player, obj.getName(), LINE_BAL, 8,
            Component.literal("B ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Money ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(EconomyUtils.compact(data.balance)).withStyle(ChatFormatting.GREEN)));

        // A Asset (7)
        sendLine(player, obj.getName(), LINE_ASSET, 7,
            Component.literal("◆ ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Asset ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(EconomyUtils.compact(assets)).withStyle(ChatFormatting.YELLOW)));

        // ⚔ Kills (6)
        sendLine(player, obj.getName(), LINE_KLS, 6,
            Component.literal("⚔ ").withStyle(ChatFormatting.RED)
                .append(Component.literal("Kills ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(EconomyUtils.compact(data.kills)).withStyle(ChatFormatting.RED)));

        // ⏱ Lottery (5)
        sendLine(player, obj.getName(), LINE_LOTTERY, 5, buildLotteryLine(player.level().getServer()));

        // $ Spend (4)
        sendLine(player, obj.getName(), LINE_SPEND, 4,
            Component.literal("$ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("Spend ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(EconomyUtils.compact(data.totalSpend)).withStyle(ChatFormatting.LIGHT_PURPLE)));

        // ★ Rank (3) — based on total wealth
        String rank = RankManager.rankName(totalWealth);
        sendLine(player, obj.getName(), LINE_RANK, 3,
            Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Rank ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(rank).withStyle(RankManager.rankColor(rank))));

        // Spacer (2) — non-breaking space prevents client deduplication
        sendLine(player, obj.getName(), LINE_SP2, 2, Component.literal(" "));

        // Ping (1)
        sendLine(player, obj.getName(), LINE_PING, 1,
            Component.literal("Ping (" + player.connection.latency() + " ms)")
                .withStyle(ChatFormatting.WHITE));

        // Overhead team (nametag prefix) — also based on total wealth
        assignOverheadTeam(player, rank);

        // Broadcast updated tab-list name to all players
        player.level().getServer().getPlayerList().broadcastAll(
            new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    public void updatePingOnly(ServerPlayer player) {
        Objective obj = objectives.get(player.getStringUUID());
        if (obj == null || !initialised.contains(player.getStringUUID())) return;
        sendLine(player, obj.getName(), LINE_PING, 1,
            Component.literal("Ping (" + player.connection.latency() + " ms)")
                .withStyle(ChatFormatting.WHITE));
    }

    public void updateLotteryLine(ServerPlayer player) {
        Objective obj = objectives.get(player.getStringUUID());
        if (obj == null || !initialised.contains(player.getStringUUID())) return;
        sendLine(player, obj.getName(), LINE_LOTTERY, 5, buildLotteryLine(player.level().getServer()));
    }

    private static net.minecraft.network.chat.MutableComponent buildLotteryLine(MinecraftServer server) {
        LotteryManager lottery = AndromedaEconomy.lottery;
        if (lottery == null) {
            return Component.literal("⏱ ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Lottery").withStyle(ChatFormatting.WHITE));
        }
        if (lottery.isResultWindowActive()) {
            return Component.literal("⏱ ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Showing Result").withStyle(ChatFormatting.YELLOW));
        }
        long ticks = lottery.getTicksUntilDraw(server);
        long days  = ticks / 24000;
        long hours = (ticks % 24000) / 1000;
        return Component.literal("⏱ ").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal("Lottery ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(days + "d " + hours + "h").withStyle(ChatFormatting.YELLOW));
    }

    public void remove(ServerPlayer player) {
        String uuid = player.getStringUUID();
        initialised.remove(uuid);
        RankManager.removeCache(player.getUUID());
        removeOverheadTeam(player);
        Objective obj = objectives.remove(uuid);
        if (obj == null) return;
        player.connection.send(
            new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Each player gets their own personal scoreboard team so the overhead prefix
     * can be tailored to their name length.  Full rank name is shown when it fits
     * (prefix + name ≤ 24 chars), otherwise the short abbreviation is used.
     */
    private static void assignOverheadTeam(ServerPlayer player, String rank) {
        var scoreboard = player.level().getServer().getScoreboard();
        String teamName = "ae_" + player.getStringUUID().replace("-", "").substring(0, 14);

        // Remove from any current team first — prevents duplicate membership that
        // causes AMP (and some server monitors) to show the player multiple times.
        scoreboard.removePlayerFromTeam(player.getScoreboardName());

        var team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.ALWAYS);
            team.setColor(net.minecraft.ChatFormatting.RESET);
        }

        team.setPlayerPrefix(RankManager.overheadPrefix(rank, player.getGameProfile().name()));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }

    /** Removes old rank-abbreviation teams left over from versions before per-player teams. */
    public static void cleanupLegacyTeams(net.minecraft.server.MinecraftServer server) {
        var scoreboard = server.getScoreboard();
        // Old teams were named ae_<abbrev> where abbrev is ≤4 chars (e.g. ae_an, ae_ceo, ae_mrb)
        // Per-player teams are ae_<14 hex chars> — easily distinguishable by length
        new java.util.ArrayList<>(scoreboard.getPlayerTeams()).forEach(team -> {
            String n = team.getName();
            if (n.startsWith("ae_") && n.length() <= 7) { // ae_ + up to 4 chars = max 7
                scoreboard.removePlayerTeam(team);
            }
        });
    }

    /** Clean up the player's personal team when they leave. */
    private static void removeOverheadTeam(ServerPlayer player) {
        try {
            var scoreboard = player.level().getServer().getScoreboard();
            String teamName = "ae_" + player.getStringUUID().replace("-", "").substring(0, 14);
            var team = scoreboard.getPlayerTeam(teamName);
            if (team != null) scoreboard.removePlayerTeam(team);
        } catch (Exception ignored) {}
    }

    private static void sendLine(ServerPlayer player, String objName, String holder,
                                  int score, Component display) {
        player.connection.send(new ClientboundSetScorePacket(
            holder, objName, score,
            Optional.of(display), Optional.of(BlankFormat.INSTANCE)));
    }
}
