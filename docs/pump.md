# Electric Pump (`electricadvantage:electric_pump`)

## Behaviour

1. **Pipe.** While the block under the lowest pump pipe is air, the pump places
   another `pump_pipe_electric` segment there. Each segment costs 400 FE
   (EA2: 100 electricity) and takes 11 ticks. Breaking the pump, or any segment,
   removes the pipe below it. Pipes have no drops.
2. **Find a source.** When the pipe reaches something that isn't air, the pump
   checks the pipe tip, then the four blocks beside the lowest pipe. For the
   first one holding fluid, Power Advantage's `FluidSearch.findSourceBlock`
   traces flowing fluid upstream to the source feeding it. A source the trace
   finds in an unloaded chunk is ignored.
3. **Infinite or finite.** The body is naturally infinite if its fluid is in
   the fluid tag `electricadvantage:pump_infinite` (water and lava by default)
   and `FluidSearch.countSourceBlocks` finds at least
   `pump.infiniteSourceThreshold` connected source blocks. This works like
   Create's bottomless fluids.
   - **Infinite:** fluid is taken without removing any block.
   - **Finite:** the pump removes the source through `BucketPickup`, which also
     handles waterlogged blocks. The block under the pipe goes first, and the
     pipe then extends into the hole, so a pool drains downward from the pipe
     outward, as in EA2. Fluids outside the tag are always finite and are never
     counted.
   - **Caching:** the count runs in a single tick, so the result (finite or
     infinite) is cached per pipe tip for 1,200 ticks. A pumped body only
     shrinks, so a finite result stays correct until the recheck.
4. **Rate and cost.** At most one bucket (1,000 mB) every 20 ticks. EA2 pumped
   every 32. The 20-tick gap lets small vanilla infinite-water pools refill
   between buckets. Each bucket costs 4,000 FE plus 128 FE per block between
   the pump and the source (EA2: 1,000 + 32 per block, in electricity).
5. **Tank and output.** The tank holds 4,000 mB of one fluid. The pump pumps
   only while another whole bucket of the same fluid fits. Each tick it pushes
   up to 1,000 mB into adjacent fluid handlers, including Power Advantage 3
   pipes. The tank can be drained but not filled from outside.

A redstone signal stops the pump. The comparator follows the tank level. The
screen shows energy, the tank, the pipe depth and the current status:
extending, tracing, pumping (finite or infinite), no fluid, tank blocked,
waiting for power, or stopped by redstone.

## Configuration

Server config `electricadvantage-server.toml`:

```toml
[pump]
	# 0 disables infinite sources entirely.
	infiniteSourceThreshold = 10000
```

Which fluids can be infinite is the fluid tag
`data/electricadvantage/tags/fluid/pump_infinite.json`. Every other number
(costs, intervals, tank size, trace limits, recheck time) is in
`mmdlib/standalone/block/electric_pump.json`.

## Code

- Fluid tracing is `zone.moddev.mc.poweradvantage.util.FluidSearch`, new in
  Power Advantage 3.1.0, so EA3 now requires PA3 3.1.0 or later. The
  `electricadvantage:pump_infinite` tag and the threshold config stay in EA3
  and are passed in.
- [PumpBlockEntity](../src/main/java/zone/moddev/mc/electricadvantage/pump/PumpBlockEntity.java)
  handles pipe extension, pumping, costs, the tank and export.

## Verification

`PumpGameTests` covers:
- pipe extension, cost and retraction
- finite lava losing exactly one source per 20 ticks: the one under the pipe
  first, then the pipe drops into the hole and pumps from beside it
- depth-based cost
- a 25-source water body counting as infinite at a threshold of 20, then being
  consumed with the threshold set to 0
- tank fluid mismatch blocking pumping, external fill refused and drain allowed
- redstone, the comparator, and persistence

Appearance, screen sync and real PA3 pipe export have been checked in a live
client. The one-tick cost of counting very large bodies such as oceans is not
yet measured.
