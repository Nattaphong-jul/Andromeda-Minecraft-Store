package com.andromeda.economy.data;

public class PlayerData {
    public final String uuid;
    public final String username;
    public double balance;
    public int kills;

    public PlayerData(String uuid, String username, double balance, int kills) {
        this.uuid = uuid;
        this.username = username;
        this.balance = balance;
        this.kills = kills;
    }
}
