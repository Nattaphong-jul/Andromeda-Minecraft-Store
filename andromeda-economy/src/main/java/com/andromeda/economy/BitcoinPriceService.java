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
 * Fetches the live Bitcoin spot price in THB and pushes it into PriceManager.
 *
 * Primary:  Coinbase public API — free, no key, no rate limit documented
 *   https://api.coinbase.com/v2/prices/BTC-THB/spot
 *   Response: {"data":{"amount":"2490963.47","base":"BTC","currency":"THB"}}
 *
 * Fallback: CoinGecko free tier
 *   https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=thb
 *   Response: {"bitcoin":{"thb":2491101}}
 *
 * If both fail the cached/fallback price is kept — the server NEVER crashes.
 *
 * Refresh interval: every 15 minutes (set in AndromedaEconomy tick handler).
 */
public class BitcoinPriceService {

    /** In-game THB starting price used before the first successful API call. */
    public static final double FALLBACK_PRICE = 2_500_000.0;

    private static final String PRIMARY_URL =
        "https://api.coinbase.com/v2/prices/BTC-THB/spot";
    private static final String FALLBACK_URL =
        "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=thb";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Most recently successfully fetched price. */
    private volatile double cachedPrice = FALLBACK_PRICE;

    // ─────────────────────────────────────────────────────────────────────────

    public void fetchAndApply(MinecraftServer server) {
        CompletableFuture.runAsync(() -> {
            double price = tryFetch(PRIMARY_URL, this::parseCoinbase);
            if (price <= 0) {
                AndromedaEconomy.LOGGER.warn("[Andromeda] Coinbase BTC API failed, trying CoinGecko...");
                price = tryFetch(FALLBACK_URL, this::parseCoinGecko);
            }
            if (price <= 0) {
                AndromedaEconomy.LOGGER.warn("[Andromeda] Both BTC APIs failed — keeping {} THB", (long) cachedPrice);
                return;
            }

            final double finalPrice = Math.round(price / 100.0) * 100.0; // round to nearest 100 THB
            cachedPrice = finalPrice;
            server.execute(() -> {
                AndromedaEconomy.prices.updateBitcoinPrice(finalPrice);
                AndromedaEconomy.broadcastPriceMap(server); // push corrected sell prices to all clients
                AndromedaEconomy.LOGGER.info("[Andromeda] Bitcoin price: {} THB", (long) finalPrice);
            });
        });
    }

    public double getCachedPrice() { return cachedPrice; }

    // ─────────────────────────────────────────────────────────────────────────

    private double tryFetch(String url, java.util.function.Function<String, Double> parser) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return parser.apply(resp.body());
        } catch (Exception e) {
            AndromedaEconomy.LOGGER.debug("[Andromeda] BTC fetch error from {}: {}", url, e.getMessage());
            return -1;
        }
    }

    /** {"data":{"amount":"2490963.47","base":"BTC","currency":"THB"}} */
    private double parseCoinbase(String body) {
        try {
            JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
            return Double.parseDouble(data.get("amount").getAsString());
        } catch (Exception e) { return -1; }
    }

    /** {"bitcoin":{"thb":2491101}} */
    private double parseCoinGecko(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonObject("bitcoin").get("thb").getAsDouble();
        } catch (Exception e) { return -1; }
    }
}
