# Electric Advantage 3 feature parity TODO

Inventory scanned from the EA2 archive on 2026-10-31. EA3 now implements the 13 electronics/material items, all four batteries, all seven ore/infrastructure blocks, the four generation/storage blocks, and the first five processing machines.
Lithium and sulfur use restricted MMDLib material definitions. Full parity checkboxes remain unchecked until their
acquisition dependencies and interactive checks are complete.
`EA2/` is reference-only and must remain unchanged.

## How to track completion

Each content checkbox means the feature is usable in EA3: registration, behavior,
recipes/acquisition, assets/localization, persistence and synchronization, and
applicable automation are implemented and verified in game. A successful build
alone does not complete a feature. Checked subtasks record completed portions
of an otherwise unfinished feature. Update these flags during each implementation
pass and record verification evidence beside completed entries. The IDs below are legacy registry paths under `electricadvantage:`;
Java field names sometimes differ. Current material-generated IDs and their
legacy mappings are documented in [materials.md](docs/materials.md).

Inventory total: **17 standalone items + 23 blocks**. The blocks comprise **15 main
machines + 1 hidden turret variant + 7 other blocks**, including ores, transport,
lighting, and the pump's generated pipe. Block items are included with their blocks,
not counted again as standalone items.

## Current implementation progress

Current implementation (2026-10-31): nine component items use MMDLib standalone
JSON; lithium and sulfur use material JSON with explicit part selections. Lithium
selects ingot, dust, nugget, battery and ore; sulfur selects dust and ore. Fluids
and entity parts are disabled. Both paths use `RuntimeContentRegistrar`, which
queues the namespace dynamically without a hard-coded item list.
Their legacy textures are copied to modern `textures/item` paths, model parents
use `minecraft:item/generated`, and existing item translations are converted to
JSON. MMDLib supplies the `electricadvantage:materials` creative tab.

Static recipes implement normal-mode electronics crafting, silicon/solder/lithium
powder smelting, and nine-to-one lithium nugget/ingot conversion. The nugget
conversion is an explicit addition resolving EA2's acquisition gap. Recipes that
need external material tags are gated on those tags being nonempty; they become
available when compatible content supplies the ingredients. This does not add
substitute copper/tin/lead/carbon materials to EA3.

Completed work:

- [x] Register all 13 electronics/material items through nine standalone definitions and two restricted material families.
- [x] Define lithium with only ingot/dust/nugget/battery/ore and sulfur with only dust/ore; disable fluids/entities.
- [x] Replace prototype `li_*`/`sulfur_powder` references with material-generated IDs; see [mapping](docs/materials.md).
- [x] Implement both ore blocks, mining tags, drops, textures/models, lithium ore smelting, and three MMDLib crushing recipes.
- [x] Implement material-selected lithium battery: JSON capacity, energy capability, bounded/simulated transfer, persisted charge, and tooltip.
- [x] Port item models/textures and existing translations; supply material/component tags and creative-tab title.
- [x] Add 17 initial recipes, including conditional normal-mode electronics crafting, three smelting recipes, and reversible lithium nugget conversion.
- [x] Verify NeoForge startup, build, and packaged item resources.
- [x] Add and pass the isolated server integration test for all 13 registrations/stack limits, three smelting recipes, and both lithium conversions.

Remaining checks for this item group:

- [ ] Verify item appearance and creative-tab entries in an interactive client.
- [ ] Verify conditional electronics recipes with the external material ingredients installed.
- [ ] Implement tech-progression/apocalyptic recipe selection.
- [x] Implement refinery production and electric crusher integration with MMDLib crushing recipes.
- [ ] Verify OreSpawn deposits in newly generated chunks and complete end-to-end survival progression.

Still pending: tech-progression/apocalyptic recipe selection; new-chunk OreSpawn
deposit verification; the remaining processing/automation machines; interactive client,
menu synchronization and automation/unload checks.
No claim of complete survival progression is made by this first pass.

## Items: electronics and materials (13)

Source: [Items.java](EA2/src/main/java/cyano/electricadvantage/init/Items.java),
[Recipes.java](EA2/src/main/java/cyano/electricadvantage/init/Recipes.java), and
[English names](EA2/src/main/resources/assets/electricadvantage/lang/en_US.lang).

