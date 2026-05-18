package com.andromeda.economy;

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
 * Fetches the live gold spot price (USD/troy oz) and converts it to in-game THB.
 *
 * API:  https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/xau.json
 *       Free, no API key, updated daily. Response: { "xau": { "usd": 4539.0, ... } }
 *
 * Scaling: at ~$4 539/oz the scale factor 13.22 gives ≈ 60 000 THB.
 *   If gold rises to $5 000 → ~66 100 THB; if it falls to $4 000 → ~52 880 THB.
 *
 * If the API is unreachable the cached/fallback price is kept — no crash, no exception
 * propagated to the server thread.
 *
 * Refresh interval: every 15 minutes (configured in AndromedaEconomy tick handler).
 */
public class GoldPriceService {

    public static final double FALLBACK_PRICE = 60_000.0;

    private static final String API_URL =
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/xau.json";

    /** USD-per-oz → in-game THB.  At $4 539/oz this gives ≈ 60 000 THB. */
    private static final double SCALE = 13.22;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile double cachedPrice = FALLBACK_PRICE;

    // ─────────────────────────────────────────────────────────────────────────

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
                double usdPerOz = parsePrice(resp.body());
                if (usdPerOz <= 0) throw new IllegalStateException("Non-positive gold price: " + usdPerOz);

                // Round to nearest 1 000 THB for clean display
                double thb = Math.round(usdPerOz * SCALE / 1_000.0) * 1_000.0;
                cachedPrice = thb;

                server.execute(() -> {
                    AndromedaEconomy.prices.updateGoldPrice(thb);
                    AndromedaEconomy.LOGGER.info(
                        "[Andromeda] Gold price: {} THB (${}/oz)", (long) thb, String.format("%.2f", usdPerOz));
                });

            } catch (Exception e) {
                AndromedaEconomy.LOGGER.warn(
                    "[Andromeda] Gold API unavailable — keeping {} THB: {}", (long) cachedPrice, e.getMessage());
            }
        });
    }

    public double getCachedPrice() { return cachedPrice; }

    // ─────────────────────────────────────────────────────────────────────────

    /** Parses  { "xau": { "usd": 4539.0, ... }, "date": "..." } */
    private static double parsePrice(String body) {
        try {
            JsonObject root = JsonParser.parseString(body.trim()).getAsJsonObject();
            JsonObject xau  = root.getAsJsonObject("xau");
            if (xau != null && xau.has("usd")) return xau.get("usd").getAsDouble();
        } catch (Exception ignored) {}
        return -1;
    }
}
