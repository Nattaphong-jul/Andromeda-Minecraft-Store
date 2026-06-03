# Andromeda Economy

A server-side Fabric economy mod for Minecraft **26.1.2**. Runs fully without any client mod — install the same jar on the client too for enhanced price tooltips.

> **Currency:** THB (Thai Baht)

---

## Table of Contents
- [Installation](#installation)
- [Rank System](#rank-system)
- [Shop](#shop)
- [Sell & Rank-Based Pricing](#sell--rank-based-pricing)
- [Balance & Pay](#balance--pay)
- [Bank / Asset Chest](#bank--asset-chest)
- [Company System](#company-system)
- [Lottery](#lottery)
- [Special Items](#special-items)
- [Mob Rewards & Death](#mob-rewards--death)
- [Admin Commands](#admin-commands)
- [OP Utility Commands](#op-utility-commands)
- [HUD Sidebar](#hud-sidebar)
- [Config Files](#config-files)
- [Mod Compatibility](#mod-compatibility)
- [Requirements](#requirements)

---

## Installation

### Server-only (recommended)
Drop the jar into the server's `mods/` folder. Clients connect with vanilla — no client mod needed.

> Item lore on non-client-mod players shows the sell price per item.

### Server + Client
Install the **same jar** on both server and client for enhanced tooltips:
- Prices are sent via packet on join (no item data modification)
- Tooltip shows **total stack value** (e.g. `6.4M THB` for 64 diamonds)
- Items stack perfectly at all times
- Sell price shown in tooltip automatically adjusts to your current rank

---

## Rank System

Rank is calculated from **total wealth** (balance + ender chest asset value).

| Rank | Threshold | Color |
|---|---|---|
| Unemployed | < 100K THB | White |
| Salary Man | 100K – 10M | White |
| Anutin | 10M – 100M | Blue |
| CEO | 100M – 1B | Dark Green |
| MrBeast | 1B – 10B | Aqua |
| CK | 10B – 100B | Light Purple |
| Jensen Huang | 100B – 500B | Green |
| Elon Musk | 500B – 1T | Gold |
| FED | 1T – 2T | Red |
| Cheater | ≥ 2T | Black |

Rank is displayed:
- As a **prefix above your head** (e.g. `CEO PlayerName`)
- In the **tab list** (e.g. `PlayerName [CEO]`)
- On the **sidebar HUD**

---

## Shop

```
/shop [search term]    (alias: /sh)
```

- 54-slot paginated GUI showing all buyable items
- Use `/shop diamond` to filter results by name or tag
- Click an item → quantity selector (×1 ×8 ×16 ×32 ×64)
- All vanilla items, enchanted books (per level), and potions are available
- Special items: Amethyst Pickaxe, Speed Hopper, Bitcoin (real-time price)
- Compatible mod items (BiomesOPlenty, More Sweet Treats) are auto-priced

### Price Tiers (defaults)

| Category | Examples | Buy Price |
|---|---|---|
| Common | Wood, Leather, Coal | ~500 – 2,000 |
| Uncommon | Copper, Iron | 2,000 – 10,000 |
| Gold Ingot | — | 60,000 |
| Diamond | — | 100,000 |
| Emerald | — | 80,000 |
| Netherite Ingot | — | 1,500,000 |
| Netherite Tools/Armor | Sword, Pickaxe, Boots… | 4,600,000 – 14,600,000 |
| Elytra | — | 10,000,000 |
| Nether Star | — | 5,000,000 |
| Wither Spawn Egg | — | 50,000,000 |
| Ender Dragon Spawn Egg | — | 100,000,000 |
| Amethyst Pickaxe | 3×3 mining, Fortune III | 100,000,000 |
| Speed Hopper | 10 items/cycle | 7,500 |

---

## Sell & Rank-Based Pricing

```
/sell
```

Opens a 54-slot sell GUI. Place items in and click **✔ Confirm Sale**.

### Sell Deduction by Rank

The higher your rank, the more is deducted from the sell price.

| Rank | Deduction | You receive |
|---|---|---|
| Unemployed / Salary Man | −5% | 95% of buy price |
| Anutin | −15% | 85% |
| CEO | −30% | 70% |
| MrBeast | −50% | 50% |
| CK | −60% | 40% |
| Jensen Huang | −70% | 30% |
| Elon Musk | −80% | 20% |
| FED / Cheater | −90% | 10% |

### Exceptions (always full price — no rank deduction)

| Item | Sell Price |
|---|---|
| **Gold Ingot** | 100% of buy price |
| **Gold Block** | 100% of buy price |
| **Bitcoin** (Command Block) | Live market price |
| **Any Spawn Egg** | Fixed **100,000 THB** |

> All other gold variants (Gold Nugget, Raw Gold, Gold Ore variants) are subject to rank deduction.

### Netherite Sell Lock
Buy prices for netherite were increased +50% in the Economy Balancing patch.
To prevent windfall for players who stocked before the patch, **netherite sell prices are calculated from pre-increase values** — the higher buy price does not increase what you earn when selling.

---

## Balance & Pay

```
/balance [player]    (alias: /bal)
/pay <player> <amount>
```

Amount supports shorthand: `500k` · `2.5m` · `1b` · `0.5t`

```
/pay Steve 100000
/pay Steve 500k
/pay Steve 2.5m
/pay Steve 1b
```

---

## Bank / Asset Chest

```
/bank    (alias: /asset)
```

Opens your **54-slot ender chest**. The total sell value of all items inside is tracked as your **asset value**, which counts toward your rank calculation.

> **Total Wealth = Balance + Ender Chest Asset Value**

---

## Company System

```
/company
```

Opens the company list GUI. Click a company skull to view and apply.

### Commands

| Command | Description |
|---|---|
| `/company create <name>` | Create a company (letters/numbers/underscores, max 16 chars) |
| `/company apply <name>` | Apply to join a company |
| `/company accept <player>` | Accept a pending application (owner only) |
| `/company reject <player>` | Reject a pending application (owner only) |
| `/company kick <player>` | Remove a member (owner only) |
| `/company leave` | Leave your company (non-owners only) |
| `/company transfer <player>` | Transfer ownership to a member |
| `/company disband` | Disband the company (owner only) |
| `/company info` | Show your company's members and total revenue |
| `/company top` | Top 10 companies by all-time revenue |

### Revenue Sharing
When a member earns money (selling, mob kills, etc.):
- The earner keeps **90%**
- The remaining **10%** is split equally among all other members
- Online members are notified in chat

---

## Lottery

```
/lottery    (alias: /lot)
```

- Rounds run every **24 real-time hours**
- Each round has a fixed pool of **36 numbers** (same for all players)
- Maximum **2 tickets** per player per round — ticket price: **100,000 THB**
- **Prizes:** 1st match: 200,000,000 · 2nd: 100,000,000 · 3rd: 50,000,000 THB

### How It Works
1. Open `/lottery` and click a number to buy a ticket
2. The draw fires automatically every 24 hours
3. Winners are notified in-game (even if offline when the draw fires)
4. Prizes are **claimable for 24 hours** after the draw via the **Redeem** button
5. A new round starts immediately after the draw

### Admin Commands

| Command | Description |
|---|---|
| `/lottery forcedraw` | Force the draw immediately |
| `/lottery status` | Show current round info and claim window |
| `/lottery viewresult` | View current winning numbers (private) |
| `/lottery setnumber <tier 1-3> <number>` | Override a prize number |

---

## Special Items

### Amethyst Pickaxe
**Price:** 100,000,000 THB — Search `/shop amethyst` or `/shop pickaxe`

- Enchanted Netherite Pickaxe (Efficiency V · Fortune III · Unbreaking III · Mending)
- Mines a **3×3 area** with every block break, perpendicular to your look direction
- Fortune III applies to **all 9 blocks** in the area
- **Purple particle preview** outlines the 3×3 while aiming
- Plays the amethyst block break sound when the area is cleared
- Breaking the pickaxe drops it intact with all enchantments
- Fully server-side — no client mod needed

### Speed Hopper
**Price:** 7,500 THB — Search `/shop speed` or `/shop hopper`

- Transfers **10 items per cycle** into the container below (vanilla = 1 per cycle)
- Also sucks in **10 items per cycle** from the container above
- Aqua name + enchanted glint distinguishes it from regular hoppers
- Breaking drops the Speed Hopper item on the ground
- Fully server-side — no client mod needed

---

## Mob Rewards & Death

### Mob Kill Rewards
Earn THB for killing mobs. Examples:

| Mob | Reward |
|---|---|
| Zombie / Skeleton / Spider | 1,000 |
| Creeper / Witch | 3,000 |
| Phantom | 5,000 |
| Enderman | 5,000 |
| Blaze / Ghast / Wither Skeleton | 10,000 |
| Shulker | 20,000 |
| Evoker | 25,000 |
| Ravager | 30,000 |
| Elder Guardian | 80,000 |
| Creaking | 50,000 |
| Warden | 800,000 |
| Wither | 500,000 |
| Ender Dragon | 2,000,000 |

> **Penalty mobs:** Allay −10,000 THB · Bee −1,000 THB

### PvP Death
The killer steals **5%** of the victim's current balance.

### Environmental Death
Dying to a **mob, fall, fire, lava, void, suffocation, or any non-player cause** deducts **2.4%** of your balance (not transferred to anyone).

---

## Admin Commands

All commands are **OP-only**. Confirmation messages are private (only the executing admin sees them).

| Command | Description |
|---|---|
| `/admin pay <player> <amount>` | Add money to a player's balance |
| `/admin deduct <player> <amount>` | Remove money from a player's balance (floors at 0) |
| `/admin speedhopper` | Give yourself a Speed Hopper for testing |

Amount supports shorthand: `1b` · `500k` · `10m` · `2.5t`

---

## OP Utility Commands

| Command | Description |
|---|---|
| `/nv` / `/nightvision` | Toggle infinite night vision (OP only) |
| `/sp` | Silently toggle spectator mode and back (OP only, no broadcast to other players) |

---

## HUD Sidebar

Each player has a personal sidebar showing:

| Line | Content |
|---|---|
| ★ Rank | Coloured by wealth tier |
| ◈ Company | Your company name, or `None` |
| B Balance | Current balance |
| ◆ Asset | Ender chest total sell value |
| $ Spend | All-time total spent in shop |
| ⚔ Kills | Mob kill count |
| ⏱ Lottery | Next draw countdown (`Xh YYm`) |
| Ping | Connection latency in ms |

---

## Config Files

All files are auto-generated in `config/andromeda-economy/` on first server start.

| File | Description |
|---|---|
| `prices.json` | Buy prices for every item. Edit freely — read on every startup. Delete to regenerate defaults. |
| `mob_rewards.json` | THB reward per mob type. Delete to regenerate defaults. |
| `companies.json` | Company data — auto-managed, do not edit manually. |
| `lottery.json` | Lottery round state — auto-managed, do not edit manually. |
| `players.db` | Player balances, kills, and spending (SQLite). |

---

## Mod Compatibility

Prices for mod items are auto-generated on first server start with the mod installed.

### BiomesOPlenty
**484 items** priced automatically. Notable anchors:

| Item | Buy Price |
|---|---|
| Rose Quartz Cluster | 5,000 |
| Rose Quartz Block | 18,000 |
| Brimstone | 10,000 |
| Flesh | 20,000 |
| Wispjelly | 20,000 |
| Anomaly | 50,000 |
| Music Disc Wanderer | 200,000 |
| All 14 wood type sets | Logs 2,000 · Planks 500 |

### More Sweet Treats
**18 food items** priced automatically (1,500 – 8,000 THB range).

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | ≥ 0.18.4 |
| Fabric API | 0.149.0+26.1.2 |
| Java | 21+ |

---

## Building from Source

```bash
git clone https://github.com/Nattaphong-jul/Andromeda-Minecraft-Store.git
cd Andromeda-Minecraft-Store/andromeda-economy
./gradlew build
# Fat jar: build/libs/andromeda-economy-1.8.3+26.1.2.jar
```

---

## Changelog Highlights

| Version | Summary |
|---|---|
| **v1.8.3** | Netherite +50%, sell base lock, gold deduction fix |
| **v1.8.2** | 2.4% non-PvP death penalty |
| **v1.8.1** | Client tooltips show rank-adjusted sell prices |
| **v1.8.0** | Rank-based sell pricing, spawn egg sell cap (100K) |
| **v1.7.9** | Speed Hopper polish (price, drop, double-visual fix, suck-in) |
| **v1.7.8** | Speed Hopper fixed (Mixin env — `server` → `mixins` section) |
| **v1.7.1** | Amethyst Pickaxe 3×3, lottery round broadcast |
| **v1.6.9** | BiomesOPlenty compatibility (484 items) |
| **v1.6.12** | Tiered spawn egg prices by mob difficulty |
| **v1.6.4** | Lottery 24h claim window, removed result phase |
| **v1.6.3** | Company 10% flat distribution, FED rank |
