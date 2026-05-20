package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;

import com.mojang.serialization.DataResult;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private Connection conn;

    public DatabaseManager() {
        try {
            File dbFile = new File("config/andromeda-economy/players.db");
            dbFile.getParentFile().mkdirs();
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement s = conn.createStatement()) {
                s.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid     TEXT PRIMARY KEY,
                        username TEXT,
                        balance  REAL    DEFAULT 0.0,
                        kills    INTEGER DEFAULT 0
                    )""");
                // Non-destructive migration: add total_spend if it doesn't exist yet
                try { s.execute("ALTER TABLE players ADD COLUMN total_spend REAL DEFAULT 0.0"); }
                catch (SQLException ignored) { /* already exists */ }
                s.execute("""
                    CREATE TABLE IF NOT EXISTS ender_chest_ext (
                        uuid TEXT    NOT NULL,
                        slot INTEGER NOT NULL,
                        item_nbt TEXT NOT NULL,
                        PRIMARY KEY (uuid, slot)
                    )""");
            }
        } catch (Exception e) {
            AndromedaEconomy.LOGGER.error("Failed to initialise SQLite database", e);
        }
    }

    public void ensurePlayer(String uuid, String username) {
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO players (uuid, username, balance, kills) VALUES (?,?,0.0,0)")) {
                ps.setString(1, uuid);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE players SET username=? WHERE uuid=?")) {
                ps.setString(1, username);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("ensurePlayer failed for {}", uuid, e);
        }
    }

    public PlayerData getPlayer(String uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, username, balance, kills, total_spend FROM players WHERE uuid=?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerData(rs.getString("uuid"), rs.getString("username"),
                        rs.getDouble("balance"), rs.getInt("kills"), rs.getDouble("total_spend"));
            }
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("getPlayer failed for {}", uuid, e);
        }
        return null;
    }

    public PlayerData getPlayerByName(String username) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, username, balance, kills, total_spend FROM players WHERE LOWER(username)=LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerData(rs.getString("uuid"), rs.getString("username"),
                        rs.getDouble("balance"), rs.getInt("kills"), rs.getDouble("total_spend"));
            }
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("getPlayerByName failed for {}", username, e);
        }
        return null;
    }

    public void setBalance(String uuid, double balance) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET balance=? WHERE uuid=?")) {
            ps.setDouble(1, balance);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("setBalance failed for {}", uuid, e);
        }
    }

    public boolean transferBalance(String fromUuid, String toUuid, double amount) {
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement deduct = conn.prepareStatement(
                    "UPDATE players SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
                deduct.setDouble(1, amount);
                deduct.setString(2, fromUuid);
                deduct.setDouble(3, amount);
                if (deduct.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
            }
            try (PreparedStatement add = conn.prepareStatement(
                    "UPDATE players SET balance = balance + ? WHERE uuid = ?")) {
                add.setDouble(1, amount);
                add.setString(2, toUuid);
                add.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            AndromedaEconomy.LOGGER.error("transferBalance failed", e);
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    public void incrementKills(String uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET kills = kills + 1 WHERE uuid=?")) {
            ps.setString(1, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("incrementKills failed for {}", uuid, e);
        }
    }

    public List<String> getAllUsernames() {
        try (Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT username FROM players ORDER BY username");
            List<String> names = new ArrayList<>();
            while (rs.next()) names.add(rs.getString("username"));
            return names;
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("getAllUsernames failed", e);
            return List.of();
        }
    }

    public void addSpend(String uuid, double amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET total_spend = total_spend + ? WHERE uuid=?")) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("addSpend failed for {}", uuid, e);
        }
    }

    // ── Ender chest extended storage ─────────────────────────────────────────

    public void saveEnderChestExt(String uuid, SimpleContainer extra, RegistryAccess ra) {
        try {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM ender_chest_ext WHERE uuid = ?")) {
                del.setString(1, uuid);
                del.executeUpdate();
            }
            RegistryOps<Tag> ops = ra.createSerializationContext(NbtOps.INSTANCE);
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO ender_chest_ext (uuid, slot, item_nbt) VALUES (?, ?, ?)")) {
                for (int i = 0; i < extra.getContainerSize(); i++) {
                    ItemStack stack = extra.getItem(i);
                    if (stack.isEmpty()) continue;
                    DataResult<Tag> res = ItemStack.CODEC.encodeStart(ops, stack);
                    Tag tag = res.result().orElse(null);
                    if (tag == null) continue;
                    ins.setString(1, uuid);
                    ins.setInt(2, i);
                    ins.setString(3, tag.toString());
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("saveEnderChestExt failed for {}", uuid, e);
        }
    }

    public void loadEnderChestExt(String uuid, SimpleContainer extra, RegistryAccess ra) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT slot, item_nbt FROM ender_chest_ext WHERE uuid = ?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            RegistryOps<Tag> ops = ra.createSerializationContext(NbtOps.INSTANCE);
            while (rs.next()) {
                int slot = rs.getInt("slot");
                if (slot < 0 || slot >= extra.getContainerSize()) continue;
                try {
                    Tag tag = TagParser.parseCompoundFully(rs.getString("item_nbt"));
                    extra.setItem(slot, ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY));
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("loadEnderChestExt failed for {}", uuid, e);
        }
    }

    public void addBalance(String uuid, double amount) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE players SET balance = balance + ? WHERE uuid=?")) {
            ps.setDouble(1, amount);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("addBalance failed for {}", uuid, e);
        }
    }
}
