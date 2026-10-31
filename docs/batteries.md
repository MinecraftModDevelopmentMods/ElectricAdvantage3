# Rechargeable batteries

All four batteries share `BatteryItem`, `BatteryImplementation`, and
`StackEnergyStorage`. No per-tier Java subclasses or registration lists are used.
MMDLib discovers the definitions, creates each item through the shared factory,
and includes it in the Electric Advantage materials creative tab.

| Battery | Capacity | Definition |
| --- | --- | --- |
| Lead-Acid | 1,000 FE | `mmdlib/standalone/item/lead_acid_battery.json` |
| Nickel-Metal Hydride | 2,000 FE | `mmdlib/standalone/item/nickel_hydride_battery.json` |
| Alkaline | 4,000 FE | `mmdlib/standalone/item/alkaline_battery.json` |
| Lithium-Ion | 8,000 FE | `mmdlib/material/lithium.json`, selecting `electricadvantage:battery` |

Standalone definitions supply `factory_data.capacity`; material batteries can
supply `special.battery_capacity`. The factory validates a positive integer
capacity and enforces a stack limit of one. New batteries start fully charged,
matching EA2. Capacities retain EA2's numerical values in PA3's FE units.

The stack adapter delegates charging/discharging to MMDLib `EnergyBuffer` and
persists charge under `electricadvantage:energy` in `minecraft:custom_data`.
Each operation reads the live stack, so multiple capability handles agree and
copied stacks remain independent. Simulation never changes charge. Energy is
bounded to zero/capacity; unrelated custom-data fields are preserved. Tooltips
show charge percentage and stored/maximum FE. NeoForge's item energy capability
provides the shared interface for future battery-array integration and other mods.

Recipes retain EA2's plastic casing and core ingredients:

- Lead-acid: lead ingot, sulfur dust, water bucket.
- Nickel-metal hydride: nickel ingot, redstone dust, water bucket.
- Alkaline: iron ingot, gunpowder, zinc ingot.
- Lithium-ion: lithium ingot, redstone dust, carbon dust.

Crafting returns empty water buckets. Recipes requiring external material tags
load only when those tags are nonempty. EA3 does not supply lead, nickel, zinc,
or carbon materials. All batteries belong to `c:batteries`.

Run `./gradlew --offline -PgameTests runGameTestServer`. Tests cover all capacities,
initial charge, limits/simulation, multiple handles, copy isolation, persistence
(including empty charge), energy conservation when transferring to an MMDLib
buffer, crafting outputs, and bucket remainders. Test-only common tags map vanilla
items to the missing external ingredients, allowing recipes to be tested without
adding production materials; those fixtures are excluded from the release JAR.

Interactive client appearance/tooltips, real external material providers, and the
battery-array machine remain separate parity checks.
