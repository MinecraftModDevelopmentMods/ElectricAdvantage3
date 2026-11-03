# Processing and automation: first implementation batch

Five machines now share the `processing` MMDLib block module, one typed
`ProcessingSettings` codec, block-entity factory and runtime implementation.
Standalone JSON selects behavior, input/output counts, costs, timings, buffers,
fluid acceptance and plastic output. Each machine has migrated EA2 models/textures,
a crafting recipe and an MMDLib XML screen opened by `mmdlib:open_menu`.

| Machine | Inputs / outputs | Processing |
| --- | --- | --- |
| Electric Arc Furnace | 4 parallel item inputs / 6 outputs | Current smelting recipes; 200 ticks and 8 FE per tick per active lane |
| Electric Rock Crusher | 3 parallel item inputs / 6 outputs | MMDLib crushing recipes; 200 ticks and 12 FE per tick per active lane |
| Electric Oven | 1 item input / 1 output | Smelting recipes whose result has a food component; 100 ticks at 4 FE/tick |
| Electric Distiller | 1 input tank / 1 output tank, 1,000 mB each | PA3 `DistillingRecipe.find`; recipe duration, 16 FE per working tick |
| Petro-Plastic Refinery | 2,000-mB input tank / 1 item output | 100 mB per plastic ingot over 100 powered ticks at 12 FE/tick |

All five retain the legacy 500-FE numeric buffer size and accept FE through the
existing cable network or ordinary sided energy capability. `rate` sets intake
rate. Consumption does not use a separate hidden energy store.

## Inventory and work conservation

MMDLib ItemStorage roles enforce input-only insertion and output-only extraction.
The same filters apply to real XML menu slots. Recipe lanes retain independent
progress, recipe IDs and input item/components, so changing an input cannot reuse
another recipe's progress. A count change to the same input does not invalidate it.
Recipe changes on datapack reload are observed on the next lookup.

A result must fit completely across the output slots before progress spends
energy. Completion inserts the full result and consumes exactly one input.
Parallel lanes see the preceding lane's committed output. Full output never
inserts a partial result, consumes input or spends energy. Progress and inventory
are saved through registry-aware NBT. Loss of power reduces ordinary item-lane
progress by one tick; the oven resets progress under redstone, matching EA2.

Output item export rotates across loaded neighbors, with a shared per-machine
item count budget. Input slots are excluded from exports. Hoppers and other mods
can independently insert inputs or extract outputs through the item capability.
Machine removal drops its inventory on the server. Models distinguish active,
idle-but-powered, and unpowered states.

## Fluids

The fluid capability fills only the input tank and drains only the output tank.
An electric distiller accepts fluids recognized by PA3 recipes. It reserves a
whole input batch when work begins, then saves the pending output and timer.
A removed recipe does not erase an already-started batch. Finished pending output
waits for space without consuming extra power, and completes once space returns.
Only output fluid is exported into PA3 pipes or adjacent fluid handlers; no
separate EA fluid-transport network is introduced.

The refinery's `accepted_fluids` list defaults to PA3 crude and refined oil.
`fluid_per_item`, `ticks`, `cost` and `result_item` are data settings. Input fluid
is consumed at completion, after checking that the item result fits. Changing
fluid identity resets its work; saves retain the current fluid identity and timer.
Redstone pauses fluid-machine work without losing input or pending results.

## Verification and remaining work

```sh
./gradlew --offline build
./gradlew --offline -PgameTests runGameTestServer
python3 scripts/validate_resources.py
xvfb-run -a ./gradlew --offline -PgameTests -PclientSmoke runClient
```

Processing tests cover four saved/resumed smelting lanes; three crushing lanes
and exact output counts; atomic full-output behavior; oven food filtering, changed
inputs and redstone; distillation input/output roles, saved pending work and
backpressure; both refinery oils, timing, amounts, persistence and full outputs;
live PA3 electric supply through cable to a furnace exporting into a chest; XML
menu slot counts; and all five crafting recipes. Test-only material tags make
conditional steel recipes available without adding production steel content.

Machine appearance, connected screen synchronization, external automation and
unload/reload have been checked in a live client.

The assembler is documented in [assembler.md](assembler.md) and the growth chamber
and controller in [farming.md](farming.md). The remaining processing/automation
entry is the moving mining drill; the pump is in [pump.md](pump.md) and the laser turret in [turret.md](turret.md). The hidden hostile turret variant also remains pending. These are
tracked individually in TODO.md; this first batch does not claim full parity for
the processing/automation section.
