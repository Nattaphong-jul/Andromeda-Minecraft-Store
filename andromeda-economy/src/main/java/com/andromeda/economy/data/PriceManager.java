package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;

import java.io.*;
import java.util.*;

public class PriceManager {
    private static final File FILE = new File("config/andromeda-economy/prices.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Hardcoded anchor prices (THB). Values are ranked by rarity and community
     * economy standards. Blocks = 9× their base material; ores ≈ 0.9× material;
     * raw materials = 0.8× smelted ingot.
     */
    private static final Map<String, Double> ANCHORS;
    static {
        Map<String, Double> m = new LinkedHashMap<>();
        // ── Base materials ───────────────────────────────────────────────────
        m.put("minecraft:dirt",              1.0);
        m.put("minecraft:cobblestone",       10.0);
        m.put("minecraft:stone",             15.0);
        m.put("minecraft:coal",              500.0);
        m.put("minecraft:copper_ingot",      800.0);
        m.put("minecraft:iron_ingot",        2_000.0);
        m.put("minecraft:gold_ingot",        60_000.0);
        m.put("minecraft:gold_nugget",        6_700.0);   // ≈ ingot/9
        m.put("minecraft:lapis_lazuli",      3_000.0);
        m.put("minecraft:redstone",          1_000.0);
        m.put("minecraft:quartz",            5_000.0);  // 4 → 20K block
        m.put("minecraft:amethyst_shard",    2_000.0);
        m.put("minecraft:diamond",           100_000.0);
        m.put("minecraft:emerald",           80_000.0);
        m.put("minecraft:netherite_ingot",   1_000_000.0);
        m.put("minecraft:netherite_scrap",   235_000.0); // (ingot−4×gold)/4
        m.put("minecraft:ancient_debris",    235_000.0); // 1:1 → scrap
        m.put("minecraft:elytra",           10_000_000.0);
        m.put("minecraft:spawner",          40_000_000.0);
        m.put("minecraft:dragon_egg",        50_000_000.0);
        // ── Raw (pre-smelt) materials ────────────────────────────────────────
        m.put("minecraft:raw_iron",          1_600.0);
        m.put("minecraft:raw_gold",          60_000.0);
        m.put("minecraft:raw_gold_block",   540_000.0);  // 9× raw gold — prevents block→raw arbitrage
        m.put("minecraft:raw_iron_block",    18_000.0);  // 9× raw iron (same logic)
        m.put("minecraft:raw_copper_block",   5_760.0);  // 9× raw copper
        m.put("minecraft:raw_copper",        640.0);
        // ── Material blocks (9× ingot, quartz 4×, amethyst 9×) ──────────────
        m.put("minecraft:coal_block",        4_500.0);
        m.put("minecraft:copper_block",      7_200.0);
        m.put("minecraft:iron_block",        18_000.0);
        m.put("minecraft:gold_block",        540_000.0);
        m.put("minecraft:lapis_block",       27_000.0);
        m.put("minecraft:redstone_block",    9_000.0);
        m.put("minecraft:quartz_block",          20_000.0);
        m.put("minecraft:quartz_pillar",         20_000.0);
        m.put("minecraft:chiseled_quartz_block", 20_000.0);
        m.put("minecraft:quartz_bricks",         20_000.0);
        m.put("minecraft:smooth_quartz",         20_000.0);
        m.put("minecraft:amethyst_block",    18_000.0);
        m.put("minecraft:diamond_block",     900_000.0);
        m.put("minecraft:emerald_block",     720_000.0);
        m.put("minecraft:netherite_block",   9_000_000.0);
        // ── Ores (≈ 0.9× material; deepslate +5% for depth) ─────────────────
        m.put("minecraft:coal_ore",                500.0);
        m.put("minecraft:deepslate_coal_ore",      530.0);
        m.put("minecraft:copper_ore",              720.0);
        m.put("minecraft:deepslate_copper_ore",    760.0);
        m.put("minecraft:iron_ore",                1_800.0);
        m.put("minecraft:deepslate_iron_ore",      1_900.0);
        m.put("minecraft:gold_ore",                60_000.0);
        m.put("minecraft:deepslate_gold_ore",      63_000.0);
        m.put("minecraft:nether_gold_ore",        24_000.0);
        m.put("minecraft:lapis_ore",               5_000.0);
        m.put("minecraft:deepslate_lapis_ore",     5_500.0);
        m.put("minecraft:redstone_ore",            3_000.0);
        m.put("minecraft:deepslate_redstone_ore",  3_500.0);
        m.put("minecraft:nether_quartz_ore",       1_800.0);
        m.put("minecraft:emerald_ore",             72_000.0);
        m.put("minecraft:deepslate_emerald_ore",   76_000.0);
        m.put("minecraft:diamond_ore",             90_000.0);
        m.put("minecraft:deepslate_diamond_ore",   95_000.0);
        // ancient_debris is mined directly (no separate "ore" item)
        // ── End-game & special items ─────────────────────────────────────────
        m.put("minecraft:blaze_rod",               50_000.0);
        m.put("minecraft:blaze_powder",            25_000.0);
        m.put("minecraft:ghast_tear",              80_000.0);
        m.put("minecraft:magma_cream",             10_000.0);
        m.put("minecraft:slimeball",               2_000.0);
        m.put("minecraft:ender_pearl",             10_000.0);
        m.put("minecraft:ender_eye",               35_000.0);
        m.put("minecraft:end_crystal",             100_000.0);
        m.put("minecraft:prismarine_shard",        5_000.0);
        m.put("minecraft:prismarine_crystals",     3_000.0);
        m.put("minecraft:sponge",                  50_000.0);
        m.put("minecraft:phantom_membrane",        20_000.0);
        m.put("minecraft:turtle_scute",            30_000.0);
        m.put("minecraft:armadillo_scute",         20_000.0);
        m.put("minecraft:rabbit_foot",             15_000.0);
        m.put("minecraft:spider_eye",              1_000.0);
        m.put("minecraft:fermented_spider_eye",    5_000.0);
        m.put("minecraft:wither_skeleton_skull",   500_000.0);
        m.put("minecraft:chorus_fruit",            5_000.0);
        m.put("minecraft:shulker_shell",           200_000.0);
        m.put("minecraft:echo_shard",              200_000.0);
        m.put("minecraft:nautilus_shell",          100_000.0);
        m.put("minecraft:heart_of_the_sea",        500_000.0);
        m.put("minecraft:trident",                 1_000_000.0);
        m.put("minecraft:mace",                    2_000_000.0);
        m.put("minecraft:heavy_core",              1_500_000.0); // main Mace component
        m.put("minecraft:breeze_rod",                150_000.0); // 3 needed for Mace
        m.put("minecraft:trial_spawner",           3_000_000.0);
        m.put("minecraft:reinforced_deepslate",       35_000.0);
        m.put("minecraft:totem_of_undying",          100_000.0);
        m.put("minecraft:nether_star",             5_000_000.0);
        m.put("minecraft:light",                 100_000_000.0); // admin-only light block
        m.put("minecraft:wet_sponge",               50_000.0);  // = dry sponge — no furnace arbitrage
        // ── Misc / commonly misclassified ────────────────────────────────────
        m.put("minecraft:string",                    500.0);
        m.put("minecraft:feather",                   200.0);
        m.put("minecraft:gunpowder",               5_000.0);
        m.put("minecraft:bone",                      300.0);
        m.put("minecraft:bone_meal",                 100.0);
        m.put("minecraft:rotten_flesh",               50.0);
        m.put("minecraft:arrow",                     100.0);
        m.put("minecraft:bow",                    10_000.0);
        m.put("minecraft:crossbow",               15_000.0);
        m.put("minecraft:shield",                  5_000.0);
        m.put("minecraft:saddle",                 20_000.0);
        m.put("minecraft:name_tag",               30_000.0);
        m.put("minecraft:lead",                    5_000.0);
        m.put("minecraft:honeycomb",               2_000.0);
        m.put("minecraft:honey_bottle",            1_000.0);
        m.put("minecraft:ink_sac",                   300.0);
        m.put("minecraft:glow_ink_sac",            5_000.0);
        m.put("minecraft:sugar",                     200.0);
        m.put("minecraft:egg",                        50.0);
        m.put("minecraft:wheat",                     300.0);
        m.put("minecraft:wheat_seeds",                50.0);
        m.put("minecraft:apple",                     500.0);
        m.put("minecraft:golden_apple",           50_000.0);
        m.put("minecraft:enchanted_golden_apple",500_000.0);
        m.put("minecraft:book",                    1_000.0);
        m.put("minecraft:paper",                     100.0);
        m.put("minecraft:glass_bottle",              200.0);
        m.put("minecraft:brewing_stand",          20_000.0);
        m.put("minecraft:cauldron",                5_000.0);
        m.put("minecraft:fishing_rod",             3_000.0);
        m.put("minecraft:flint_and_steel",         2_000.0);
        m.put("minecraft:compass",                 5_000.0);
        m.put("minecraft:clock",                   5_000.0);
        m.put("minecraft:map",                     1_000.0);
        m.put("minecraft:sweet_berries",             200.0);
        m.put("minecraft:glow_berries",              500.0);
        m.put("minecraft:chorus_fruit",            5_000.0);
        m.put("minecraft:disc_fragment_5",        50_000.0);
        m.put("minecraft:music_disc_13",         100_000.0);
        m.put("minecraft:music_disc_cat",        100_000.0);
        m.put("minecraft:music_disc_blocks",     100_000.0);
        m.put("minecraft:music_disc_chirp",      100_000.0);
        m.put("minecraft:music_disc_far",        100_000.0);
        m.put("minecraft:music_disc_mall",       100_000.0);
        m.put("minecraft:music_disc_mellohi",    100_000.0);
        m.put("minecraft:music_disc_stal",       100_000.0);
        m.put("minecraft:music_disc_strad",      100_000.0);
        m.put("minecraft:music_disc_ward",       100_000.0);
        m.put("minecraft:music_disc_11",         200_000.0);
        m.put("minecraft:music_disc_wait",       200_000.0);
        m.put("minecraft:music_disc_otherside",  500_000.0);
        m.put("minecraft:music_disc_5",          500_000.0);
        m.put("minecraft:music_disc_pigstep",    500_000.0);
        m.put("minecraft:music_disc_relic",      500_000.0);
        m.put("minecraft:music_disc_creator",    500_000.0);
        m.put("minecraft:music_disc_creator_music_box", 500_000.0);
        m.put("minecraft:music_disc_precipice",  500_000.0);
        // firework_rocket handled by duration variants below; firework_star stays
        m.put("minecraft:firework_star",           1_000.0);
        // ── Logs (fixed at 2 K to prevent plank-arbitrage) ──────────────────
        // All overworld log variants
        for (String wood : new String[]{"oak","spruce","birch","jungle","acacia","dark_oak",
                                         "cherry","mangrove","pale_oak"}) {
            m.put("minecraft:" + wood + "_log",     2_000.0);
            m.put("minecraft:" + wood + "_wood",    2_000.0);
            m.put("minecraft:stripped_" + wood + "_log",  1_800.0);
            m.put("minecraft:stripped_" + wood + "_wood", 1_800.0);
        }
        m.put("minecraft:crimson_stem",            2_000.0);
        m.put("minecraft:warped_stem",             2_000.0);
        m.put("minecraft:stripped_crimson_stem",   1_800.0);
        m.put("minecraft:stripped_warped_stem",    1_800.0);
        m.put("minecraft:bamboo_block",            1_500.0);
        ANCHORS = Collections.unmodifiableMap(m);
    }

    /** Creative-mode / admin-only items that must not appear in the shop. */
    private static final Set<String> BLOCKED = Set.of(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:command_block", "minecraft:chain_command_block",
        "minecraft:repeating_command_block", "minecraft:command_block_minecart",
        "minecraft:bedrock", "minecraft:barrier", "minecraft:structure_block",
        "minecraft:structure_void", "minecraft:jigsaw", "minecraft:light",
        "minecraft:debug_stick", "minecraft:knowledge_book",
        "minecraft:petrified_oak_slab", "minecraft:moving_piston",
        "minecraft:end_portal_frame", // creative-only
        "minecraft:bundle",           // unobtainable in survival in most versions
        "minecraft:firework_rocket"   // replaced by duration-specific shop entries
    );

    private final Map<String, PriceEntry> prices = new LinkedHashMap<>();

    public PriceManager() {
        if (!FILE.exists()) generate(); else loadFile();
    }

    private void generate() {
        FILE.getParentFile().mkdirs();
        JsonObject root = new JsonObject();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            String key = id.toString();
            if (BLOCKED.contains(key)) continue; // skip creative-only items
            double price = computePrice(id);
            List<String> tags = buildTags(id);
            addEntry(root, key, price, tags);
        }
        // Firework rockets with explicit flight durations (1 = short, 3 = elytra-boost)
        addEntry(root, "firework_rocket:1",  2_000.0,  List.of("firework", "rocket", "duration", "1"));
        addEntry(root, "firework_rocket:2",  5_000.0,  List.of("firework", "rocket", "duration", "2"));
        addEntry(root, "firework_rocket:3", 10_000.0,  List.of("firework", "rocket", "duration", "3"));

        try (Writer w = new FileWriter(FILE)) { GSON.toJson(root, w); }
        catch (IOException e) { AndromedaEconomy.LOGGER.error("Failed to write prices.json", e); }
    }

