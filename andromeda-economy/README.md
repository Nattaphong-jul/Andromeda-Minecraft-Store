# Andromeda Economy

A Fabric economy mod for Minecraft **26.1.2** — server-side only by default, with optional client-side installation for enhanced price tooltips and perfect item stacking.

---

## Features

| Feature | Description |
|---|---|
| **Shop** | Paginated 54-slot GUI with search, buy prices, and quantity selector |
| **Sell** | 27-slot GUI — drop items in, confirm to sell at 85% of buy price |
| **Balance** | Check your own or any player's THB balance |
| **Pay** | Transfer THB between players; supports `k`/`m`/`b`/`t` suffixes |
| **HUD** | Per-player sidebar showing balance, kills, and ping |
| **Mob rewards** | Earn THB for killing mobs (configurable) |
| **Night Vision** | Toggle infinite night vision |
| **Prices** | Auto-generated for all vanilla items, enchanted books (per level), potions, splash & lingering potions |

---

## Commands

| Command | Description |
|---|---|
| `/shop [search]` / `/sh [search]` | Open the shop (optionally filtered by search term) |
| `/sell` | Open the sell GUI |
| `/balance [player]` | Show your or another player's balance |
| `/pay <player> <amount>` | Send THB to another player (e.g. `/pay Steve 10m`) |
| `/nightvision` / `/nv` | Toggle infinite night vision |

### Pay amount formats
`/pay Steve 500` · `/pay Steve 10k` · `/pay Steve 2.5m` · `/pay Steve 1b` · `/pay Steve 0.5t`

---

## Installation

### Server-only (no client mod required)
Drop `andromeda-economy-1.0.1+26.1.2.jar` into your server's `mods/` folder. Clients connect with vanilla Minecraft — no extra mods needed.

**Price display:** Item lore shows the per-unit sell price (`100K THB`). Items of the same type always share identical lore and stack correctly.

### Hybrid (server + client)
Install the **same jar** on both the server and each client's `mods/` folder.

**Enhanced price display:** The mod detects the client installation automatically. Instead of modifying item data (which prevents stacking), prices are sent via a custom packet and rendered dynamically in the tooltip:
- Items stay completely clean — **perfect stacking** at all times
- Tooltip shows the **total stack value** (`6.4M THB` for 64 diamonds), updating instantly as stack size changes

---

## Configuration

All config files are generated automatically on first server start inside `config/andromeda-economy/`.

### `prices.json`
Contains buy prices for every item. Edit freely — the file is read on startup.

```json
"minecraft:diamond": {
  "price": 100000.0,
  "tags": ["diamond", "gem"]
}
```

To regenerate with updated default prices (after a mod update), delete `prices.json` and restart.

### `mob_rewards.json`
THB earned per mob kill.

```json
"minecraft:zombie": 100.0,
"minecraft:ender_dragon": 500000.0
```

---

## Default Price Tiers

| Category | Examples | Price |
|---|---|---|
| Dirt / Cobblestone | Dirt, Stone | 1 – 15 THB |
| Common | Wood, Leather, Coal | 200 – 2,000 THB |
| Uncommon | Copper, Lapis | 2,000 – 10,000 THB |
| Rare | Redstone, Gold | 10,000 – 100,000 THB |
| Valuable | Diamond, Emerald | 80,000 – 100,000 THB |
| End-game | Netherite, Elytra, Trident | 1,000,000 – 2,000,000 THB |
| Ultra-rare | Dragon Egg, Nether Star | 5,000,000 – 50,000,000 THB |
| Spawn Eggs | All types | 1,000,000 THB |
| Enchanted Books | Per enchantment + level | 50,000 – 20,000,000 THB |
| Potions | Per effect + type | 4,000 – 8,000 THB |

Material blocks are priced at 9× their ingot. Ores are ~90% of the material price.

---

## Requirements

- **Minecraft** 26.1.2
- **Fabric Loader** 0.18.4+
- **Fabric API** 0.149.0+26.1.2
- **Java** 21+

---

## Building from Source

```bash
git clone https://github.com/Nattaphong-jul/Andromeda-Minecraft-Store.git
cd Andromeda-Minecraft-Store/andromeda-economy
./gradlew shadowJar
# Output: build/libs/andromeda-economy-1.0.1+26.1.2.jar
```
