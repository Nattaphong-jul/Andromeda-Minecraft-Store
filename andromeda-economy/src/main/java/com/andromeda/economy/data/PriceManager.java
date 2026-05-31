package com.andromeda.economy.data;

import com.andromeda.economy.AndromedaEconomy;
import com.andromeda.economy.EconomyUtils;
import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
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
        m.put("minecraft:breeze_rod",                  3_000.0); // dropped by Breeze
        m.put("minecraft:trial_spawner",           3_000_000.0);
        m.put("minecraft:reinforced_deepslate",       35_000.0);
        m.put("minecraft:totem_of_undying",          100_000.0);
        m.put("minecraft:nether_star",             5_000_000.0);
        m.put("minecraft:beacon",                  5_000_000.0); // main component is Nether Star
        m.put("minecraft:light",                       1_000.0); // placeable light source
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
        m.put("minecraft:sugar",                   5_000.0); // slightly more than sugarcane
        m.put("minecraft:sugar_cane",              4_000.0);
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
        // ── Spawn eggs (tiered by mob difficulty) ────────────────────────────
        // Common overworld
        m.put("minecraft:zombie_spawn_egg",              100_000.0);
        m.put("minecraft:husk_spawn_egg",                100_000.0);
        m.put("minecraft:drowned_spawn_egg",             150_000.0);
        m.put("minecraft:zombie_villager_spawn_egg",     100_000.0);
        m.put("minecraft:skeleton_spawn_egg",            100_000.0);
        m.put("minecraft:stray_spawn_egg",               150_000.0);
        m.put("minecraft:bogged_spawn_egg",              150_000.0);
        m.put("minecraft:spider_spawn_egg",              100_000.0);
        m.put("minecraft:cave_spider_spawn_egg",         200_000.0);
        m.put("minecraft:creeper_spawn_egg",             200_000.0);
        m.put("minecraft:witch_spawn_egg",               300_000.0);
        m.put("minecraft:slime_spawn_egg",               200_000.0);
        m.put("minecraft:silverfish_spawn_egg",           50_000.0);
        m.put("minecraft:endermite_spawn_egg",            50_000.0);
        m.put("minecraft:phantom_spawn_egg",             500_000.0);
        // Mid-tier overworld / end
        m.put("minecraft:enderman_spawn_egg",          1_000_000.0);
        m.put("minecraft:guardian_spawn_egg",          1_000_000.0);
        m.put("minecraft:elder_guardian_spawn_egg",   10_000_000.0);
        m.put("minecraft:shulker_spawn_egg",           5_000_000.0);
        m.put("minecraft:pillager_spawn_egg",            500_000.0);
        m.put("minecraft:vindicator_spawn_egg",          500_000.0);
        m.put("minecraft:evoker_spawn_egg",            3_000_000.0);
        m.put("minecraft:vex_spawn_egg",                 300_000.0);
        m.put("minecraft:ravager_spawn_egg",           2_000_000.0);
        // Nether
        m.put("minecraft:blaze_spawn_egg",             2_000_000.0);
        m.put("minecraft:wither_skeleton_spawn_egg",   5_000_000.0);
        m.put("minecraft:ghast_spawn_egg",             2_000_000.0);
        m.put("minecraft:magma_cube_spawn_egg",          300_000.0);
        m.put("minecraft:piglin_spawn_egg",              200_000.0);
        m.put("minecraft:piglin_brute_spawn_egg",        500_000.0);
        m.put("minecraft:zombified_piglin_spawn_egg",    200_000.0);
        m.put("minecraft:hoglin_spawn_egg",              300_000.0);
        m.put("minecraft:zoglin_spawn_egg",              500_000.0);
        // Trial chambers / new
        m.put("minecraft:breeze_spawn_egg",            1_000_000.0);
        m.put("minecraft:creaking_spawn_egg",          3_000_000.0);
        // Bosses — priced so farming is barely profitable after many kills
        m.put("minecraft:wither_spawn_egg",           50_000_000.0); // nether star 5M/kill → ~10 kills to break even
        m.put("minecraft:ender_dragon_spawn_egg",    100_000_000.0);
        m.put("minecraft:warden_spawn_egg",           30_000_000.0);
        m.put("minecraft:illusioner_spawn_egg",        5_000_000.0);
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
        // ── Bamboo products (bamboo = 4,600) ────────────────────────────────────
        // Anchoring bamboo explicitly because adding the product anchors below
        // shifts the RNG sequence, which would otherwise change bamboo's tier price.
        m.put("minecraft:bamboo",                  4_600.0);
        m.put("minecraft:stripped_bamboo",         4_600.0); // 1:1 with bamboo
        m.put("minecraft:bamboo_block",           41_400.0); // 9 bamboo
        m.put("minecraft:bamboo_planks",           2_300.0); // bamboo / 2
        m.put("minecraft:bamboo_mosaic",           2_300.0); // same material
        m.put("minecraft:bamboo_slab",             1_150.0); // plank / 2
        m.put("minecraft:bamboo_mosaic_slab",      1_150.0);
        m.put("minecraft:bamboo_stairs",           3_450.0); // plank × 1.5
        m.put("minecraft:bamboo_mosaic_stairs",    3_450.0);
        m.put("minecraft:bamboo_door",             4_600.0); // 6 planks → 3 doors
        m.put("minecraft:bamboo_trapdoor",         6_900.0); // 6 planks → 2 trapdoors
        m.put("minecraft:bamboo_fence",            3_000.0); // 4 planks + 2 sticks → 3 fences
        m.put("minecraft:bamboo_fence_gate",       9_200.0); // 4 planks → 1 gate
        m.put("minecraft:bamboo_button",           2_300.0); // 1 plank
        m.put("minecraft:bamboo_pressure_plate",   4_600.0); // 2 planks
        m.put("minecraft:bamboo_sign",             4_600.0); // 6 planks → 3 signs
        m.put("minecraft:bamboo_hanging_sign",     5_000.0);
        m.put("minecraft:bamboo_raft",             5_000.0);
        m.put("minecraft:bamboo_chest_raft",      10_000.0);
        ANCHORS = Collections.unmodifiableMap(m);
    }

    /** Creative-mode / admin-only items that must not appear in the shop. */
    private static final Set<String> BLOCKED = Set.of(
        "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:command_block", "minecraft:chain_command_block",
        "minecraft:repeating_command_block", "minecraft:command_block_minecart",
        "minecraft:bedrock", "minecraft:barrier", "minecraft:structure_block",
        "minecraft:structure_void", "minecraft:jigsaw",
        "minecraft:debug_stick", "minecraft:knowledge_book",
        "minecraft:petrified_oak_slab", "minecraft:moving_piston",
        "minecraft:end_portal_frame", // creative-only
        "minecraft:bundle",           // unobtainable in survival in most versions
        "minecraft:firework_rocket"   // replaced by duration-specific shop entries
    );

    private final Map<String, PriceEntry> prices = new LinkedHashMap<>();

    public PriceManager() {
        if (!FILE.exists()) generate(); else loadFile();
        ensureSpecialItems();
    }

    /** Ensures custom shop items that aren't in the Minecraft registry are always present. */
    private void ensureSpecialItems() {
        if (!prices.containsKey("ae:amethyst_pickaxe")) {
            JsonObject root = loadRoot();
            addEntry(root, "ae:amethyst_pickaxe", 100_000_000.0,
                List.of("amethyst", "pickaxe", "netherite", "9x9", "mining"));
            saveRoot(root);
        }
        if (!prices.containsKey("ae:speed_hopper")) {
            JsonObject root = loadRoot();
            addEntry(root, "ae:speed_hopper", 7_500.0,
                List.of("speed", "hopper", "fast", "transfer", "10"));
            saveRoot(root);
        }
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

    // ── BiomesOPlenty compat ──────────────────────────────────────────────────

    private static final Map<String, Double> BOP_ANCHORS = new HashMap<>();
    static {
        // Rose quartz (amethyst analogue)
        BOP_ANCHORS.put("rose_quartz_chunk",          2_000.0);
        BOP_ANCHORS.put("small_rose_quartz_bud",        500.0);
        BOP_ANCHORS.put("medium_rose_quartz_bud",     1_000.0);
        BOP_ANCHORS.put("large_rose_quartz_bud",      1_500.0);
        BOP_ANCHORS.put("rose_quartz_cluster",         5_000.0);
        BOP_ANCHORS.put("rose_quartz_block",          18_000.0);
        // Brimstone (nether material)
        BOP_ANCHORS.put("brimstone",                  10_000.0);
        BOP_ANCHORS.put("brimstone_bricks",           12_000.0);
        BOP_ANCHORS.put("brimstone_brick_slab",        6_000.0);
        BOP_ANCHORS.put("brimstone_brick_stairs",      9_000.0);
        BOP_ANCHORS.put("brimstone_brick_wall",        6_000.0);
        BOP_ANCHORS.put("brimstone_bud",               3_000.0);
        BOP_ANCHORS.put("brimstone_cluster",           8_000.0);
        BOP_ANCHORS.put("brimstone_fumarole",          5_000.0);
        BOP_ANCHORS.put("chiseled_brimstone_bricks",  12_000.0);
        // Rare mob/creepy drops
        BOP_ANCHORS.put("flesh",                      20_000.0);
        BOP_ANCHORS.put("porous_flesh",               15_000.0);
        BOP_ANCHORS.put("flesh_tendons",              15_000.0);
        BOP_ANCHORS.put("flesh_tendons_strand",       10_000.0);
        BOP_ANCHORS.put("hair",                        5_000.0);
        BOP_ANCHORS.put("spider_egg",                 20_000.0);
        BOP_ANCHORS.put("glowworm_silk",              10_000.0);
        BOP_ANCHORS.put("glowworm_silk_strand",        5_000.0);
        BOP_ANCHORS.put("pus_bubble",                  5_000.0);
        BOP_ANCHORS.put("webbing",                     5_000.0);
        BOP_ANCHORS.put("stringy_cobweb",              2_000.0);
        BOP_ANCHORS.put("hanging_cobweb",              2_000.0);
        BOP_ANCHORS.put("hanging_cobweb_strand",       1_000.0);
        // Rare creatures / overworld
        BOP_ANCHORS.put("eyebulb",                    15_000.0);
        BOP_ANCHORS.put("wispjelly",                  20_000.0);
        BOP_ANCHORS.put("lumaloop",                    5_000.0);
        BOP_ANCHORS.put("lumaloop_plant",              3_000.0);
        BOP_ANCHORS.put("enderphyte",                 10_000.0);
        BOP_ANCHORS.put("anomaly",                    50_000.0);
        BOP_ANCHORS.put("glowshroom",                  1_000.0);
        BOP_ANCHORS.put("glowshroom_block",            8_000.0);
        BOP_ANCHORS.put("glowing_moss_block",          2_000.0);
        BOP_ANCHORS.put("glowing_moss_carpet",           500.0);
        // Special blocks
        BOP_ANCHORS.put("null_block",                 30_000.0);
        BOP_ANCHORS.put("thermal_calcite",             5_000.0);
        BOP_ANCHORS.put("thermal_calcite_vent",        8_000.0);
        BOP_ANCHORS.put("origin_grass_block",          5_000.0);
        BOP_ANCHORS.put("dried_salt",                    500.0);
        // Fluids
        BOP_ANCHORS.put("blood_bucket",                5_000.0);
        BOP_ANCHORS.put("liquid_null_bucket",         10_000.0);
        // Music disc
        BOP_ANCHORS.put("music_disc_wanderer",       200_000.0);
        // Special glowing flowers
        BOP_ANCHORS.put("glowflower",                  1_000.0);
        BOP_ANCHORS.put("burning_blossom",             5_000.0);
        BOP_ANCHORS.put("endbloom",                    5_000.0);
        BOP_ANCHORS.put("icy_iris",                    1_000.0);
        BOP_ANCHORS.put("origin_rose",                 2_000.0);
    }

    /**
     * Scans the live item registry for all biomesoplenty:* items not yet in prices.json
     * and adds them with sensible prices. Safe to call on every server start — skips
     * items that already have a price entry.
     */
    public void addBiomesOPlentyItems(MinecraftServer server) {
        boolean bopLoaded = BuiltInRegistries.ITEM.keySet().stream()
            .anyMatch(id -> "biomesoplenty".equals(id.getNamespace()));
        if (!bopLoaded) return;

        JsonObject root = loadRoot();
        boolean dirty = false;

        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !"biomesoplenty".equals(id.getNamespace())) continue;
            String key = id.toString();
            if (prices.containsKey(key)) continue;

            double price = bopPrice(id.getPath());
            List<String> tags = buildTags(id);
            addEntry(root, key, price, tags);
            dirty = true;
        }

        if (dirty) {
            saveRoot(root);
            AndromedaEconomy.LOGGER.info("[AndromedaEconomy] Added BiomesOPlenty items to prices.json");
        }
    }

    private static double bopPrice(String path) {
        if (BOP_ANCHORS.containsKey(path)) return BOP_ANCHORS.get(path);
        // Stripped logs/wood (check before generic _log/_wood)
        if (path.startsWith("stripped_") && path.endsWith("_log"))  return 1_800.0;
        if (path.startsWith("stripped_") && path.endsWith("_wood")) return 1_800.0;
        // Standard wood set
        if (path.endsWith("_log") || path.endsWith("_wood"))        return 2_000.0;
        if (path.endsWith("_planks"))                               return 500.0;
        if (path.endsWith("_fence_gate"))                           return 2_000.0;
        if (path.endsWith("_fence"))                                return 600.0;
        if (path.endsWith("_slab"))                                 return 250.0;
        if (path.endsWith("_stairs"))                               return 750.0;
        if (path.endsWith("_door"))                                 return 1_000.0;
        if (path.endsWith("_trapdoor"))                             return 1_500.0;
        if (path.endsWith("_button"))                               return 100.0;
        if (path.endsWith("_pressure_plate"))                       return 200.0;
        if (path.endsWith("_hanging_sign"))                         return 1_000.0;
        if (path.endsWith("_sign"))                                 return 1_000.0;
        if (path.endsWith("_chest_boat"))                           return 4_000.0;
        if (path.endsWith("_boat"))                                 return 2_000.0;
        if (path.endsWith("_shelf"))                                return 1_500.0;
        if (path.endsWith("_leaves"))                               return 100.0;
        if (path.endsWith("_sapling"))                              return 500.0;
        if (path.endsWith("_leaf_litter"))                          return 500.0;
        // Flower petal blocks (crafted decoration)
        if (path.endsWith("_flower_petal_block"))                   return 2_000.0;
        // Potted plants (display items)
        if (path.startsWith("potted_"))                             return 500.0;
        // Sandstone variants
        if (path.contains("sandstone"))                             return 500.0;
        if (path.endsWith("_sand"))                                 return 50.0;
        // Glowing blocks
        if (path.startsWith("glowing_"))                            return 2_000.0;
        // Default — covers remaining flowers, grasses, misc plants
        return 2_000.0;
    }

    // ── More Sweet Treats compat ──────────────────────────────────────────────

    private static final Map<String, Double> MST_ANCHORS = Map.of(
        "apple_pie",            8_000.0,
        "honey_cake",           8_000.0,
        "choc_chip_muffin",     5_000.0,
        "beetroot_brownie",     5_000.0,
        "choc_honeycomb_cookie",4_000.0,
        "turkish_delight",      4_000.0,
        "caramel_slice",        3_000.0,
        "chocolate_bar",        3_000.0,
        "chocolate_ice_cream",  5_000.0,
        "chocolate_milkshake",  4_000.0
    );
    // second map because Map.of limit is 10 entries
    private static final Map<String, Double> MST_ANCHORS2 = Map.of(
        "candy_apple",          3_000.0,
        "caramel",              1_500.0,
        "caramel_candy",        2_000.0,
        "berry_candy",          2_000.0,
        "melon_candy",          2_000.0,
        "melon_lollipop",       2_500.0,
        "melon_smoothie",       3_000.0,
        "glow_berry_gummy",     3_000.0
    );

    public void addMoreSweetTreatsItems(MinecraftServer server) {
        boolean loaded = BuiltInRegistries.ITEM.keySet().stream()
            .anyMatch(id -> "more_sweet_treats".equals(id.getNamespace()));
        if (!loaded) return;

        JsonObject root = loadRoot();
        boolean dirty = false;

        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !"more_sweet_treats".equals(id.getNamespace())) continue;
            String key = id.toString();
            if (prices.containsKey(key)) continue;

            String path = id.getPath();
            double price = MST_ANCHORS.getOrDefault(path,
                           MST_ANCHORS2.getOrDefault(path, 2_000.0));
            List<String> tags = buildTags(id);
            addEntry(root, key, price, tags);
            dirty = true;
        }

        if (dirty) {
            saveRoot(root);
            AndromedaEconomy.LOGGER.info("[AndromedaEconomy] Added More Sweet Treats items to prices.json");
        }
    }

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

    /**
     * Returns the total sell value of a stack, including the sell value of any items
     * stored inside it (e.g. shulker box contents).  Count is already factored in.
     *
     *   Regular item: getSellPriceForStack(stack) * stack.getCount()
     *   Shulker box:  (shulker_price * count) + sum(item_price * item_count for each item inside)
     */
    public double getTotalSellValue(ItemStack stack) {
        double ownPrice = getSellPriceForStack(stack);
        double ownValue = ownPrice > 0 ? ownPrice * stack.getCount() : 0;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return ownValue;

        double contentsValue = contents.nonEmptyItemCopyStream()
            .mapToDouble(inner -> {
                double p = getSellPriceForStack(inner);
                return p > 0 ? p * inner.getCount() : 0;
            })
            .sum();
        return ownValue + contentsValue;
    }

    public double getSellPrice(String itemId) {
        double buy = getBuyPrice(itemId);
        if (buy <= 0) return -1;
        return NO_DISCOUNT.contains(itemId) ? buy : buy * 0.85;
    }

    // ── Rank-based selling ────────────────────────────────────────────────────

    /** All spawn eggs sell for this fixed price regardless of rank or buy price. */
    public static final double SPAWN_EGG_SELL_PRICE = 100_000.0;

    /** Returns the deduction rate (0–1) for the given rank. */
    public static double rankSellDeduction(String rank) {
        return switch (rank) {
            case "Unemployed", "Salary Man" -> 0.05;
            case "Anutin"       -> 0.15;
            case "CEO"          -> 0.30;
            case "MrBeast"      -> 0.50;
            case "CK"           -> 0.60;
            case "Jensen Huang" -> 0.70;
            case "Elon Musk"    -> 0.80;
            case "FED", "Cheater" -> 0.90;
            default -> 0.15;
        };
    }

    /**
     * Rank-adjusted sell price for a given item and player balance.
     * Spawn eggs always sell for SPAWN_EGG_SELL_PRICE (100K).
     * Gold/Bitcoin are unaffected (market price, no spread).
     */
    public double getSellPriceByRank(String itemId, double balance) {
        double buy = getBuyPrice(itemId);
        if (buy <= 0) return -1;
        if (itemId.endsWith("_spawn_egg")) return SPAWN_EGG_SELL_PRICE;
        if (NO_DISCOUNT.contains(itemId)) return buy;
        double deduction = rankSellDeduction(com.andromeda.economy.RankManager.rankName(balance));
        return buy * (1.0 - deduction);
    }

    public double getSellPriceForStackByRank(ItemStack stack, double balance) {
        if (stack.getItem() == Items.FIREWORK_ROCKET) {
            Fireworks fw = stack.get(DataComponents.FIREWORKS);
            if (fw != null) {
                double p = getSellPriceByRank("firework_rocket:" + fw.flightDuration(), balance);
                if (p > 0) return p;
            }
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? getSellPriceByRank(id.toString(), balance) : -1;
    }

    /** Rank-adjusted total sell value including shulker box contents. */
    public double getTotalSellValueByRank(ItemStack stack, double balance) {
        double ownPrice = getSellPriceForStackByRank(stack, balance);
        double ownValue = ownPrice > 0 ? ownPrice * stack.getCount() : 0;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return ownValue;

        double contentsValue = contents.nonEmptyItemCopyStream()
            .mapToDouble(inner -> {
                double p = getSellPriceForStackByRank(inner, balance);
                return p > 0 ? p * inner.getCount() : 0;
            })
            .sum();
        return ownValue + contentsValue;
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
