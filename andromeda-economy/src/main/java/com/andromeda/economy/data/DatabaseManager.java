package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;

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
                "SELECT uuid, username, balance, kills FROM players WHERE uuid=?")) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerData(rs.getString("uuid"), rs.getString("username"),
                        rs.getDouble("balance"), rs.getInt("kills"));
            }
        } catch (SQLException e) {
            AndromedaEconomy.LOGGER.error("getPlayer failed for {}", uuid, e);
        }
        return null;
    }

    public PlayerData getPlayerByName(String username) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, username, balance, kills FROM players WHERE LOWER(username)=LOWER(?)")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PlayerData(rs.getString("uuid"), rs.getString("username"),
                        rs.getDouble("balance"), rs.getInt("kills"));
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
