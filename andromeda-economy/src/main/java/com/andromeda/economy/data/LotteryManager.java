package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;
import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.util.*;

public class LotteryManager {
    private static final File FILE = new File("config/andromeda-economy/lottery.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final long ROUND_TICKS = 7L * 24000L;
    public static final long RESULT_WINDOW_TICKS = 24000L;
    public static final int MAX_TICKETS = 5;
    public static final double[] PRIZES = {100_000_000.0, 40_000_000.0, 6_000_000.0};

    private double ticketPrice = 100_000.0;
    private int currentRoundId = 1;
    private long roundStartTime = -1;
    private boolean resultWindowActive = false;
    private long resultWindowStartTime = 0;
    private String[] currentWinningNumbers = {"", "", ""};
    private String[] previousWinningNumbers = {"", "", ""};
    private int previousRoundId = 0;

    private final Map<String, PlayerLotteryData> playerData = new HashMap<>();
    private final Map<String, List<Integer>> pendingNotifications = new HashMap<>();

    public static class PlayerLotteryData {
        public int roundId;
        public List<String> numbers = new ArrayList<>();
        public boolean[] claimedTiers = new boolean[3];
    }

    public LotteryManager() {
        load();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        long worldTime = server.overworld().getGameTime();
        if (roundStartTime < 0) {
            roundStartTime = worldTime;
            save();
            return;
        }
        if (resultWindowActive) {
            if (worldTime - resultWindowStartTime >= RESULT_WINDOW_TICKS) {
                endResultWindow(server);
            }
        } else {
            if (worldTime - roundStartTime >= ROUND_TICKS) {
                conductDraw(server);
            }
        }
    }

    private void conductDraw(MinecraftServer server) {
        Random rng = new Random();
        currentWinningNumbers = new String[]{
            String.format("%06d", rng.nextInt(1_000_000)),
            String.format("%06d", rng.nextInt(1_000_000)),
            String.format("%06d", rng.nextInt(1_000_000))
        };
        resultWindowActive = true;
        resultWindowStartTime = server.overworld().getGameTime();

        for (Map.Entry<String, PlayerLotteryData> e : playerData.entrySet()) {
            if (e.getValue().roundId != currentRoundId) continue;
            List<Integer> wonTiers = getWonTiers(e.getValue().numbers);
            if (wonTiers.isEmpty()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
            if (player != null) {
                sendWinTitle(player, wonTiers.get(0));
            } else {
                pendingNotifications.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(wonTiers);
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AndromedaEconomy.hud.update(player);
        }
        save();
    }

    private void endResultWindow(MinecraftServer server) {
        previousWinningNumbers = currentWinningNumbers.clone();
        previousRoundId = currentRoundId;
        playerData.values().removeIf(d -> d.roundId == currentRoundId);
        currentRoundId++;
        roundStartTime = server.overworld().getGameTime();
        resultWindowActive = false;
        currentWinningNumbers = new String[]{"", "", ""};
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AndromedaEconomy.hud.update(player);
        }
        save();
    }

    // ── Login notification ────────────────────────────────────────────────────

    public void onPlayerLogin(ServerPlayer player) {
        List<Integer> pending = pendingNotifications.remove(player.getStringUUID());
        if (pending != null && !pending.isEmpty()) {
            sendWinTitle(player, pending.get(0));
            save();
        }
    }

    private void sendWinTitle(ServerPlayer player, int tier) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.literal("Congratulations!").withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.literal("You won Lottery Prize #" + (tier + 1) + "!").withStyle(ChatFormatting.WHITE)));
    }

    private List<Integer> getWonTiers(List<String> tickets) {
        List<Integer> won = new ArrayList<>();
        for (int t = 0; t < 3; t++) {
            if (!currentWinningNumbers[t].isEmpty() && tickets.contains(currentWinningNumbers[t])) {
                won.add(t);
            }
        }
        return won;
    }

    // ── Ticket operations ─────────────────────────────────────────────────────

    public int getTicketCount(String uuid) {
        PlayerLotteryData d = playerData.get(uuid);
        if (d == null || d.roundId != currentRoundId) return 0;
        return d.numbers.size();
    }

    public PlayerLotteryData getPlayerData(String uuid) {
        PlayerLotteryData d = playerData.get(uuid);
        if (d == null || d.roundId != currentRoundId) return null;
        return d;
    }

    private PlayerLotteryData getOrCreate(String uuid) {
        PlayerLotteryData d = playerData.computeIfAbsent(uuid, k -> {
            PlayerLotteryData nd = new PlayerLotteryData();
            nd.roundId = currentRoundId;
            return nd;
        });
        if (d.roundId != currentRoundId) {
            d.roundId = currentRoundId;
            d.numbers.clear();
            d.claimedTiers = new boolean[3];
        }
        return d;
    }

    public void buyTicket(String uuid, String number) {
        getOrCreate(uuid).numbers.add(number);
        save();
    }

    public double claimPrize(String uuid, String ticketNumber) {
        if (!resultWindowActive) return 0;
        PlayerLotteryData d = playerData.get(uuid);
        if (d == null || d.roundId != currentRoundId) return 0;
        double total = 0;
        int count = Collections.frequency(d.numbers, ticketNumber);
        for (int t = 0; t < 3; t++) {
            if (currentWinningNumbers[t].equals(ticketNumber) && !d.claimedTiers[t]) {
                d.claimedTiers[t] = true;
                total += count * PRIZES[t];
            }
        }
        if (total > 0) save();
        return total;
    }

    // ── Admin / test helpers ──────────────────────────────────────────────────

    /** Forces the draw to happen immediately (OP-only, for testing). */
    public void forceDraw(MinecraftServer server) {
        if (!resultWindowActive) conductDraw(server);
    }

    /** Skips the result window and starts the next round immediately (OP-only, for testing). */
    public void forceEndResult(MinecraftServer server) {
        if (resultWindowActive) endResultWindow(server);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public double getTicketPrice()               { return ticketPrice; }
    public boolean isResultWindowActive()        { return resultWindowActive; }
    public int getCurrentRoundId()               { return currentRoundId; }
    public String[] getCurrentWinningNumbers()   { return currentWinningNumbers; }
    public String[] getPreviousWinningNumbers()  { return previousWinningNumbers; }
    public int getPreviousRoundId()              { return previousRoundId; }

    public long getTicksUntilDraw(MinecraftServer server) {
        if (resultWindowActive || roundStartTime < 0) return 0;
        long worldTime = server.overworld().getGameTime();
        return Math.max(0, (roundStartTime + ROUND_TICKS) - worldTime);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            root.addProperty("ticketPrice", ticketPrice);
            root.addProperty("currentRoundId", currentRoundId);
            root.addProperty("roundStartTime", roundStartTime);
            root.addProperty("resultWindowActive", resultWindowActive);
            root.addProperty("resultWindowStartTime", resultWindowStartTime);
            root.addProperty("previousRoundId", previousRoundId);

            JsonArray win = new JsonArray();
            for (String n : currentWinningNumbers) win.add(n);
            root.add("currentWinningNumbers", win);

            JsonArray prevWin = new JsonArray();
            for (String n : previousWinningNumbers) prevWin.add(n);
            root.add("previousWinningNumbers", prevWin);

            JsonObject tickets = new JsonObject();
            for (Map.Entry<String, PlayerLotteryData> e : playerData.entrySet()) {
                JsonObject p = new JsonObject();
                p.addProperty("roundId", e.getValue().roundId);
                JsonArray nums = new JsonArray();
                for (String n : e.getValue().numbers) nums.add(n);
                p.add("numbers", nums);
                JsonArray claimed = new JsonArray();
                for (boolean b : e.getValue().claimedTiers) claimed.add(b);
                p.add("claimedTiers", claimed);
                tickets.add(e.getKey(), p);
            }
            root.add("playerTickets", tickets);

            JsonObject notifications = new JsonObject();
            for (Map.Entry<String, List<Integer>> e : pendingNotifications.entrySet()) {
                JsonArray tiers = new JsonArray();
                for (int t : e.getValue()) tiers.add(t);
                notifications.add(e.getKey(), tiers);
            }
            root.add("pendingNotifications", notifications);

            try (Writer w = new FileWriter(FILE)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            AndromedaEconomy.LOGGER.error("Failed to save lottery data", e);
        }
    }

    public void load() {
        if (!FILE.exists()) return;
        try (Reader r = new FileReader(FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            if (root.has("ticketPrice"))           ticketPrice = root.get("ticketPrice").getAsDouble();
            if (root.has("currentRoundId"))        currentRoundId = root.get("currentRoundId").getAsInt();
            if (root.has("roundStartTime"))        roundStartTime = root.get("roundStartTime").getAsLong();
            if (root.has("resultWindowActive"))    resultWindowActive = root.get("resultWindowActive").getAsBoolean();
            if (root.has("resultWindowStartTime")) resultWindowStartTime = root.get("resultWindowStartTime").getAsLong();
            if (root.has("previousRoundId"))       previousRoundId = root.get("previousRoundId").getAsInt();

            if (root.has("currentWinningNumbers")) {
                JsonArray arr = root.getAsJsonArray("currentWinningNumbers");
                for (int i = 0; i < 3 && i < arr.size(); i++)
                    currentWinningNumbers[i] = arr.get(i).getAsString();
            }
            if (root.has("previousWinningNumbers")) {
                JsonArray arr = root.getAsJsonArray("previousWinningNumbers");
                for (int i = 0; i < 3 && i < arr.size(); i++)
                    previousWinningNumbers[i] = arr.get(i).getAsString();
            }
            if (root.has("playerTickets")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("playerTickets").entrySet()) {
                    JsonObject p = e.getValue().getAsJsonObject();
                    PlayerLotteryData d = new PlayerLotteryData();
                    d.roundId = p.get("roundId").getAsInt();
                    for (JsonElement n : p.getAsJsonArray("numbers")) d.numbers.add(n.getAsString());
                    JsonArray claimed = p.getAsJsonArray("claimedTiers");
                    for (int i = 0; i < 3 && i < claimed.size(); i++)
                        d.claimedTiers[i] = claimed.get(i).getAsBoolean();
                    playerData.put(e.getKey(), d);
                }
            }
            if (root.has("pendingNotifications")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pendingNotifications").entrySet()) {
                    List<Integer> tiers = new ArrayList<>();
                    for (JsonElement t : e.getValue().getAsJsonArray()) tiers.add(t.getAsInt());
                    pendingNotifications.put(e.getKey(), tiers);
                }
            }
        } catch (Exception e) {
            AndromedaEconomy.LOGGER.error("Failed to load lottery data", e);
        }
    }
}
