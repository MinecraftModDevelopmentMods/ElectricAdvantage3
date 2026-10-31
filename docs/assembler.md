# Automated Assembler (`electricadvantage:electric_fabricator`)

The assembler crafts from a 3×3 ghost pattern using real items from a nine-slot
parts buffer, and writes to one output slot. It uses one recipe catalog, built
from two sources:

1. **Imported crafting recipes.** Every `minecraft:crafting_shaped` and
   `minecraft:crafting_shapeless` recipe that fits 3×3 is imported as
   `electricadvantage:imported/<namespace>/<path>`. Imports use the block's
   `default_time` and `default_power_cost`. Source recipes are never modified.
   Recipes whose ID path ends in `_manual_only` are not imported. Other
   crafting serializers (special recipes, mod-specific shaped types) are skipped.
2. **Explicit recipes** of type `electricadvantage:assembling` (below).

Explicit recipes are listed first. A recipe with `override` removes that
source recipe's import. An explicit recipe whose ID equals an imported ID
replaces that import. Two explicit recipes overriding the same source is an
error.

The catalog is invalidated on every datapack reload and rebuilt the first time
a machine uses it. That rebuild happens after all reload listeners have run,
so recipe changes from KubeJS and CraftTweaker are included in both the
imports and the explicit recipes. Code that changes a live `RecipeManager`
outside a reload should call `AssemblerCatalog.rebuild(manager)` afterwards.

## Recipe format

```json
{
  "type": "electricadvantage:assembling",
  "pattern": ["AB", " A"],
  "key": {
    "A": { "item": "minecraft:iron_ingot", "count": 3 },
    "B": { "tag": "c:ingots/copper", "count": 2 }
  },
  "result": { "id": "minecraft:diamond", "count": 2 },
  "time": 6,
  "power_cost": 11,
  "override": "minecraft:some_crafting_recipe"
}
```

| Field | Notes |
| --- | --- |
| `pattern` + `key` | Shaped form: rows of 1–3 characters, all the same width; space means empty. Blank outer rows and columns are trimmed. Mirrored matches are accepted. |
| `ingredients` | Shapeless form, 1–9 entries. Use this instead of `pattern`/`key`, not alongside them. |
| ingredient `count` | 1–64. Each cell must be filled from one item identity (same item and components); a count-2 cell cannot take one copper and one iron. |
| `result` | Item stack, with components allowed. |
| `time` | Ticks per craft, 1–1,000,000. |
| `power_cost` | Electricity per craft, charged once at completion. The FE cost is `power_cost × fe_per_electricity` (default 4). |
| `override` | Optional. ID of a crafting recipe whose import this recipe replaces. |

Ingredients use NeoForge's ingredient codec, so custom ingredient types
(`neoforge:compound`, `neoforge:components`, etc.) are accepted. Container
remainders (buckets, bottles) go back into the parts buffer. If a remainder
does not fit, the craft waits.

## KubeJS

```js
ServerEvents.recipes(event => {
  event.custom({
    type: 'electricadvantage:assembling',
    ingredients: [{ item: 'minecraft:iron_ingot', count: 2 }],
    result: { id: 'minecraft:iron_block' },
    time: 40,
    power_cost: 50
  }).id('kubejs:assembler/iron_block')

  event.remove({ type: 'electricadvantage:assembling', output: 'minecraft:diamond' })
})
```

Removing or editing a vanilla crafting recipe also changes its assembler
import.

## CraftTweaker

```zenscript
<recipetype:electricadvantage:assembling>.addJsonRecipe("iron_block", {
  type: "electricadvantage:assembling",
  ingredients: [{ item: "minecraft:iron_ingot", count: 2 }],
  result: { id: "minecraft:iron_block" },
  time: 40,
  power_cost: 50
});
<recipetype:electricadvantage:assembling>.remove(<item:minecraft:diamond>);
```

These snippets use each mod's generic custom-recipe support. EA3 has no
dedicated KubeJS schema or CraftTweaker manager class yet. Neither mod is in
the development environment, so the snippets have not been tested in game.