- [ ] `blank_circuit_board` — Circuit Board (Blank); copper/plastic crafting component.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `control_circuit` — Control Circuit; blank board + microchip + solder.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `integrated_circuit` — Microchip; recipe-mode-dependent silicon/metal recipes.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `psu` — Power Supply Unit; legacy Java field `power_supply_unit`.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `silicon_ingot` — Silicon Ingot; smelt silicon blend.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `silicon_mix` — Silicon Blend; sand + carbon dust; field `silicon_blend`.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `solder_mix` — Solder Blend; tin dust with lead or silver dust; field `solder_blend`.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `solder` — Solder; smelt solder blend.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `li_ingot` — Lithium Ingot; smelt lithium ore or powder.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `li_powder` — Powdered Lithium; crusher output from lithium ore/ingots.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `li_nugget` — Lithium Nugget; EA2 had no nugget conversion recipe; EA3 now provides reversible nine-nugget/one-ingot crafting.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `sulfur_powder` — Powdered Sulfur; sulfur ore drops/crushing and battery ingredient.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.
- [ ] `petrolplastic_ingot` — Petro-Plastic Ingot; refinery output and plastic crafting ingredient.
  - [x] Item definition, MMDLib registration, tags, model, texture, and existing translations ported; server registration and packaged resources verified.


## Items: rechargeable batteries (4)

Sources: [BatteryItem.java](EA2/src/main/java/cyano/electricadvantage/items/BatteryItem.java),
[Power.java](EA2/src/main/java/cyano/electricadvantage/init/Power.java), and `Recipes.java`.
Capacities below use EA2's displayed kJ units. All four implemented batteries
retain their numeric capacities in FE for PA3 compatibility; see [batteries](docs/batteries.md).

- [ ] `lead_acid_battery` — Lead-Acid Battery; capacity 1,000; lead/sulfur/water recipe.
  - [x] Data-defined item/capacity, shared MMDLib energy core, capability, persistence, percentage/capacity tooltip, assets/translations, battery tag and conditional recipe implemented.
  - [x] Battery-array integration implemented and server-tested.
  - [ ] Verify interactive client appearance/tooltips and real external material providers.
- [ ] `nickel_hydride_battery` — Nickel-Metal Hydride Battery; capacity 2,000; nickel/redstone/water recipe.
  - [x] Data-defined item/capacity, shared MMDLib energy core, capability, persistence, percentage/capacity tooltip, assets/translations, battery tag and conditional recipe implemented.
  - [x] Battery-array integration implemented and server-tested.
  - [ ] Verify interactive client appearance/tooltips and real external material providers.
- [ ] `alkaline_battery` — Alkaline Battery; capacity 4,000; iron/gunpowder/zinc recipe.
  - [x] Data-defined item/capacity, shared MMDLib energy core, capability, persistence, percentage/capacity tooltip, assets/translations, battery tag and conditional recipe implemented.
  - [x] Battery-array integration implemented and server-tested.
  - [ ] Verify interactive client appearance/tooltips and real external material providers.
- [ ] `lithium_battery` — Lithium-Ion Battery; capacity 8,000; lithium/redstone/carbon recipe.
  - [x] Material battery part, 8,000 FE capacity, charge storage/capability, tooltip, model/texture/translations and conditional crafting recipe implemented.
  - [x] Battery-array integration implemented and server-tested.
  - [ ] Verify interactive appearance and crafting with real external carbon materials.
- [ ] Shared battery behavior: stack size one, newly created batteries start fully charged, bounded charge/discharge, persisted charge, percentage/capacity tooltips, and battery-array compatibility.
  - [x] One shared item factory and stack adapter for all four tiers; MMDLib EnergyBuffer supplies bounded transfer logic.
  - [x] Full initial charge, nonstacking, simulated transfer, stack-copy independence, live capability handles, persisted charge (including empty), and percentage/capacity tooltip implemented.
  - [x] Battery-array capability/inventory integration implemented and server-tested; interactive menu/automation checks remain pending.

## Blocks: ores and infrastructure (7)

Sources: [Blocks.java](EA2/src/main/java/cyano/electricadvantage/init/Blocks.java),
[block implementations](EA2/src/main/java/cyano/electricadvantage/blocks), and the LED machine classes.

