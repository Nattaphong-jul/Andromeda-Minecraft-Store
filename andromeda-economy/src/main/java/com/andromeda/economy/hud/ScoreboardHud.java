package com.andromeda.economy.hud;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.RankManager;
import com.andromeda.economy.data.PlayerData;
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
 *   [Bold Aqua] Andromeda   ← objective title
 *   <blank>                  ← score 6
 *   B Money 1.5K             ← score 5
 *   ⚔ Kills 23               ← score 4
 *   ★ Rank CEO               ← score 3
 *   <blank>                  ← score 2
 *   Ping (12 ms)             ← score 1
 */
public class ScoreboardHud {

    private static final String LINE_SP1  = "ae_s1";
    private static final String LINE_BAL  = "ae_bl";
    private static final String LINE_KLS  = "ae_kl";
    private static final String LINE_RANK = "ae_rk";
    private static final String LINE_SP2  = "ae_s2";
    private static final String LINE_PING = "ae_pg";

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

        // Keep RankManager cache in sync — Mixin reads from here for tab list
        RankManager.cacheBalance(player.getUUID(), data.balance);

        Objective obj = getOrCreate(player);
        String uuid = player.getStringUUID();

        int mode = initialised.add(uuid)
            ? ClientboundSetObjectivePacket.METHOD_ADD
            : ClientboundSetObjectivePacket.METHOD_CHANGE;
        player.connection.send(new ClientboundSetObjectivePacket(obj, mode));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));

        sendLine(player, obj.getName(), LINE_SP1, 6, Component.literal(" "));

        MutableComponent moneyLine = Component.literal("B ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("Money ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(EconomyUtils.compact(data.balance)).withStyle(ChatFormatting.GREEN));
        sendLine(player, obj.getName(), LINE_BAL, 5, moneyLine);

        MutableComponent killsLine = Component.literal("⚔ ").withStyle(ChatFormatting.RED)
            .append(Component.literal("Kills ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(EconomyUtils.compact(data.kills)).withStyle(ChatFormatting.RED));
        sendLine(player, obj.getName(), LINE_KLS, 4, killsLine);

        String rank = RankManager.rankName(data.balance);
        MutableComponent rankLine = Component.literal("★ ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Rank ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(rank).withStyle(RankManager.rankColor(rank)));
        sendLine(player, obj.getName(), LINE_RANK, 3, rankLine);

        sendLine(player, obj.getName(), LINE_SP2, 2, Component.literal(" "));

        sendLine(player, obj.getName(), LINE_PING, 1,
            Component.literal("Ping (" + player.connection.latency() + " ms)")
                .withStyle(ChatFormatting.WHITE));

        // Assign overhead team (scoreboard team prefix controls nametag)
        assignOverheadTeam(player, rank);

        // Broadcast updated tab-list display name to ALL connected players
        var packet = new ClientboundPlayerInfoUpdatePacket(
            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player);
        player.level().getServer().getPlayerList().broadcastAll(packet);
    }

    public void updatePingOnly(ServerPlayer player) {
        Objective obj = objectives.get(player.getStringUUID());
        if (obj == null || !initialised.contains(player.getStringUUID())) return;
        sendLine(player, obj.getName(), LINE_PING, 1,
            Component.literal("Ping (" + player.connection.latency() + " ms)")
                .withStyle(ChatFormatting.WHITE));
    }

    public void remove(ServerPlayer player) {
        String uuid = player.getStringUUID();
        initialised.remove(uuid);
        RankManager.removeCache(player.getUUID());
        Objective obj = objectives.remove(uuid);
        if (obj == null) return;
        player.connection.send(
            new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Moves the player into the appropriate rank team so the overhead prefix updates. */
    private static void assignOverheadTeam(ServerPlayer player, String rank) {
        var scoreboard = player.level().getServer().getScoreboard();
        String teamName = RankManager.teamName(rank);
        var team = scoreboard.getPlayerTeam(teamName);
        if (team == null) return; // teams created in AndromedaEconomy.setupRankTeams()
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }

    private static void sendLine(ServerPlayer player, String objName, String holder,
                                  int score, Component display) {
        player.connection.send(new ClientboundSetScorePacket(
            holder, objName, score,
            Optional.of(display), Optional.of(BlankFormat.INSTANCE)));
    }
}
