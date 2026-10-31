# Ores and infrastructure

All infrastructure blocks are declared under `mmdlib/standalone/block`. The shared
`electric_infrastructure` module and block-entity factory use typed settings for
kind, buffer capacity, transfer rate, LED interval and LED consumption. New data
variants reuse the same Java behavior and MMDLib registration path.

| Block | Current behavior |
| --- | --- |
| Electric power cable | Six-face connections/shapes; input FE capability; buffered network transmission |
| Electric scaffold | Same electricity transport, full-block collision, legacy frame/cable model |
| Electric switch | Right-click toggles open/closed; rising redstone closes, falling redstone opens; retained capability handles obey current state |
| LED bar | Input-only endpoint, consumes energy, synchronizes lit state and emits level-15 block light |
| Pump pipe | Generated-only shaft, search-only item and no drops; breaking/removing a segment clears contiguous loaded segments below |

Electric transport uses PA3 `TransportNetworks`, `TransportAdapter`, cached capability
endpoints, route graphs and fair transfer budgets. EA3 supplies the electricity
adapter and dispatches buffered FE through these routes. Sources push FE into a
conductor, as PA3's infinite power block does; there is no automatic extraction
from passive energy stores. Consumers receive energy through their sided FE
capability. Cable input capabilities do not allow extraction, preventing other
cables or machines from pulling transit energy around the network.

Each conductor currently has a 4,096-FE buffer and 4,096-FE transfer rating, supplied
in JSON. PA3's component budget limits total network throughput to its minimum
conductor rating. This is conservative shared throughput, rather than a separate
allowance per cable or per destination. Every conductor is retained as a route
vertex because any conductor can accept pushed FE. Chunk events, topology changes,
capability invalidation, and dimension unloading are forwarded to the shared core.
Cached paths are checked against loaded, enabled conductors before transfer.
Per-dimension transfer operations are bounded; reentrant dispatch is refused.

LEDs are preferred destinations and do not relay power. Their initial FE balance
is one FE per eight ticks with a four-FE buffer, all configurable in their
standalone definition. EA2's fractional energy cost is not a direct physical FE
conversion. Lit state drives vanilla lighting and blockstate models. Switches
retain buffered energy while open but neither accept nor transmit it. A manual
switch toggle remains until another toggle or a redstone edge occurs.

Recipes include cable casing alternatives (plastic/rubber; copper ingot/rod),
scaffold from cable plus PA3 steel frame, the lever-controlled switch and LED bar.
Unavailable common ingredient tags disable the corresponding recipes. Cable is
added to `c:wires`, supporting component/PSU recipes. The pipe is intentionally a
generated-only block with an item visible in creative search only. Pump deployment
and drill movement will be integrated when those machines are implemented.

## Ores

MMD OreSpawn 4 is a required separate mod. Gradle pins
`zone.moddev.mc.orespawn:OreSpawn:4.1.0.121012` from the ModDevZone releases
repository, filtered to the `zone.moddev.mc.orespawn` group. This target-qualified
artifact is the Minecraft 1.21.1 NeoForge build, rather than the repository's
cross-target `latest` version. MMDLib and Power Advantage remain local JAR dependencies.

The bundled ore-only provider is
[`data/electricadvantage/orespawn/provider.json`](../src/main/resources/data/electricadvantage/orespawn/provider.json).
It follows the copied [OreSpawn provider guide](../other-docs/orespawn4-docs/PROVIDERS.md).
OreSpawn discovers the JSON directly; EA3 does not write generated configuration
files or register native deposit features/biome modifiers. The previous six
configured-feature, placed-feature and biome-modifier resources were removed.

Both ore rules target Overworld stone ore replaceables at Y=1..32 with uniform
heights and the vein pattern. Lithium uses 0.125 expected attempts per chunk and
an inclusive 6–10 block budget; sulfur uses one attempt and an 8–24 block budget.
These ranges restore EA2's size/variation intent (8 +/- 2 and 16 +/- 8). They are
placement budgets, so blocked candidates may yield fewer actual blocks.

The provider adds no rocks, strata or terrain replacements. Retrogen is disabled
in both rules by default. OreSpawn owns configuration and world-profile handling:
pack authors may override defaults through `config/electricadvantage-orespawn.json`,
and existing worlds use their saved `serverconfig/orespawn-worldgen.json` profile.
Changing bundled defaults alone does not rewrite an established world's profile.

## Verification

Run:

```sh
./gradlew --offline build
./gradlew --offline -PgameTests runGameTestServer
python3 scripts/validate_resources.py
```

Tests cover live ticking from PA3 infinite power through cable/scaffold/switch to
LED, open-switch isolation and reopening, redstone controls and input limits,
connection state, full scaffold collision, saved buffered energy, pipe cleanup, search-only visibility
and no drops. The OreSpawn integration test checks that the active world profile
contains both provider rules with the expected blocks, heights, frequencies and
host tags, and confirms the retired native features are absent. This verifies
provider acceptance/configuration, not actual deposit placement in new chunks.
Existing material/battery tests remain part of the same suite. The resource
validator checks model parents and inherited texture references against local
library JARs, including modern atlas paths.

Interactive appearance, switch/LED lighting over a real client connection, actual
new-chunk ore distribution, external automation, unload/reload stress and large
network performance remain separate checks. Pump and mining-drill integration
remain pending those machines.