- [ ] `li_ore` — Lithium Ore; drops itself, pickaxe tier 1 in EA2, smelting and crusher recipes; field `lithium_ore`.
  - [x] Material-generated `lithium_ore`, assets, mining tags, self-drop, smelting and crushing recipes implemented.
  - [x] Supply a packaged MMD OreSpawn 4 provider with restricted stone hosts and verify both rules in the active world profile; remove native deposit features/modifiers.
  - [ ] Verify ore appearance/mining and distribution in newly generated chunks.
- [ ] `sulfur_ore` — Sulfur Ore; drops 1–4 sulfur powder, pickaxe tier 0 in EA2; crusher yields four powder.
  - [x] Material-generated ore, assets, mining tags, 1–4 dust drop, Silk Touch ore loot branch and crushing recipe implemented.
  - [x] Supply a packaged MMD OreSpawn 4 provider with restricted stone hosts and verify both rules in the active world profile; remove native deposit features/modifiers.
  - [ ] Verify ore appearance/mining and distribution in newly generated chunks.
- [ ] `electric_conduit` — Electric Power Cable; electricity network connections and cable models; copper with plastic/rubber recipes.
  - [x] MMDLib block/entity definitions, cable shapes/connections, input capability, persisted buffer, PA3 transport adapter, four conditional recipes and migrated assets implemented.
  - [x] Live PA3 generator-to-consumer transport, input limits/simulation and cable recipes verified on the test server.
  - [ ] Verify client appearance/lighting and external automation; stress-test network unload/reload and larger layouts.
- [ ] `electric_track` — Electric Scaffold; full-block collision, electricity conduction, and mining-drill movement support; cable + steel frame recipe.
  - [x] Shared electric transport, full-block scaffold collision, legacy models and cable + PA3 frame recipe implemented.
  - [x] Electric transport through scaffold, full collision and crafting verified on the test server.
  - [ ] Verify client appearance and integrate mining-drill movement when the drill is implemented.
- [ ] `electric_switch` — Electric Switch; electricity switching through Power Advantage's switch implementation; lever/wire/frame recipe.
  - [x] Manual toggle, redstone-edge control, network isolation, dynamic capability refusal, migrated models and crafting implemented.
  - [x] Power interruption/reopening, redstone transitions, retained capability behavior and crafting verified on the test server.
  - [ ] Verify client appearance/lighting and external automation; stress-test network unload/reload and larger layouts.
- [ ] `led_bar` — LED Bar; power-consuming light with synchronized lit state and high-priority power request.
  - [x] Input-only powered light, preferred network delivery, data-defined consumption/capacity, synchronized lit state, level-15 lighting, models and recipe implemented.
  - [x] Live generator/cable/switch operation, loss of light after power interruption and crafting output verified on the test server.
  - [ ] Verify client appearance/lighting and external automation; stress-test network unload/reload and larger layouts.
- [ ] `pump_pipe_electric` — Pump Pipe; generated by the electric pump, no drops, connected pipes below are removed when broken. Search-tab-only visibility in EA2; no crafting recipe.
  - [x] Generated-only block, shaft shape, empty loot and bottom-up lower-column cleanup implemented; its block item is visible in creative search only.
  - [x] Lower-column removal, preservation above the break, empty loot and search-only item visibility verified on the test server.
  - [ ] Integrate pipe deployment/retraction with the electric pump when implemented; verify client appearance.

## Blocks: generation and storage (4)

Each entry includes its Block and TileEntity implementation under
[machines/](EA2/src/main/java/cyano/electricadvantage/machines).

- [ ] `photovoltaic_generator` — Photovoltaic Generator (`PhotovoltaicGenerator*`); light-dependent output, day/weather/block-light behavior, comparator output, redstone disable, and generator GUI. Crafting excluded in apocalyptic recipe mode.
  - [x] Data-defined shared generator core, daylight/weather/block-light output, fractional generation persistence, energy/comparator/redstone behavior, migrated assets, recipe and MMDLib XML screen implemented.
  - [x] Server checks pass for daylight/night, rain/thunder, redstone/comparator behavior, recipe output and XML/menu binding.
  - [ ] Verify interactive client appearance, live menu synchronization and automation/unload behavior. Restore apocalyptic-mode recipe restriction with recipe-mode support.
