# Growth Chamber and Control Unit

Both blocks are redesigned from EA2 by user direction. They work like a tripled
Garden Cloche, powered by a separate Control Unit, as in EA2.

## Growth Chamber (`electricadvantage:growth_chamber`)

| Slots | Role |
| --- | --- |
| 0–2 | Soil, one per growth lane. Never consumed. Automation may insert, not extract. |
| 3–5 | Seed, one per lane, above its soil. Never consumed. Same automation rules. |
| 6–11 | Shared harvest outputs, exported to neighbours (16 items/tick) and extractable. Other growth chambers are never export targets, so clustered chambers don't fill each other's seed and soil slots. |
| 12 | Upgrade, in the screen's right-hand side tab. Player-only. |

Each lane looks up an `electricadvantage:growing` recipe for its seed and soil.
Every tick a lane with a recipe uses one unit of greenhouse power and gains one
growth tick. When it reaches the recipe's `time`, the harvest is rolled once.
If the whole harvest does not fit in the outputs, it is held and that lane
stops growing until it fits. Held harvests are saved, and dropped if the block
is broken.

Losing power or a redstone signal pauses a lane but keeps its progress.
Changing a lane's seed, soil or recipe restarts it. Progress survives
save/reload; EA2 lost it.

## Growth Chamber Control Unit (`electricadvantage:growth_chamber_controller`)

The Control Unit takes electricity (FE), water (4,000 mB tank, water only) and
fertilizer (one input slot). It produces up to 16 greenhouse units per tick
into a 1,000-unit buffer. Each unit costs 32 FE (EA2's 8 electricity), 2 mB of
water and 1 fertilizer. A fertilizer item is taken only when its whole value
fits in the 2,000-unit fertilizer store. Bone meal is worth 1,000, EA2's value
for the block of dirt it used instead of fertilizer: about 1.25 wheat harvests
per bone meal.

There is no greenhouse cable. Each tick the Control Unit shares its buffer
equally between the chambers it touches, directly or through other touching
chambers, up to 16 chambers. The cluster is rescanned every 20 ticks and when
a neighbour of the Control Unit changes. A redstone signal stops conversion
and distribution. The comparator outputs 15 when there is enough energy, water
and fertilizer for a unit, as in EA2, and 0 otherwise.

All numbers are `block_entity_data` in the two
`mmdlib/standalone/block/growth_chamber*.json` files.

## Recipes: `electricadvantage:growing`

```json
{
  "type": "electricadvantage:growing",
  "seed": { "item": "minecraft:oak_sapling" },
  "soil": { "tag": "electricadvantage:growth_soils/dirt" },
  "time": 1200,
  "results": [
    { "item": { "id": "minecraft:oak_log", "count": 4 }, "bonus_per_fertility": 0.5 },
    { "item": { "id": "minecraft:apple" }, "chance": 0.2, "chance_per_fertility": 0.05 },
    { "item": { "id": "minecraft:oak_leaves", "count": 4 }, "upgrade": "shearing" }
  ]
}
```

| Result field | Meaning |
| --- | --- |
| `item` | Item stack produced. Exactly one of `item` or `copy_seed` is required. |
| `copy_seed` + `count` | Produce `count` copies of the seed itself. Used for tag-based duplication, e.g. every flower in a tag. |
| `chance` | Probability the entry drops at all (default 1). |
| `chance_per_fertility` | Added to `chance` per soil fertility level. |
| `bonus_per_fertility` | Per fertility level, the probability of +1 item, like fortune or looting. |
| `upgrade` | Drops only while the upgrade slot holds an item tagged `electricadvantage:growth_chamber_upgrades/<name>`. |

Soil fertility is the item data map `electricadvantage:soil_fertility`
(`{"fertility": n, "yield": x}`, both optional). Soils without an entry have
fertility 0 and yield 1. Mud, rooted dirt, podzol, mycelium, moss and both
nyliums are fertility 1. Expensive modded soils should get higher values by
datapack. Fertilizer is the item data map `electricadvantage:fertilizer`
(`{"value": n}`).

`yield` multiplies every result's count after the fertility bonus, and the
fractional part is the chance of one more item. For example, 1 wheat at yield
1.5 gives 1, with a 50% chance of 2. Unlike fertility, yield applies the same
way to every recipe.

Farmland is a dirt soil with yield 1.5, so it gives half as much again as dirt.
Farmland can't be picked up in survival, so EA3 adds a crafting recipe: a hoe
plus dirt, a grass block or a dirt path gives farmland, and the hoe comes back
with one point of damage. It uses the `electricadvantage:crafting_shapeless_tool`
recipe type, a shapeless recipe that damages tools instead of using them up.

Allowed soils are the `electricadvantage:growth_soils/*` item tags, so packs
can add soils without editing recipes. The upgrade slot accepts
`#electricadvantage:growth_chamber_upgrades`.

## Bundled recipes

`data/electricadvantage/recipe/growing/` covers:

- **Crops:** wheat, carrot, potato, beetroot, pumpkin, melon, torchflower,
  pitcher plant, sweet and glow berries, nether wart (soul sand), cocoa (jungle
  log), chorus (end stone), sugar cane, cactus (sand), bamboo, kelp and
  seagrass (water bucket), and red and brown mushrooms.
- **Trees:** every vanilla tree, grown from its sapling. Logs and saplings;
  oak and dark oak also give apples. Leaves and sticks need the shearing
  upgrade.
- **Huge fungi:** stems and fungus. Wart blocks and shroomlights need the
  shearing upgrade. They grow on netherrack, or on nylium for the fertility
  bonus.
- **EA2 duplication:** flowers, grasses and ferns, vines, lily pads, corals and
  coral fans, and the grass block. Wither roses are excluded.

Growth times follow EA2's 100 ticks per stage (wheat 800, trees 1,200).

## Not yet done

- The Shearing Upgrade (`electricadvantage:shearing_upgrade`) has no recipe
  and uses the vanilla shears texture as a placeholder.
- No JEI category or script integration; that is deferred until after
  parity.
- Interactive appearance, connected-screen sync, real pipe/conveyor automation
  and chunk unload/reload are not yet verified in game.

## Verification

```sh
python3 scripts/validate_resources.py
./gradlew --offline -PgameTests runGameTestServer
```

`FarmingGameTests` covers: wheat timing with catalysts kept; soil restrictions
and slot rules; fertility bonus and upgrade gating; farmland's 1.5 yield (in the
chamber and over 1,000 seeded harvests), yield applying after the fertility
bonus, and the farmland recipe with hoe damage and breakage; full-output holding across
save/reload; progress across reload, power loss and redstone; seed-copy
duplication; Control Unit costs, cluster connection and equal sharing; input
filters, comparator and redstone; and menu slot binding.
