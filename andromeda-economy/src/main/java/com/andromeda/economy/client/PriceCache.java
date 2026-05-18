package com.andromeda.economy.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of SELL prices received from the server.
 *
 * The server pre-computes the correct sell price for every item
 * (including the no-discount rules for gold and Bitcoin) and sends
 * those values directly.  This class just stores and looks them up —
 * no further multiplication is applied, so gold/Bitcoin display correctly.
 */
@Environment(EnvType.CLIENT)
public final class PriceCache {

    private static Map<String, Double> sellPrices = Collections.emptyMap();

    private PriceCache() {}

    public static void update(Map<String, Double> received) {
        sellPrices = new HashMap<>(received);
    }

    public static void clear() {
        sellPrices = Collections.emptyMap();
    }

    public static boolean isLoaded() {
        return !sellPrices.isEmpty();
    }

    /** Returns the sell price for the given item registry ID, or -1 if unknown. */
    public static double getSellPrice(String itemId) {
        Double p = sellPrices.get(itemId);
        return p != null ? p : -1;
    }
}
