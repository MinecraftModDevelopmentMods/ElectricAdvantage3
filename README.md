# Electric Advantage 3

Minecraft 1.21.1 mod using NeoForge 21.1.265, ModDevGradle, and Java 21.
Root Java package: `zone.moddev.mc.electricadvantage`.

Required dependencies (compile and development runtime):

- `libs/mmdlib-2.0.0-alpha.jar` (MMDLib 2.0)
- `libs/PowerAdvantage-1.21.1-3.0.0.jar` (Power Advantage 3.0)
- `zone.moddev.mc.orespawn:OreSpawn:4.1.0.121012` (MMD OreSpawn 4, from ModDevZone Maven)

These dependencies are not bundled into the mod JAR. Install them alongside
Electric Advantage in a regular Minecraft instance. Replace the file dependencies
in `build.gradle` with Maven coordinates when MMDLib and Power Advantage are published.
The ModDevZone repository is configured with an OreSpawn-only group filter; expand
that filter alongside the dependency changes once their coordinates are known.

Use Java 21 and the checked-in Gradle wrapper:

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runData
```

The dedicated server requires accepting Minecraft's EULA in `run/eula.txt`.
Build artifacts are written to `build/libs/`.

All 17 electronics/material and battery items and the seven ore/infrastructure
blocks are implemented with textures, translations, tags, and initial recipes.
Lithium and sulfur use restricted MMDLib material definitions; other components
use standalone definitions. See [material selections and ID mapping](docs/materials.md).
All batteries use the same MMDLib-backed energy core; see [batteries](docs/batteries.md).
Cable/scaffold/switch electricity transport uses the PA3 shared transport core;
LED lighting, generated pump pipe, and OreSpawn 4 deposit generation are implemented.
See [infrastructure behavior and verification](docs/infrastructure.md).
Four generation/storage machines now share data-driven settings and MMDLib XML
screens; see [generation and storage](docs/generation-storage.md).
The furnace, crusher, oven, distiller and refinery now share a processing core;
see [processing behavior and remaining machines](docs/processing.md).
Conditional recipes require compatible material tags; remaining automation machines, recipe modes,
and complete survival acquisition remain pending.
Track implementation and verification in [TODO.md](TODO.md).

Run the isolated server integration test (excluded from release JARs):

```sh
./gradlew -PgameTests runGameTestServer
```

`EA2/` is an untouched archive for reference only; it is not
included as a Gradle project or source directory.

Validate model parents and inherited texture paths with `python3 scripts/validate_resources.py`.
