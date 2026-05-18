package com.andromeda.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches the live gold spot price (USD/oz) from a free metals API and converts
 * it to in-game THB, then pushes the update into PriceManager.
 *
 * If the API is unavailable the cached/fallback price is kept and the game
 * continues without any crash.
 *
 * Scaling: at ~$3 200/oz → 60 000 THB  (factor ≈ 18.75)
 */
public class GoldPriceService {

    /** Default in-game gold price used on first start / API failure. */
    public static final double FALLBACK_PRICE = 60_000.0;

    /** USD-per-oz → in-game THB multiplier.  Adjust to taste. */
    private static final double SCALE = 18.75;

    /** Primary free API — no key required. */
    private static final String API_URL = "https://api.metals.live/v1/spot/gold";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Most recently successfully fetched price. */
    private volatile double cachedPrice = FALLBACK_PRICE;

    // ─────────────────────────────────────────────────────────────────────────

    /** Fires an async HTTP request; result is applied on the server thread. */
    public void fetchAndApply(MinecraftServer server) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                double usd = parseGoldPrice(resp.body());
                if (usd <= 0) throw new IllegalStateException("Non-positive gold price: " + usd);

                // Round to nearest 1 000 THB for clean display
                double thb = Math.round(usd * SCALE / 1_000.0) * 1_000.0;
                cachedPrice = thb;

                server.execute(() -> {
                    AndromedaEconomy.prices.updateGoldPrice(thb);
                    AndromedaEconomy.LOGGER.info("Gold price updated: {:.0f} THB (${:.2f}/oz)", thb, usd);
                });

            } catch (Exception e) {
                // Never let a network error crash the server
                AndromedaEconomy.LOGGER.warn(
                    "Gold price API failed — keeping cached price ({} THB): {}",
                    cachedPrice, e.getMessage());
            }
        });
    }

    public double getCachedPrice() { return cachedPrice; }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles two common response shapes:
     *   [{"gold": 3200.5}]   ← metals.live array
     *   {"gold": 3200.5}      ← object
     *   {"price": 3200.5}     ← alternative key
     */
    private static double parseGoldPrice(String body) {
        try {
            var el = JsonParser.parseString(body.trim());
            JsonObject obj = el.isJsonArray()
                    ? el.getAsJsonArray().get(0).getAsJsonObject()
                    : el.getAsJsonObject();

            for (String key : new String[]{"gold", "price", "rate", "XAU"}) {
                if (obj.has(key)) return obj.get(key).getAsDouble();
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