- [ ] `hydroelectric_generator` — Hydroelectric Generator (`HydroelectricGenerator*`); hydroturbine entity lifecycle, water-driven generation, turbine collision/damage, renderer/animation, and generator GUI. See also [HydroturbineEntity.java](EA2/src/main/java/cyano/electricadvantage/entities/HydroturbineEntity.java).
  - [x] Data-defined generator, flowing-water detection/output, block-entity-owned turbine lifecycle, client animation/renderer, blade damage/type/death messages, migrated assets, recipe and MMDLib screen implemented.
  - [x] Server checks pass for horizontal water flow, electrical generation, blade damage, stopping without water, crafting and XML/menu binding.
  - [ ] Verify interactive client appearance, live menu synchronization and automation/unload behavior.
- [ ] `steam_powered_generator` — Steam-Powered Electric Generator (`SteamPoweredElectricGenerator*`); accepts steam-network power and supplies electricity, steam buffer persistence/sync, and generator GUI. Corrected conversion target: 32 steam → 1 electricity (0.03125 electricity per steam), derived from 8 steam/RF and 0.25 electricity/RF; replace EA2's erroneous 31.25 electricity-per-steam constant.
  - [x] PA3 steam-only fluid storage/capability, whole-batch conversion with remainders/backpressure, persisted tanks/energy, redstone behavior, recipe, assets and MMDLib screen implemented.
  - [x] Server checks pass for 32 mB -> 4 FE (32 steam/electricity at 0.25 electricity/FE), invalid fluid rejection, simulation/remainders/full buffers/redstone/persistence, and live PA3 pipe -> generator -> cable -> LED operation.
  - [ ] Verify interactive client appearance, live menu synchronization and automation/unload behavior.
- [ ] `electric_battery_array` — Battery Array (`ElectricBatteryArray*`; field `battery_array`); eight battery slots, aggregate charge/capacity, charging and discharge, backup-priority requests, prevention of array-to-array cycling, redstone discharge disable, and battery GUI.
  - [x] Shared core with eight filtered battery slots, summed item charge/capacity, charge/discharge, post-primary backup dispatch, no array-to-array cycling, redstone discharge disable, persisted inventory, item drops, recipe, assets and MMDLib screen implemented.
  - [x] Server checks pass for mixed capacities, filters, simulation/discharge/charging/persistence, isolation between arrays, backup-powered loads, redstone cutoff, crafting and real menu slots.
  - [ ] Verify interactive client appearance, live menu synchronization and automation/unload behavior.

## Blocks: processing and automation (11)

Implementations: [machines/](EA2/src/main/java/cyano/electricadvantage/machines).
Screens: [gui/](EA2/src/main/java/cyano/electricadvantage/gui).
Current first-batch implementation and behavior: [processing.md](docs/processing.md).

- [ ] `electric_furnace` — Electric Arc Furnace (`ElectricFurnace*`; field `arc_furnace`); four parallel smelting inputs, six output slots, individual progress, and arc-furnace GUI.
  - [x] Shared data-defined processing core, four smelting lanes, six output slots, independent saved progress, energy/item automation, redstone state, recipe/assets and MMDLib XML screen implemented.
  - [x] Four parallel smelts resume across saved progress; live PA3 generator -> cable -> furnace -> chest export, lane consumption, menu slots and crafting verified.
  - [ ] Verify interactive appearance, connected GUI synchronization, real external automation and unload/reload behavior.
- [ ] `electric_crusher` — Electric Rock Crusher (`ElectricCrusher*`; field `rock_crusher`); three parallel crushing inputs, six output slots, shared crusher recipe integration, individual progress, and crusher GUI.
  - [x] Three parallel lanes using MMDLib crushing recipes, six outputs, per-lane progress/persistence, atomic output reservation, automation, recipe/assets and XML screen implemented.
  - [x] Three recipe outputs/counts, rejection of partially fitting results, full-output conservation/recovery, menu slots and crafting verified.
  - [ ] Verify interactive appearance, connected GUI synchronization, real external automation and unload/reload behavior.