    private void loadFile() {
        try (Reader r = new FileReader(FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) { generate(); return; }
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject obj = e.getValue().getAsJsonObject();
                double price = obj.get("price").getAsDouble();
                List<String> tags = new ArrayList<>();
                obj.get("tags").getAsJsonArray().forEach(t -> tags.add(t.getAsString()));
                prices.put(e.getKey(), new PriceEntry(price, tags));
            }
        } catch (IOException e) { AndromedaEconomy.LOGGER.error("Failed to read prices.json", e); }
    }

    private void addEntry(JsonObject root, String key, double price, List<String> tags) {
        prices.put(key, new PriceEntry(price, tags));
        JsonObject obj = new JsonObject();
        obj.addProperty("price", price);
        JsonArray arr = new JsonArray();
        tags.forEach(arr::add);
        obj.add("tags", arr);
        root.add(key, obj);
    }

    // -------------------------------------------------------------------------

    private double computePrice(Identifier id) {
        String key = id.toString();
        if (ANCHORS.containsKey(key)) return ANCHORS.get(key);
        return tierPrice(id.getPath(), id.getNamespace());
    }

    private static final Random RNG = new Random(42);

    private double tierPrice(String path, String ns) {
        if (path.endsWith("_spawn_egg"))                              return 1_000_000.0;
        if (path.startsWith("netherite_") && isToolOrArmour(path))   return rng(3_000_000, 10_000_000);
        if (path.startsWith("diamond_")   && isToolOrArmour(path))   return rng(300_000,     500_000);
        if (contains(path, "redstone", "quartz"))                     return rng(20_000,      100_000);
        if (contains(path, "copper", "lapis"))                        return rng(2_000,        10_000);
        if (contains(path, "gravel", "sand", "flint"))                return rng(10,               50);
        // Logs are anchored separately to prevent plank-arbitrage; catch remaining wood items here
        if (path.endsWith("_planks"))                                 return rng(400,           600);
        if (contains(path, "wood", "log", "stone", "leather"))        return rng(500,          2_000);
        if (!"minecraft".equals(ns)) return 5_000.0;
        return rng(1_000, 5_000);
    }

    // -------------------------------------------------------------------------

    /** Adds all enchanted books (per-level) to the shop on server start. */
    public void addEnchantedBooks(MinecraftServer server) {
        Registry<Enchantment> reg = server.registryAccess()
            .lookup(Registries.ENCHANTMENT).orElse(null);
        if (reg == null) return;

        JsonObject root = loadRoot();
        boolean dirty = false;

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : reg.entrySet()) {
            ResourceKey<Enchantment> key = entry.getKey();
            Enchantment ench = entry.getValue();
            Identifier enchId = key.identifier();
            double maxPrice = enchantmentPrice(ench.getWeight());
            int maxLevel = ench.getMaxLevel();

            for (int lvl = 1; lvl <= maxLevel; lvl++) {
                // key format: "enchanted_book:<namespace>:<path>:<level>"
                String shopId = "enchanted_book:" + enchId + ":" + lvl;
                if (prices.containsKey(shopId)) continue;

                // Each level below max costs 50 % less than the level above
                double price = maxPrice * Math.pow(0.5, maxLevel - lvl);
                List<String> tags = new ArrayList<>(Arrays.asList(
                    "enchanted", "book", "enchantment",
                    enchId.getPath(), EconomyUtils.toRoman(lvl).toLowerCase()
                ));
                for (String w : enchId.getPath().split("_")) tags.add(w);
                addEntry(root, shopId, price, tags);
                dirty = true;
            }
        }

        if (dirty) saveRoot(root);
    }

    /** Adds all drinkable, splash, and lingering potions to the shop on server start. */
    public void addPotions(MinecraftServer server) {
        Registry<Potion> reg = server.registryAccess()
            .lookup(Registries.POTION).orElse(null);
        if (reg == null) return;

        JsonObject root = loadRoot();
        boolean dirty = false;

        // prefix → item type tag → price multiplier
        record PotionVariant(String prefix, String typeTag, double multiplier) {}
        List<PotionVariant> variants = List.of(
            new PotionVariant("potion",          "drinkable", 1.0),
            new PotionVariant("splash_potion",   "splash",    1.2),
            new PotionVariant("lingering_potion", "lingering", 1.5)
        );

        for (Map.Entry<ResourceKey<Potion>, Potion> entry : reg.entrySet()) {
            ResourceKey<Potion> key = entry.getKey();
            Identifier potionId = key.identifier();
            String path = potionId.getPath();

            double basePrice;
            if (path.equals("water") || path.equals("mundane") || path.equals("thick") || path.equals("awkward"))
                basePrice = 500.0;
            else if (path.startsWith("strong_")) basePrice = 8_000.0;
            else if (path.startsWith("long_"))   basePrice = 6_000.0;
            else                                  basePrice = 4_000.0;

            for (PotionVariant v : variants) {
                String shopId = v.prefix() + ":" + potionId;
                if (prices.containsKey(shopId)) continue;

                double price = basePrice * v.multiplier();
                List<String> tags = new ArrayList<>(Arrays.asList("potion", v.typeTag(), path));
                for (String w : path.split("_")) tags.add(w);
                // Common search aliases so players can find potions by popular name
                if (path.contains("swiftness"))    { tags.add("speed"); tags.add("swift"); }
                if (path.contains("strength"))       tags.add("power");
                if (path.contains("leaping"))      { tags.add("jump"); tags.add("leap"); }
                if (path.contains("healing"))      { tags.add("heal"); tags.add("health"); }
                if (path.contains("harming"))        tags.add("damage");
                if (path.contains("slowness"))       tags.add("slow");
                if (path.contains("invisibility")) { tags.add("invis"); tags.add("invisible"); }
                if (path.contains("regeneration"))   tags.add("regen");
                if (path.contains("fire_resistance")) tags.add("fire");
                if (path.contains("night_vision"))    tags.add("night");
                if (path.contains("water_breathing")) tags.add("water");
                addEntry(root, shopId, price, tags);
                dirty = true;
            }
        }

        if (dirty) saveRoot(root);
    }

    private JsonObject loadRoot() {
        if (FILE.exists()) {
            try (Reader r = new FileReader(FILE)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                if (root != null) return root;
            } catch (IOException ignored) {}
        }
        return new JsonObject();
    }

    private void saveRoot(JsonObject root) {
        try (Writer w = new FileWriter(FILE)) { GSON.toJson(root, w); }
        catch (IOException e) { AndromedaEconomy.LOGGER.error("Failed to save prices.json", e); }
    }

    // -------------------------------------------------------------------------

    private double enchantmentPrice(int weight) {
        if (weight >= 10) return rng(50_000,      200_000);
        if (weight >= 5)  return rng(200_000,    1_000_000);
        if (weight >= 2)  return rng(1_000_000,  5_000_000);
        return rng(5_000_000, 20_000_000);
    }

    private static boolean isToolOrArmour(String path) {
        return path.endsWith("_sword") || path.endsWith("_pickaxe") || path.endsWith("_axe")
            || path.endsWith("_shovel") || path.endsWith("_hoe")
            || path.endsWith("_helmet") || path.endsWith("_chestplate")
            || path.endsWith("_leggings") || path.endsWith("_boots");
    }

    private static boolean contains(String path, String... kw) {
        for (String k : kw) if (path.contains(k)) return true;
        return false;
    }

    private double rng(int min, int max) { return min + RNG.nextDouble() * (max - min); }

    private List<String> buildTags(Identifier id) {
        List<String> tags = new ArrayList<>(Arrays.asList(id.getPath().split("_")));
        if (!"minecraft".equals(id.getNamespace())) tags.add(id.getNamespace());
        tags.removeIf(String::isEmpty);
        return tags;
    }

    // -------------------------------------------------------------------------

    public PriceEntry getEntry(String itemId) { return prices.get(itemId); }

    public double getBuyPrice(String itemId) {
        PriceEntry e = prices.get(itemId);
        return e != null ? e.price : -1;
    }

    /**
     * Items that sell at full buy price (no 15 % discount).
     * Gold: fixed at 60 K, no discount by design.
     * Bitcoin (command_block): market price, always buy = sell.
     */
    private static final Set<String> NO_DISCOUNT = Set.of(
        "minecraft:gold_ingot", "minecraft:gold_block", "minecraft:gold_nugget",
        "minecraft:raw_gold", "minecraft:gold_ore",
        "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
        "minecraft:command_block"   // Bitcoin — market rate, no spread
    );

    /**
     * Returns the sell price for an actual ItemStack, handling special cases
     * (e.g. firework rockets priced by flight duration).
     */
    public double getSellPriceForStack(ItemStack stack) {
        // Firework rockets: sell price depends on flight duration
        if (stack.getItem() == Items.FIREWORK_ROCKET) {
            Fireworks fw = stack.get(DataComponents.FIREWORKS);
            if (fw != null) {
                double p = getSellPrice("firework_rocket:" + fw.flightDuration());
                if (p > 0) return p;
            }
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? getSellPrice(id.toString()) : -1;
    }

    public double getSellPrice(String itemId) {
        double buy = getBuyPrice(itemId);
        if (buy <= 0) return -1;
        return NO_DISCOUNT.contains(itemId) ? buy : buy * 0.85;
    }

    private static final List<String> BITCOIN_TAGS = List.of("bitcoin", "btc", "crypto", "command", "block");

    /** Seeds the in-memory Bitcoin entry on startup (before first API call). */
    public void initBitcoin(double initialPrice) {
        prices.put("bitcoin",                    new PriceEntry(initialPrice, BITCOIN_TAGS));
        prices.put("minecraft:command_block",    new PriceEntry(initialPrice, BITCOIN_TAGS));
    }

    /** Called by BitcoinPriceService every 15 min with the live market price. */
    public void updateBitcoinPrice(double price) {
        prices.put("bitcoin",                    new PriceEntry(price, BITCOIN_TAGS));
        prices.put("minecraft:command_block",    new PriceEntry(price, BITCOIN_TAGS));
    }

    /** Returns a search-friendly display name for any shopId. */
    private static String searchableName(String key) {
        if (key.equals("bitcoin")) return "bitcoin btc crypto";
        if (key.startsWith("firework_rocket:")) {
            String dur = key.substring("firework_rocket:".length());
            return "firework rocket duration " + dur;
        }
        if (key.startsWith("enchanted_book:")) {
            // "enchanted_book:minecraft:protection:4" -> "protection 4" or "protection iv"
            int lastColon = key.lastIndexOf(':');
            String level = key.substring(lastColon + 1);
            String enchPath = key.substring("enchanted_book:".length(), lastColon);
            // enchPath = "minecraft:protection"
            String path = enchPath.contains(":") ? enchPath.split(":")[1] : enchPath;
            return ("enchanted book " + path + " " + level).replace("_", " ");
        }
        if (key.startsWith("lingering_potion:")) {
            String path = key.substring(key.lastIndexOf(':') + 1);
            return ("lingering potion " + path).replace("_", " ");
        }
        if (key.startsWith("splash_potion:")) {
            String path = key.substring(key.lastIndexOf(':') + 1);
            return ("splash potion " + path).replace("_", " ");
        }
        if (key.startsWith("potion:")) {
            String rest = key.substring("potion:".length()); // "minecraft:healing"
            String path = rest.contains(":") ? rest.split(":")[1] : rest;
            return ("potion " + path).replace("_", " ");
        }
        return EconomyUtils.toDisplayName(key).toLowerCase(Locale.ROOT);
    }

    public List<Map.Entry<String, PriceEntry>> search(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, PriceEntry>> result = new ArrayList<>();

        // Name search (searchable display name) — skip items hidden from shop
        for (Map.Entry<String, PriceEntry> e : prices.entrySet()) {
            if (BLOCKED.contains(e.getKey())) continue;
            if (searchableName(e.getKey()).contains(lower)) result.add(e);
        }
        if (!result.isEmpty()) {
            result.sort(Comparator.comparing(e -> searchableName(e.getKey())));
            return result;
        }

        // Tag fallback
        String[] words = lower.split("\\s+");
        for (Map.Entry<String, PriceEntry> e : prices.entrySet()) {
            if (BLOCKED.contains(e.getKey())) continue;
            for (String w : words) {
                if (e.getValue().tags.contains(w)) { result.add(e); break; }
            }
        }
        result.sort(Comparator.comparing(e -> searchableName(e.getKey())));
        return result;
    }

    public List<Map.Entry<String, PriceEntry>> getAll() {
        List<Map.Entry<String, PriceEntry>> list = new ArrayList<>();
        for (Map.Entry<String, PriceEntry> e : prices.entrySet()) {
            if (!BLOCKED.contains(e.getKey())) list.add(e);
        }
        list.sort(Comparator.comparing(e -> searchableName(e.getKey())));
        return list;
    }

    public static final class PriceEntry {
        public final double price;
        public final List<String> tags;
        PriceEntry(double price, List<String> tags) {
            this.price = price;
            this.tags  = List.copyOf(tags);
        }
    }
}
