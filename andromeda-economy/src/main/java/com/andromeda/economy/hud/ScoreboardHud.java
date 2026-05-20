package com.andromeda.economy.hud;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.andromeda.economy.data.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.BlankFormat;
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
 * Per-player sidebar HUD (per-player packet trick — no client mod required).
 *
 * Sidebar layout (descending score = nearer to title):
 *   [Bold Aqua] Andromeda   ← objective title
 *   <blank>                  ← score 6
 *   B Money 1.5K             ← score 5  (B=green, Money=white, amount=green)
 *   ⚔ Kills 23               ← score 4  (⚔=red, Kills=white, count=red)
 *   ★ Rank CEO               ← score 3  (★=gold, Rank=white, name=rank colour)
 *   <blank>                  ← score 2
 *   Ping (12 ms)             ← score 1  (white)
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
    // Rank helpers

    private static String rankName(double balance) {
        if (balance >= 500_000_000_000.0) return "Elon Musk";
        if (balance >= 1_000_000_000.0)   return "MrBeast";
        if (balance >= 100_000_000.0)     return "CEO";        // 100M – 1B
        if (balance >= 10_000_000.0)      return "Anutin";
        if (balance >= 100_000.0)         return "Salary Man";
        return "Unemployed";
    }

    private static ChatFormatting rankColor(String rank) {
        return switch (rank) {
            case "Salary Man" -> ChatFormatting.WHITE;
            case "Anutin"     -> ChatFormatting.BLUE;
            case "CEO"        -> ChatFormatting.GREEN;
            case "MrBeast"    -> ChatFormatting.AQUA;
            case "Elon Musk"  -> ChatFormatting.GOLD;
            default           -> ChatFormatting.RED;   // Unemployed
        };
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Objective getOrCreate(ServerPlayer player) {
        return objectives.computeIfAbsent(player.getStringUUID(), uuid -> {
            Scoreboard local = new Scoreboard();
            String name = "ae_" + uuid.replace("-", "").substring(0, 13);
            MutableComponent title = Component.literal("Andromeda")
                .withStyle(s -> s.withBold(true).withColor(ChatFormatting.AQUA));
            return local.addObjective(
                name,
                ObjectiveCriteria.DUMMY,
                title,
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                BlankFormat.INSTANCE
            );
        });
    }

    public void update(ServerPlayer player) {
        PlayerData data = AndromedaEconomy.db.getPlayer(player.getStringUUID());
        if (data == null) return;

        Objective obj = getOrCreate(player);
        String uuid = player.getStringUUID();

        int mode = initialised.add(uuid)
            ? ClientboundSetObjectivePacket.METHOD_ADD
            : ClientboundSetObjectivePacket.METHOD_CHANGE;
        player.connection.send(new ClientboundSetObjectivePacket(obj, mode));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, obj));

        // Spacer (score 6)
        sendLine(player, obj.getName(), LINE_SP1, 6, Component.literal(" "));

        // B Money (score 5)
        MutableComponent moneyLine = Component.literal("B ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("Money ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(EconomyUtils.compact(data.balance)).withStyle(ChatFormatting.GREEN));
        sendLine(player, obj.getName(), LINE_BAL, 5, moneyLine);

        // ⚔ Kills (score 4)
        MutableComponent killsLine = Component.literal("⚔ ").withStyle(ChatFormatting.RED)
            .append(Component.literal("Kills ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(EconomyUtils.compact(data.kills)).withStyle(ChatFormatting.RED));
        sendLine(player, obj.getName(), LINE_KLS, 4, killsLine);

        // ★ Rank (score 3)
        String rank = rankName(data.balance);
        MutableComponent rankLine = Component.literal("★ ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Rank ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(rank).withStyle(rankColor(rank)));
        sendLine(player, obj.getName(), LINE_RANK, 3, rankLine);

        // Spacer (score 2) — non-breaking space avoids client deduplication with score-6 spacer
        sendLine(player, obj.getName(), LINE_SP2, 2, Component.literal(" "));

        // Ping (score 1)
        sendLine(player, obj.getName(), LINE_PING, 1,
            Component.literal("Ping (" + player.connection.latency() + " ms)")
                .withStyle(ChatFormatting.WHITE));
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
        Objective obj = objectives.remove(uuid);
        if (obj == null) return;
        player.connection.send(new ClientboundSetObjectivePacket(obj, ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    private static void sendLine(ServerPlayer player, String objName, String holder, int score, Component display) {
        player.connection.send(new ClientboundSetScorePacket(
            holder, objName, score,
            Optional.of(display),
            Optional.of(BlankFormat.INSTANCE)
        ));
    }
}
