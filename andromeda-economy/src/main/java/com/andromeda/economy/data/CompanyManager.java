package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.util.*;

public class CompanyManager {
    private static final File FILE = new File("config/andromeda-economy/companies.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int MAX_NAME_LENGTH = 16;

    /** company name (lower) → CompanyData */
    private final Map<String, CompanyData> companies = new LinkedHashMap<>();
    /** member UUID → company name (lower) for fast lookup */
    private final Map<String, String> memberIndex = new HashMap<>();

    public CompanyManager() { load(); }

    // ── Queries ───────────────────────────────────────────────────────────────

    public CompanyData getByName(String name) {
        return companies.get(name.toLowerCase());
    }

    public CompanyData getByMember(String uuid) {
        String key = memberIndex.get(uuid);
        return key != null ? companies.get(key) : null;
    }

    public List<CompanyData> allSortedByRevenue() {
        List<CompanyData> list = new ArrayList<>(companies.values());
        list.sort(Comparator.comparingDouble((CompanyData c) -> c.revenue).reversed());
        return list;
    }

    public boolean nameExists(String name) {
        return companies.containsKey(name.toLowerCase());
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public boolean create(String name, String ownerUUID) {
        String key = name.toLowerCase();
        if (companies.containsKey(key)) return false;
        CompanyData c = new CompanyData(name, ownerUUID);
        companies.put(key, c);
        memberIndex.put(ownerUUID, key);
        save();
        return true;
    }

    public void disband(String name) {
        String key = name.toLowerCase();
        CompanyData c = companies.remove(key);
        if (c == null) return;
        memberIndex.remove(c.ownerUUID);
        c.memberShares.keySet().forEach(memberIndex::remove);
        save();
    }

    public void addMember(CompanyData company, String uuid, int sharePercent) {
        company.memberShares.put(uuid, sharePercent);
        memberIndex.put(uuid, company.name.toLowerCase());
        save();
    }

    public void removeMember(CompanyData company, String uuid) {
        company.memberShares.remove(uuid);
        memberIndex.remove(uuid);
        save();
    }

    public void setShare(CompanyData company, String uuid, int percent) {
        company.memberShares.put(uuid, percent);
        save();
    }

    public void transferOwnership(CompanyData company, String newOwnerUUID) {
        String oldOwnerUUID = company.ownerUUID;
        // Old owner becomes a regular member with 0% (new owner can set later)
        company.memberShares.put(oldOwnerUUID, 0);
        company.memberShares.remove(newOwnerUUID);
        company.ownerUUID = newOwnerUUID;
        // Index already points both UUIDs to this company — no change needed
        save();
    }

    /**
     * Distributes earnings when a company member earns money.
     * Returns the amount the earner actually receives (their share %).
     * Also credits all other members their share and notifies online players.
     */
    public double distribute(String earnerUUID, double amount, MinecraftServer server) {
        CompanyData company = getByMember(earnerUUID);
        if (company == null) return amount;

        int earnerPct = company.shareOf(earnerUUID);
        double earnerAmount = Math.floor(amount * earnerPct / 100.0);

        // Give every other member their cut
        for (String memberUUID : allMemberUUIDs(company)) {
            if (memberUUID.equals(earnerUUID)) continue;
            int pct = company.shareOf(memberUUID);
            if (pct <= 0) continue;
            double cut = Math.floor(amount * pct / 100.0);
            AndromedaEconomy.db.addBalance(memberUUID, cut);

            ServerPlayer online = server.getPlayerList()
                .getPlayer(UUID.fromString(memberUUID));
            if (online != null) {
                online.sendSystemMessage(
                    Component.literal("[" + company.name + "] ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("You received ")
                            .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(EconomyUtils.compact(cut) + " THB")
                            .withStyle(ChatFormatting.GREEN))
                );
                AndromedaEconomy.hud.update(online);
            }
        }

        // Track cumulative company revenue
        company.revenue += amount;
        save();

        return earnerAmount;
    }

    /** Returns all UUIDs in the company (owner + all members). */
    public static List<String> allMemberUUIDs(CompanyData company) {
        List<String> list = new ArrayList<>();
        list.add(company.ownerUUID);
        list.addAll(company.memberShares.keySet());
        return list;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save() {
        try {
            FILE.getParentFile().mkdirs();
            JsonArray arr = new JsonArray();
            for (CompanyData c : companies.values()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", c.name);
                obj.addProperty("ownerUUID", c.ownerUUID);
                obj.addProperty("revenue", c.revenue);
                JsonObject shares = new JsonObject();
                c.memberShares.forEach((uuid, pct) -> shares.addProperty(uuid, pct));
                obj.add("memberShares", shares);
                JsonArray pending = new JsonArray();
                c.pendingApplications.forEach(pending::add);
                obj.add("pendingApplications", pending);
                arr.add(obj);
            }
            try (Writer w = new FileWriter(FILE)) { GSON.toJson(arr, w); }
        } catch (Exception e) {
            AndromedaEconomy.LOGGER.error("Failed to save companies", e);
        }
    }

    private void load() {
        if (!FILE.exists()) return;
        try (Reader r = new FileReader(FILE)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return;
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                CompanyData c = new CompanyData();
                c.name      = obj.get("name").getAsString();
                c.ownerUUID = obj.get("ownerUUID").getAsString();
                c.revenue   = obj.get("revenue").getAsDouble();
                if (obj.has("memberShares")) {
                    for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("memberShares").entrySet())
                        c.memberShares.put(e.getKey(), e.getValue().getAsInt());
                }
                if (obj.has("pendingApplications")) {
                    for (JsonElement ap : obj.getAsJsonArray("pendingApplications"))
                        c.pendingApplications.add(ap.getAsString());
                }
                companies.put(c.name.toLowerCase(), c);
                memberIndex.put(c.ownerUUID, c.name.toLowerCase());
                c.memberShares.keySet().forEach(uuid -> memberIndex.put(uuid, c.name.toLowerCase()));
            }
        } catch (Exception e) {
            AndromedaEconomy.LOGGER.error("Failed to load companies", e);
        }
    }
}
