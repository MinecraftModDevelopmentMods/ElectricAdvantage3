# MMDLib and Power Advantage integration

Electric Advantage 3.1 requires MMDLib 2.0.2 and Power Advantage 3.2.

## Content

- **Items, materials and Java machine blocks** are MMDLib definitions: `mmdlib/material/`,
  `mmdlib/item/` and `mmdlib/block/` select factories, and `mmdlib/standalone/` declares instances.
  `init.ModItems` queues this namespace's definitions on a `RuntimeContentRegistrar`, which also
  supplies the `electricadvantage:materials` creative tab.
- **XML machines**: the Electric Oven, Electric Still and Petro-Plastic Refinery are declared in
  `mmdlib/machine/*.xml` and registered entirely by MMDLib. `ModItems` adds their items to the
  `materials` tab, since the registrar's tab only holds what the registrar registers. See
  [processing](processing.md).
- **Material item tags**: MMDLib 2.0.2 tags every material's items by part (`c:ingots/lithium`,
  `c:dusts/sulfur` and so on, with the `c:ingots`-style parents). EA3 writes only the tags MMDLib
  does not: ores, and the silicon and solder ingots.

## Java machines on BaseMachine

Every Java machine block entity extends MMDLib's `machine.neoforge.BaseMachine`: the furnace and
crusher (`ProcessingBlockEntity`), generators and battery array (`GenerationBlockEntity`), pump,
drill, laser turret, assembler, growth chamber and its Control Unit. MMDLib owns:

- storage persistence, through a `MachineInventoryLifecycle` registered under 3.0's save keys
  (`Items`, `Steam`, `Tank`, `Water`), so existing worlds keep their contents;
- the energy buffer, saved under MMDLib's `energy` key; each machine still reads 3.0's `Energy`;
- client sync: machines with client-only state (the drill's beam, the turret's target) send it
  through `saveClientData`/`loadClientData`;
- dropping contents when broken. `MaterialEntityBlock` calls `BaseMachine.dropContents`, so the
  blocks no longer drop items themselves. The growth chamber adds its held harvests.

The machines keep their own ticking (`afterMachineTick`), screens and capability registration; their
`AutomationInventory` has no groups, so MMDLib's side configuration drawer is not offered.

## Automation addons

`automation.ItemPorts` registers the **Item Intake Port** and **Item Output Port** as MMDLib
inventory addons (`AddonCapabilities.ADDON`), the item counterparts of Power Advantage 3.2's fluid
pumps: 16 items per tick through the faces the group is enabled on, no power. They work in any
MMDLib XML machine, including other mods'. These are generic, so they could move to Power Advantage
beside the fluid pumps.

## Why the other machines are not XML machines yet

MMDLib 2.0.2's XML machines cannot yet do what these need:

| Missing in MMDLib XML machines | Machines it blocks |
| --- | --- |
| Comparator output | Generators, battery array, pump, laser turret, Control Unit |
| More than one processor (parallel lanes) | Arc Furnace (4), Rock Crusher (3), Growth Chamber (3) |
| Slot/tank filters by tag, data map or processor | Battery array, Growth Chamber, Control Unit |
| Facing other than horizontal | Mining Drill (faces six ways) |
| A placement hook with the placer | Laser Turret (records its owner and team) |
| Energy supplied by the processor, not a fixed buffer | Battery array (charge lives on its batteries) |
| Default redstone mode | Any machine that should stop on a signal without player setup |

The assembler stays Java regardless: its catalog imports crafting recipes and returns container
remainders, which machine recipes cannot express.

## Power Advantage 3.2

- Distilling recipes are MMDLib machine recipes of type `poweradvantage:distilling`. The Electric
  Still copies them into its own type with an energy cost, so it no longer uses the deprecated
  `api.distillation.DistillingRecipe`.
- The Fluid Intake and Output Pumps move the still's and refinery's fluids, as for Power Advantage's
  Distillery.
- Electric transport still uses Power Advantage's `TransportNetworks`, and the pump uses
  `util.FluidSearch` (3.1).