- [ ] `electric_oven` — Electric Oven (`ElectricOven*`; field `oven`); one input/output, smelting restricted to food results, cooking progress, and oven GUI.
  - [x] One-input/one-output food-only smelting, 100-tick work, recipe/input-change checks, role-filtered slots, redstone behavior, recipe/assets and XML screen implemented.
  - [x] Non-food rejection, changed-input progress reset, redstone without energy consumption, food output, protected output insertion, menu slots and crafting verified.
  - [ ] Verify interactive appearance, connected GUI synchronization, real external automation and unload/reload behavior.
- [ ] `electric_fabricator` — Automated Assembler; redesigned from EA2's recursive fabricator by user direction.
  - [x] Design agreed: vanilla-style 3×3 ghost recipe grid, one real output slot, and one row of nine real parts-buffer slots consumed by crafting.
  - [x] Design agreed: dedicated assembler recipe type/catalog; import vanilla `minecraft:crafting_shaped` and `minecraft:crafting_shapeless` recipes, excluding source recipe IDs whose path ends with `_manual_only` (an ID suffix, not an item/block tag).
  - [ ] Implement imported recipe wrappers with stable IDs and source-recipe provenance; leave the source crafting recipes unchanged and refresh imports after initial recipe loading and every successful server datapack reload.
  - [ ] Support explicit assembler recipes; define deterministic precedence for explicit overrides of imported recipes.
  - [ ] Implement selection/ghost display, ingredient allocation from the nine-slot buffer, energy/progress, full-output waiting, crafting remainders, persistence, automation, XML screen and tests. No recursive intermediate crafting is required by this redesign.
  - [ ] Test shaped/shapeless imports, `_manual_only` exclusion, reload removal/replacement, ingredient alternatives, container remainders and full-buffer/output conservation.
  - EA2 reference only: [crafting utilities](EA2/src/main/java/cyano/electricadvantage/util/crafting).
- [ ] `electric_drill` — Electric Mining Drill (`ElectricDrill*`; field `laser_drill`); directional laser mining up to 64 blocks, hardness-dependent progress, drops and inventory export, movement along electric scaffold, state/inventory transfer during movement, beam damage, renderer, and drill GUI.
- [ ] `laser_turret` — Laser Turret (`LaserTurret*`); hostile targeting, owner handling, aiming/beam renderer, laser damage/sound, power use, and redstone behavior.
- [ ] `growth_chamber` — Growth Chamber (`GrowthChamber*`); three crop inputs, six output slots, virtual crop growth/harvesting, controller-supplied greenhouse power, progress, and chamber GUI. See [VirtualCrop.java](EA2/src/main/java/cyano/electricadvantage/util/farming/VirtualCrop.java).
- [ ] `growth_chamber_controller` — Growth Chamber Control Unit (`GrowthChamberController*`); electricity + water + dirt/grass converted into greenhouse-network power; soil/water/energy storage, automation, and controller GUI.
- [ ] `electric_pump` — Electric Pump (`ElectricPump*`); extends pump pipe downward, locates and consumes fluid source blocks, stores fluid, exports through Power Advantage's fluid network, and pump GUI.
- [ ] `electric_still` — Electric Distiller (`ElectricStill*`); Power Advantage distillation recipes, separate input/output tanks, electricity consumption, fluid routing, and distiller GUI.
  - [x] PA3 distillation recipes, role-separated tanks, recipe-duration/energy use, saved pending output, fluid export, recipe/assets and MMDLib XML screen implemented.
  - [x] Invalid input rejection, one-time batch consumption, pending-output persistence/full-tank waiting/recovery, input drain protection, menu and crafting verified.
  - [ ] Verify interactive appearance, connected GUI synchronization, real external automation and unload/reload behavior.
- [ ] `plastic_refinery` — Petro-Plastic Refinery (`PlasticRefinery*`); configurable accepted fluids (EA2 defaults `refined_oil` and `oil`), converts 100 mB per petro-plastic ingot over 100 processing ticks, fluid input/item output automation, and refinery GUI.
  - [x] Data-defined oil IDs, amount/timing/cost/result, fluid input and item output automation, saved progress, recipe/assets and XML screen implemented.
  - [x] Both configured PA3 oils, 100 mB/100 ticks, saved/resumed production, invalid-fluid rejection and no oil/energy loss under full-output conditions verified, along with menu/crafting.
  - [ ] Verify interactive appearance, connected GUI synchronization, real external automation and unload/reload behavior.

