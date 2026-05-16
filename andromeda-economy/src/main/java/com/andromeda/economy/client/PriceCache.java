package com.andromeda.economy.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of item prices received from the server.
 * Only active when this mod is installed on the client.
 */
@Environment(EnvType.CLIENT)
public final class PriceCache {

    private static Map<String, Double> buyPrices = Collections.emptyMap();

    private PriceCache() {}

    public static void update(Map<String, Double> received) {
        buyPrices = new HashMap<>(received);
    }

    public static void clear() {
        buyPrices = Collections.emptyMap();
    }

    public static boolean isLoaded() {
        return !buyPrices.isEmpty();
    }

    /** Returns the sell price (85 % of buy) for the given item registry ID, or -1 if unknown. */
    public static double getSellPrice(String itemId) {
        Double buy = buyPrices.get(itemId);
        return buy != null ? buy * 0.85 : -1;
    }
}
