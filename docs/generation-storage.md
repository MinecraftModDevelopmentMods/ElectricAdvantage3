# Generation and storage

The four blocks are MMDLib XML machines (`mmdlib/machine/*.xml`) with custom
processors: `SolarProcessor`, `HydroProcessor` and `SteamProcessor` share
`GeneratorProcessor`, and the array uses `BatteryArrayProcessor`. Balance values
are constants on those classes. See [MMDLib integration](mmdlib-integration.md).

| Block | Generation/storage behavior |
| --- | --- |
| Photovoltaic generator | Daylight curve, rain/thunder penalties, reduced indoor block-light generation; fractional generation is retained between ticks |
| Hydroelectric generator | Horizontal water flow immediately below spins a turbine and generates FE; stationary water does not |
| Steam-powered generator | Accepts PA3 steam through its fluid capability and PA3 pipes; consumes whole-FE batches only when energy storage has room |
| Battery array | Eight filtered, unstackable battery slots; capacity/charge aggregate the actual item energy capabilities, including compatible external batteries |

Generators have a 1,000-FE buffer and push up to 4,096 FE/tick into neighbours.
Hydro generation is 4 FE/tick and solar peak approximately 5.140418 FE/tick (the
legacy peak per eight-tick update expressed per tick). Redstone disables
generation and export, as in 3.0. Arrays still accept charging under redstone but
stop discharging. Comparators expose fill fraction, while the solar comparator
follows its current output as a share of full sun.

MMDLib XML machines only take energy in through their energy group, so
generators and arrays cannot be drained by other mods' extractors; they push
their energy out themselves. MMDLib's Gearbox Upgrade makes a generator run its
work more often, so up to 2x output with eight; Capacitor Upgrades enlarge a
generator's buffer.

## Steam units

The supplied ratio is 8 steam/RF and 0.25 electricity/RF:

- 32 steam per electricity.
- 4 FE/RF per electricity.
- Consequently **8 mB steam per FE**, using PA3 steam-fluid millibuckets as steam units.

`SteamProcessor.STEAM_PER_FE` is that batch size. Thus 32 mB produces
4 FE; 7 mB waits for another mB. Full buffers, redstone and invalid fluids cannot
consume steam. There is no periodic steam-decay loss. Tank contents and generated
energy persist. This replaces EA2's inverted `31.25 electricity per steam` constant.

## Backup storage and transport

Primary generators export through the existing electric cable/scaffold/switch
network. Their side traversal rotates across ticks. Arrays discharge after all
ordinary block-entity ticks, sharing the electric network's transfer budget.
Normal consumers and LEDs are served before charging arrays; backup dispatch
excludes every array endpoint (including direct neighboring arrays), preventing
array-to-array cycling through EA3's network. Other mods can still deliberately
connect their own extraction and insertion automation.

Array charge stays on each battery ItemStack: `BatteryArrayProcessor` supplies
the machine's energy store, reading and writing the batteries' own charge.
Save/reload and breaking the array preserve those stacks and their charge; there
is no duplicate machine energy pool. The processor admits only items with an
energy capability, for players and automation alike. The electric network
recognises an array by its position, not its capability's type.

## Turbine and screens

The hydro turbine is block-entity-owned (`HydroGeneratorEntity`) rather than a
separate world entity.
Its lifetime follows the block/chunk automatically, preventing detached turbines.
A client-only renderer orients to water flow and animates three blades. It retains
its last orientation/rotation when stopped. The server damages living entities
in the blade volume below the block every eight ticks, using the registered
`electricadvantage:propeller` damage type and translated death messages.

All four use MMDLib's generated machine screen: an energy gauge, the steam
generator's tank, and the array's eight battery slots. Battery item tooltips show
individual charge percentages.

Saves from 3.0 and 3.1 keep the steam tank (`Steam`), the array's batteries
(`Items`) and, through 3.0's `Energy` key, a generator's buffer. A solar
generator's fraction of an FE is not carried over.

Models/textures and translated block names are ported from EA2. Recipes use the
existing PSU, cable and silicon components with PA3 frames/pipes and common tags.
The retired steam governor ingredient is represented by the existing control
circuit. Machines requiring steel plates remain conditional on a material provider
supplying `c:plates/steel`; no substitute steel content is added to EA3. EA2's
apocalyptic-mode solar restriction is dropped: EA3 has no recipe modes.

## Verification

```sh
./gradlew --offline build
./gradlew --offline -PgameTests runGameTestServer
python3 scripts/validate_resources.py
xvfb-run -a ./gradlew --offline -PgameTests -PclientSmoke runClient
```

Generation tests cover steam conversion/remainders/backpressure/redstone and
persistence; real PA3 steam-pipe-to-generator-to-electric-network-to-LED ticking;
solar daylight/night/weather/redstone/comparator behavior; hydro flow/output and
blade damage; mixed battery aggregation/filtering/persistence; array-to-array
isolation and backup discharge; all four crafting recipes; loaded XML and bound
server menus/slot filters. Crafting fixtures supplying external steel plates and
chests are test-only and excluded from release artifacts.

The optional client smoke uses `build/client-smoke`, handles first-run onboarding,
and exits after reaching the title screen. It checks client bootstrap and asset
loading, not actual in-world turbine rendering or a connected screen session.
Appearance, live screen synchronization and automation/unload have since been
checked in a live client. The supplied PA3 JAR also reports missing `still_on` /
`still_off` model resources during client loading; these belong to its distillery.
