package com.andromeda.economy.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompanyData {
    public String name;
    public String ownerUUID;
    public double revenue;
    /** UUID → share percentage (0–100). Does NOT include the owner. */
    public Map<String, Integer> memberShares = new LinkedHashMap<>();
    public List<String> pendingApplications  = new ArrayList<>();

    public CompanyData() {}

    public CompanyData(String name, String ownerUUID) {
        this.name      = name;
        this.ownerUUID = ownerUUID;
        this.revenue   = 0;
    }

    /** Owner's effective share = 100 − sum of all member shares. */
    public int ownerShare() {
        int memberTotal = memberShares.values().stream().mapToInt(Integer::intValue).sum();
        return Math.max(0, 100 - memberTotal);
    }

    /** Returns the share % for any member or owner UUID. */
    public int shareOf(String uuid) {
        if (uuid.equals(ownerUUID)) return ownerShare();
        return memberShares.getOrDefault(uuid, 0);
    }

    /** True if this UUID belongs to this company (owner or member). */
    public boolean contains(String uuid) {
        return uuid.equals(ownerUUID) || memberShares.containsKey(uuid);
    }

    public boolean isOwner(String uuid) { return uuid.equals(ownerUUID); }
}
