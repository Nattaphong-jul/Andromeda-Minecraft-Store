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
import net.minecraft.sounds.SoundEvents;

import java.io.*;
import java.util.*;

public class LotteryManager {
    private static final File FILE = new File("config/andromeda-economy/lottery.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final long ROUND_TICKS = 24L * 72_000L; // 24 real hours at 20 TPS
    public static final int MAX_TICKETS = 2;
    public static final double[] PRIZES = {200_000_000.0, 100_000_000.0, 50_000_000.0};
    public static final int POOL_SIZE = 36;

    private double ticketPrice = 100_000.0;
    private int currentRoundId = 1;
    private long roundStartTime = -1;
    /** World-time tick at which the claim window closes (-1 = no window open). */
    private long claimWindowEndTime = -1L;
    private String[] currentWinningNumbers = {"", "", ""};
    private String[] previousWinningNumbers = {"", "", ""};
    private int previousRoundId = 0;
    /** Fixed pool of numbers shown in the lottery chest this round. */
    private String[] roundPool = new String[POOL_SIZE];

    private final Map<String, PlayerLotteryData> playerData = new HashMap<>();
    private final Map<String, List<Integer>> pendingNotifications = new HashMap<>();

    public static class PlayerLotteryData {
        public int roundId;
        public List<String> numbers = new ArrayList<>();
        public boolean[] claimedTiers = new boolean[3];
    }

    public LotteryManager() {
        Arrays.fill(roundPool, "000000");
        load();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        long worldTime = server.overworld().getGameTime();
        if (roundStartTime < 0) {
            roundStartTime = worldTime;
            generateRoundPool();
            save();
            return;
        }
        if (worldTime - roundStartTime >= ROUND_TICKS) {
            conductDraw(server);
        }
    }

    /**
     * Generates a fresh 36-number pool for the round and picks 3 of them as winning numbers.
     * Called at round start so the prize is determined before the draw fires.
     */
    private void generateRoundPool() {
        Random rng = new Random();
        Set<String> pool = new LinkedHashSet<>();
        // Pick 3 unique winning numbers first
        for (int t = 0; t < 3; t++) {
            String win;
            do { win = String.format("%06d", rng.nextInt(1_000_000)); }
            while (pool.contains(win));
            currentWinningNumbers[t] = win;
            pool.add(win);
        }
        // Fill remaining slots to POOL_SIZE
        while (pool.size() < POOL_SIZE) {
            pool.add(String.format("%06d", rng.nextInt(1_000_000)));
        }
        List<String> list = new ArrayList<>(pool);
        Collections.shuffle(list, rng);
        roundPool = list.toArray(new String[0]);
    }

    private void conductDraw(MinecraftServer server) {
        long worldTime = server.overworld().getGameTime();

        // Notify winners in the round that just ended
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

        // Clear data from the PREVIOUS claim period (now 2 rounds old — expired)
        if (previousRoundId > 0) {
            int expiredRound = previousRoundId;
            playerData.values().removeIf(d -> d.roundId == expiredRound);
        }
        pendingNotifications.clear();

        // Archive current round as previous
        previousWinningNumbers = currentWinningNumbers.clone();
        previousRoundId = currentRoundId;

        // Start new round immediately — claim window runs concurrently for 24 h
        currentRoundId++;
        roundStartTime = worldTime;
        currentWinningNumbers = new String[]{"", "", ""};
        claimWindowEndTime = worldTime + ROUND_TICKS;
        generateRoundPool();

        // Broadcast round-end to all online players
        Component broadcast = Component.literal("[Lottery] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Round #" + previousRoundId + " has ended! ")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal("Winners notified. Prizes claimable for 24 hours.")
                .withStyle(ChatFormatting.YELLOW));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(broadcast);
        }

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
        AndromedaEconomy.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
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

    /** Returns this round's ticket data, or null if the player has none this round. */
    public PlayerLotteryData getPlayerData(String uuid) {
        PlayerLotteryData d = playerData.get(uuid);
        if (d == null || d.roundId != currentRoundId) return null;
        return d;
    }

