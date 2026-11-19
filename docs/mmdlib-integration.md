# MMDLib and Power Advantage integration

Electric Advantage 3.1 requires MMDLib 2.0.3 and Power Advantage 3.2.

## Content

- **Items, materials and infrastructure blocks** are MMDLib definitions: `mmdlib/material/`,
  `mmdlib/item/` and `mmdlib/block/` select factories, and `mmdlib/standalone/` declares instances.
  `init.ModItems` queues this namespace's definitions on a `RuntimeContentRegistrar`, which also
  supplies the `electricadvantage:materials` creative tab. MMDLib lists the XML machines in that tab too.
- **Machines** are MMDLib XML machines, declared in `mmdlib/machine/*.xml` and registered entirely by
  MMDLib, except the assembler (below).
- **Material item tags**: MMDLib tags every material's items by part (`c:ingots/lithium`,
  `c:dusts/sulfur` and so on, with the `c:ingots`-style parents). EA3 writes only the tags MMDLib
  does not: ores, and the silicon and solder ingots.

## XML machines

| Machine | Processors | Entity |
| --- | --- | --- |
| Electric Oven, Still, Petro-Plastic Refinery | one MMDLib recipe processor | `ElectricMachineEntity` |
| Electric Arc Furnace, Rock Crusher | one recipe processor per lane | `ElectricMachineEntity` |
| Electric Pump | `PumpProcessor` | `ElectricMachineEntity` |
| Photovoltaic, Hydroelectric, Steam Powered Generator | `SolarProcessor`, `HydroProcessor`, `SteamProcessor` | `ElectricMachineEntity`; the hydro generator's `HydroGeneratorEntity` keeps the turbine's spin |
| Electric Battery Array | `BatteryArrayProcessor`, which also supplies the energy store | `ElectricMachineEntity` |
| Laser Turret (and its hidden variant) | `LaserTurretProcessor` (`$Evil`) | `LaserTurretEntity` |
| Electric Drill | `DrillProcessor` | `DrillEntity` |
| Growth Chamber, Control Unit | `GrowthChamberProcessor`, `GreenhouseControllerProcessor` | `GrowthChamberEntity`, `GreenhouseControllerEntity` |

The XML declares each machine's storage, processors, appearance, comparator source and screen;
custom processors (MMDLib `StoredProcessor`s) hold the machine's work and its saved and synced state.
`machine.ElectricMachines` registers each machine's block entity class with MMDLib from the mod
constructor. Every one extends `machine.ElectricMachineEntity`, which adds:

- **Redstone.** New machines read redstone on every face, so a signal stops them as in 3.0. Players
  can change each face in the redstone drawer. (MMDLib XML machines otherwise ignore redstone until a
  face is set to read it.)
- **Saves from 3.0.** 3.0 saved the energy buffer as `Energy` and had no redstone settings; those
  saves load with the buffer and with every face reading redstone.

Subclasses add what a processor cannot hold: client-side animation for renderers (turret aim, turbine
spin), moves of the whole block (the drill), work outside the processor's tick (the Control Unit takes
in fertilizer even under redstone), and state saved under 3.0's keys (growth chamber lanes, the
Control Unit's pools, the turret's owner and team).

Each machine keeps its block and block-entity ID, and its storage keeps 3.0's save keys and slot
order, so existing worlds load into the XML machines. What each machine loses on upgrade, if
anything, is in its own page.

**Moving resources.** Machines with plain inputs and outputs use MMDLib automation addons, configured
per group in the side configuration drawer: EA3's Item Intake and Output Ports (`automation.ItemPorts`,
16 items per tick, no power) and Power Advantage's Fluid Intake and Output Pumps. The generators and
battery array push FE into neighbours, the drill pushes into the inventory behind it, and growth
chambers push into neighbours other than chambers; those are part of how those machines work.

**Shared upgrades.** MMDLib's Gearbox Upgrade runs every processor's work more often (up to 2x), which
applies to pumping and generation as well as recipes; the Capacitor Upgrade enlarges built-in
buffers. The battery array's store is its batteries, so capacitors do not apply to it.

## The assembler stays Java

The Electric Fabricator is a Java block entity on MMDLib's `machine.neoforge.BaseMachine`: its catalog
imports crafting recipes and returns container remainders, which machine recipes cannot express.
MMDLib owns its storage persistence (under 3.0's keys), client sync and dropping its contents.

## MMDLib limits worth knowing

- An XML machine's energy group can only be an input. Generators and the battery array push their
  energy out themselves; other mods cannot pull FE from them.
- `face-texture` rules need the `cube-front` preset; other appearances switch textures with model
  parts.
- MMDLib generates `block/<machine>` and, with parts, `block/<machine>_base`; authored models must
  not use those names.

## Power Advantage 3.2

- Distilling recipes are MMDLib machine recipes of type `poweradvantage:distilling`. The Electric
  Still copies them into its own type with an energy cost.
- The Fluid Intake and Output Pumps move the still's, refinery's and pump's fluids, as for Power
  Advantage's Distillery.
- Electric transport uses Power Advantage's `TransportNetworks`, and the pump uses `util.FluidSearch`.
- Power Advantage 3.2.0 adds its XML machines to its tab by hand, and MMDLib 2.0.3 now adds them too,
  so building the creative tabs fails until Power Advantage drops that code. The game tests that
  rebuild the tabs fail for that reason.