## Blocks: hidden machine variant (1)

- [ ] `laser_turret_evil` — Player-targeting Laser Turret; shares the normal turret's display name and implementation but uses the evil targeting flag. Registered in EA2, hidden from its creative tab, and has no crafting recipe in `Recipes.java`. Preserve or explicitly document its replacement separately from the normal turret.

## Supporting parity work

- [ ] Register electricity and greenhouse network types and integrate generators, consumers, cables, switching, priorities, and storage with Power Advantage 3.
  - [x] Electricity uses PA3 transport discovery/routing/fair budgets and MMDLib storage/capabilities; cable/scaffold/switch/LED implementation and live transport tests pass.
  - [ ] Integrate future generators/storage/machines and implement greenhouse power.
- [ ] Recreate required block items, placement/orientation, shapes, break/drop behavior, active/lit states, and creative-tab placement.
- [ ] Port machine inventories, item/fluid automation, redstone controls and comparator behavior where implemented; verify full-output and empty-input conditions without duplication/loss.
- [ ] Port machine menus/screens, gauges/progress, server-side interaction validation, synchronization, and state persistence through save/reload and chunk unloading.
- [ ] Port models, blockstates, textures, custom renderers, sounds, translated names/tooltips, and laser/propeller death messages. EA2 contains multiple locale files; audit coverage rather than assuming each is complete.
- [ ] Port crafting, smelting, crushing, battery, and machine recipes from `init/Recipes.java`, including ingredient alternatives and output counts.
- [ ] Preserve or explicitly replace Power Advantage recipe modes: normal, tech progression, and apocalyptic, including electronics recipes, machine recycling, and restricted solar-generator crafting.
- [ ] Map legacy ore-dictionary aliases to current material/tag APIs, including silicon, lithium, sulfur/sulphur, solder, plastic, batteries, circuits, PSU, and cable aliases. Resolve old Base Metals ingredient/crusher dependencies through the current libraries or an explicit compatibility decision.
- [ ] Restore lithium/sulfur ore generation. EA2 writes an OreSpawn configuration from [Data.java](EA2/src/main/java/cyano/electricadvantage/Data.java); choose and verify the current integration and modern height/distribution settings.
  - [x] MMD OreSpawn 4 required dependency and schema-4 provider implemented: Overworld stone hosts, Y=1..32, lithium 0.125 attempts/6–10 blocks and sulfur 1 attempt/8–24 blocks; per-ore retrogen disabled.
  - [ ] Verify actual new-chunk distribution; no retrogen is implemented.
- [ ] Restore component/battery chest loot for mineshafts, dungeons, village blacksmiths, stronghold corridors/crossings, and end cities; source [TreasureChests.java](EA2/src/main/java/cyano/electricadvantage/init/TreasureChests.java). EA2 writes additional-loot-table files; verify actual modern loot injection.
- [ ] Restore six villager trades: petro-plastic, cable, solder, microchips, blank circuit boards, and PSUs; source [Villages.java](EA2/src/main/java/cyano/electricadvantage/init/Villages.java). Its proposed machinist villager is only a TODO, not an implemented parity feature.
- [ ] Port configuration for fabricator recursion limit, laser sound, and accepted petro-plastic fluids; source [ElectricAdvantage.java](EA2/src/main/java/cyano/electricadvantage/ElectricAdvantage.java).
- [ ] Verify progression end to end: acquire materials → components/batteries → generation/cabling → processing/automation. Check client and dedicated-server behavior and all supported recipe modes.

## Archive discrepancies to resolve before expanding scope

These are not part of the 17-item/23-block registered inventory.

- [ ] Decide whether PCB Printer (`electric_pcb_printer`) is a future feature: EA2 has localization/blockstate assets, but no corresponding block registration or machine implementation.
- [ ] Decide whether Geothermal Generator (`geothermal_generator`) is a future feature: EA2 has localization/blockstate assets, but no corresponding block registration or machine implementation.
- [ ] Audit remaining orphan resources (for example wind-turbine texture and battery-base item model) before copying assets; an asset filename alone does not establish a registered feature.