    /** Returns the previous round's ticket data (for claiming), or null if none. */
    public PlayerLotteryData getPreviousRoundData(String uuid) {
        PlayerLotteryData d = playerData.get(uuid);
        if (d == null || d.roundId != previousRoundId) return null;
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

    /**
     * Claims a prize for a previous-round ticket.
     * Returns the amount earned (0 if window closed, wrong round, or already claimed).
     */
    public double claimPrize(String uuid, String ticketNumber, MinecraftServer server) {
        long worldTime = server.overworld().getGameTime();
        if (claimWindowEndTime < 0 || worldTime >= claimWindowEndTime) return 0;
        PlayerLotteryData d = playerData.get(uuid);
        if (d == null || d.roundId != previousRoundId) return 0;
        double total = 0;
        int count = Collections.frequency(d.numbers, ticketNumber);
        for (int t = 0; t < 3; t++) {
            if (previousWinningNumbers[t].equals(ticketNumber) && !d.claimedTiers[t]) {
                d.claimedTiers[t] = true;
                total += count * PRIZES[t];
            }
        }
        if (total > 0) save();
        return total;
    }

    // ── Admin helpers ──────────────────────────────────────────────────────────

    public void forceDraw(MinecraftServer server) {
        conductDraw(server);
    }

    /**
     * Directly changes the winning number for a tier in the current round.
     * If the number isn't already in the pool, it replaces a non-winning slot so players can buy it.
     */
    public void setCurrentWinningNumber(int tier, String number) {
        if (tier < 0 || tier >= 3) return;
        boolean inPool = false;
        for (String s : roundPool) if (s.equals(number)) { inPool = true; break; }
        if (!inPool) {
            for (int i = 0; i < roundPool.length; i++) {
                boolean isWinner = false;
                for (String w : currentWinningNumbers) if (roundPool[i].equals(w)) { isWinner = true; break; }
                if (!isWinner) { roundPool[i] = number; break; }
            }
        }
        currentWinningNumbers[tier] = number;
        save();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public double getTicketPrice()               { return ticketPrice; }
    public int getCurrentRoundId()               { return currentRoundId; }
    public String[] getCurrentWinningNumbers()   { return currentWinningNumbers; }
    public String[] getPreviousWinningNumbers()  { return previousWinningNumbers; }
    public int getPreviousRoundId()              { return previousRoundId; }
    public String[] getRoundPool()               { return roundPool; }
    public long getClaimWindowEndTime()          { return claimWindowEndTime; }

    /** Returns true when a draw has happened and prizes can still be claimed. */
    public boolean isClaimWindowOpen(MinecraftServer server) {
        if (claimWindowEndTime < 0) return false;
        return server.overworld().getGameTime() < claimWindowEndTime;
    }

    public long getTicksUntilDraw(MinecraftServer server) {
        if (roundStartTime < 0) return 0;
        long worldTime = server.overworld().getGameTime();
        return Math.max(0, (roundStartTime + ROUND_TICKS) - worldTime);
    }

    public long getClaimWindowRemainingTicks(MinecraftServer server) {
        if (claimWindowEndTime < 0) return 0;
        long worldTime = server.overworld().getGameTime();
        return Math.max(0, claimWindowEndTime - worldTime);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            root.addProperty("ticketPrice", ticketPrice);
            root.addProperty("currentRoundId", currentRoundId);
            root.addProperty("roundStartTime", roundStartTime);
            root.addProperty("claimWindowEndTime", claimWindowEndTime);
            root.addProperty("previousRoundId", previousRoundId);

            JsonArray win = new JsonArray();
            for (String n : currentWinningNumbers) win.add(n);
            root.add("currentWinningNumbers", win);

            JsonArray prevWin = new JsonArray();
            for (String n : previousWinningNumbers) prevWin.add(n);
            root.add("previousWinningNumbers", prevWin);

            JsonArray pool = new JsonArray();
            for (String n : roundPool) pool.add(n);
            root.add("roundPool", pool);

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
            if (root.has("ticketPrice"))        ticketPrice = root.get("ticketPrice").getAsDouble();
            if (root.has("currentRoundId"))     currentRoundId = root.get("currentRoundId").getAsInt();
            if (root.has("roundStartTime"))     roundStartTime = root.get("roundStartTime").getAsLong();
            if (root.has("claimWindowEndTime")) claimWindowEndTime = root.get("claimWindowEndTime").getAsLong();
            if (root.has("previousRoundId"))    previousRoundId = root.get("previousRoundId").getAsInt();

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
            if (root.has("roundPool")) {
                JsonArray arr = root.getAsJsonArray("roundPool");
                roundPool = new String[arr.size()];
                for (int i = 0; i < arr.size(); i++) roundPool[i] = arr.get(i).getAsString();
            } else {
                generateRoundPool();
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
