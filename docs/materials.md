# Restricted material families

MMDLib discovers these definitions from `src/main/resources/mmdlib/material/`.
EA3 queues every loaded material in its namespace through `RuntimeContentRegistrar`.
The production registrar has no list of lithium/sulfur forms; their selections live
in the material JSON.

| Material | Item parts | Block parts | Fluids / entities |
| --- | --- | --- | --- |
| Lithium | `ingot`, `dust`, `nugget`, `electricadvantage:battery` | `ore` | Disabled |
| Sulfur | `dust` | `ore` | Disabled |

The battery module is reusable behavior registered through MMDLib's public
`ItemImplementation` API. Its template sets stack size one, and the material's
`special.battery_capacity` supplies capacity. Lithium selects 8,000 FE. This retains
the legacy numerical capacity, now expressed in the FE units used by Power
Advantage 3; no physical kJ-to-FE conversion is assumed. Newly created batteries
start full. Charge lives in the stack's persisted custom-data component, is exposed
through NeoForge's item energy capability, and supports simulated/bounded transfer.
All four battery tiers now share an MMDLib EnergyBuffer stack adapter; see
[battery implementation and recipes](batteries.md). Battery-array integration
will follow when that machine is implemented.

MMDLib's names follow `<material>_<part>`. The initial EA3 standalone definitions
were replaced, and assets, tags, translations and recipes now use these names:

| EA2 / initial EA3 path | Material-generated EA3 path |
| --- | --- |
| `li_ingot` | `lithium_ingot` |
| `li_powder` | `lithium_dust` |
| `li_nugget` | `lithium_nugget` |
| `li_ore` | `lithium_ore` |
| `sulfur_powder` | `sulfur_dust` |
| `sulfur_ore` | `sulfur_ore` |
| `lithium_battery` | `lithium_battery` |

No aliases or existing-world remapping are supplied for the initial prototype IDs.
The archive remains unchanged.

Static resources supply ore blockstates/models/textures, mining and common tags,
loot tables, lithium ore smelting, three MMDLib crushing recipes, and a conditional
lithium battery recipe. Lithium ore drops itself; sulfur ore drops 1–4 dust, with
a Silk Touch ore drop. OreSpawn 4 now owns deposit generation through a packaged ore-only provider;
see [infrastructure](infrastructure.md). Client appearance, real new-chunk
distribution, and conditional crafting with a full material provider remain pending.

Run `./gradlew --offline -PgameTests runGameTestServer` for server checks of material
selections, registered items, recipes, ore drops, and battery capabilities/persistence.
