# Chest loot and villager trades

## Chest loot

EA2 wrote one loot pool into Power Advantage's `additional-loot-tables` config
folder for six chest types. EA3 ports the same pool as a loot table plus a
NeoForge global loot modifier, so packs can change or remove it with a datapack.

- `data/electricadvantage/loot_table/chests/components.json`: EA2's pool
  unchanged. One roll, about a 25% chance of an item:

  | Item | Weight | Count |
  | --- | --- | --- |
  | (nothing) | 144 | |
  | Electric Power Cable | 12 | 3–10 |
  | Lead-Acid Battery | 8 | 1–2 |
  | Power Supply Unit | 6 | 1–4 |
  | LED Bar | 6 | 2–8 |
  | Solder | 6 | 3–12 |
  | Microchip | 3 | 5–22 |
  | Nickel-Metal Hydride Battery | 4 | 1–2 |
  | Alkaline Battery | 2 | 1–2 |
  | Lithium-Ion Battery | 1 | 1–2 |

  Batteries don't stack, so a count of 2 gives two separate batteries.

- `data/electricadvantage/loot_modifiers/chest_components.json`: a
  `neoforge:add_table` modifier that rolls the table once more for these vanilla
  chests: `abandoned_mineshaft`, `simple_dungeon`, `village/village_weaponsmith`,
  `stronghold_corridor`, `stronghold_crossing` and `end_city_treasure`.
  EA2's `village_blacksmith` table became `village/village_weaponsmith` in
  Minecraft 1.14.

To change the chests, override the modifier file. To remove the loot, override
`data/neoforge/loot_modifiers/global_loot_modifiers.json` without the
`electricadvantage:chest_components` entry, or override the loot table with an
empty one.

`LootGameTests` checks the table's 25% rate and item set, that all six chests
can give EA3 items, and that an unlisted chest (desert pyramid) never does.

## Villager trades

EA2 sold six items through the Librarian. Modern villagers pick two random
trades per level, so extra Librarian trades would make enchanted books (and
Mending) much rarer. EA3 puts the same trades on the Toolsmith, at the same
levels, and also adds them to the Wandering Trader's general pool.

| Item | Count | Emeralds | Toolsmith level |
| --- | --- | --- | --- |
| Electric Power Cable | 3–6 | 1 | Novice |
| Solder | 5–10 | 1 | Novice |
| Microchip | 3–6 | 1 | Novice |
| Circuit Board (Blank) | 3–6 | 1 | Novice |
| Petro-Plastic Ingot | 4–8 | 1 | Apprentice |
| Power Supply Unit | 1 | 1–3 | Apprentice |

Toolsmith trades have 12 uses and give vanilla's selling experience (1 at
Novice, 5 at Apprentice). Wandering Trader trades have 6 uses. The trades are
in `trades/TradeSystems.java`; NeoForge has no data format for trades in
1.21.1, so packs change them with KubeJS or CraftTweaker.

`TradeGameTests` checks each trade's item, count and price on the Toolsmith and
the Wandering Trader, and that Librarian trades are unchanged.