## Verification record

Rows record verification at each implementation stage; remaining-gap columns
describe that stage. Current pending work is listed under each feature above.

| Feature ID | Build/data/package checks | In-game scenario and result | Remaining gaps |
| --- | --- | --- | --- |
| First 13 electronics/material items | `./gradlew --offline build runData` passed; startup loaded 13 standalone item definitions. Static resources supply assets/tags/recipes; no datagen provider is needed for this pass. | `./gradlew --offline -PgameTests runGameTestServer`: 1 required test passed, covering all 13 registrations/stack limits, 3 smelting recipes, and lithium ingot/nugget conversion in both directions. | Interactive appearance/tab checks, external recipe ingredients, recipe modes, and machine/world acquisition remain pending. |
| Restricted lithium/sulfur materials | Build passed; two material definitions explicitly restrict parts and disable fluids/entities; legacy references migrated. | `./gradlew --offline -PgameTests runGameTestServer`: all 3 tests passed, covering exact material selections, battery stack limit/charging/simulation/bounds/persistence, ore drops, lithium smelting, crushing, and prior component/conversion checks. | Worldgen, interactive appearance/mining, Silk Touch interaction, conditional battery crafting with external materials, and battery array remain pending. |
| All four battery tiers | Build passed; data-driven capacities share BatteryImplementation/BatteryItem/StackEnergyStorage, using MMDLib EnergyBuffer; models/textures/translations/recipes packaged. | `./gradlew --offline -PgameTests runGameTestServer`: all 5 required tests passed. Battery tests cover four capacities, full initial charge, simulation/bounds, multiple handles, independent copies, empty-charge persistence, MMDLib transfer conservation, all four crafting recipes and bucket remainders. Crafting uses isolated test-only ingredient-tag fixtures. | Interactive client appearance/tooltips, real external material-provider compatibility, and battery-array machine remain pending. |
| Ores and infrastructure | Build and static model/texture validator passed; all five additional blocks use MMDLib definitions, shared factories and PA3 transport; native ore features and biome modifiers supplied in the initial pass (subsequently replaced by OreSpawn 4; see below). | All 10 required server tests passed, including PA3 power through cable/scaffold/switch to LED, switch isolation/reopening/redstone and input bounds, pipe cleanup/no drops, buffered-energy persistence, biome feature attachment/configured ore placement, and infrastructure recipes. | Client appearance/lighting, real new-chunk distribution, external automation and network stress/unload/reload checks; pump deployment and drill movement await those machines. |
| OreSpawn 4 integration | Required Maven dependency pinned to `4.1.0.121012`; packaged provider replaces all six native deposit resources; build passed. | All 10 required server tests passed with OreSpawn installed. OreSpawn reports EA3 revision 1 active with exactly 2 ores and no rocks; public API confirms active profile output blocks, Y range, frequency and host tags; native features are absent. | Actual OreSpawn deposit placement/distribution in new chunks and existing-world profile/retrogen scenarios are not yet verified. |
| Generation and storage | Shared MMDLib factories/settings/storage, four XML screens, recipes/assets and turbine renderer implemented; build/resource checks pass; graphical client startup reached EA3_CLIENT_BOOTSTRAP_PASS with no EA3 missing-model/texture warnings. | All 18 required server tests passed, including eight generation tests covering conversion, real PA3 fluid/electric integration, weather/hydro/damage, battery aggregation/persistence/anti-cycling, recipes and bound menus. | Interactive turbine appearance and connected GUI sync, real material-provider recipes, recipe-mode solar restriction, and automation/unload stress remain pending. |
| Processing/automation first batch | Five typed MMDLib machines, recipes/assets, role-filtered storage and XML screens implemented; build, 100-model resource validation, client bootstrap and JAR checks passed. Six other processing entries and the hidden turret remain pending. | All 25 required server tests passed. Processing regression tests cover parallel saved work, recipe/input changes, atomic output handling, food filters/redstone, distillation pending-output conservation, refinery timing/amounts/persistence, live power/item export, menus and recipes. | Interactive appearance/sync, real external automation/material providers and unload/reload stress; no claim that the entire section is complete. |
