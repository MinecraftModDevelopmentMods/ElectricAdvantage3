# Electric Mining Drill (`electric_drill`)

A port of EA2's drill (`ElectricDrill*`, field `laser_drill`). Behaviour and
numbers are EA2's, with electricity converted at 4 FE per unit. EA2's quirks
are kept on purpose; the few differences are listed at the end.

## Behaviour

- **Placement.** The drill faces into the block it was placed against, in any
  of the six directions. Placing it on top of the ground makes it face down.
- **Power.** It holds 2,000 FE and works while it has more than 96 FE and no
  redstone signal. Each tick of mining costs 96 FE.
- **Power update.** Targeting, re-checking, moving, export and beam damage run
  every 8 ticks, staggered by position, as EA2's Power Advantage power update
  did. Mining progress runs every tick.
- **Targeting.** The drill looks along its facing for up to 64 blocks. It passes
  through air and replaceable blocks, including water, lava and tall grass,
  and stops at the bottom and top of the world. The first solid block it finds
  becomes the centre of a **3×3 face** across the drill's axis. An unbreakable
  centre block, such as bedrock, ends the range instead.
  - Drops for all nine blocks are worked out when the face is targeted, with no
    tool: no Silk Touch or Fortune.
  - If any of the nine blocks changes, the face is dropped and targeted again
    from zero.
- **Mining time.** The total is the sum over the nine blocks of
  `max(32 × hardness, 16)` ticks. Air counts as 0. Nine stone take 432 ticks,
  about 21.6 s and 41,472 FE. Water and lava have hardness 100, so each one in
  the outer ring adds 3,200 ticks, and the fluid is removed with the face.
- **Stalling.** Progress goes up only when the drill has power, all of the
  face's drops fit in its six output slots together, and the centre can still
  be broken. Otherwise progress goes down by 1 per tick.
- **Completion.** Every breakable block of the face is set to air (containers
  spill their contents), the drops go into the output slots, and the drill
  targets the next face.
- **Movement.** At the end of its range, with at least 1,000 FE stored, the
  drill moves onto the first adjacent Electric Scaffold. It checks down, up,
  north, south, west, east, in that order.
  - It takes the scaffold's place, keeping its inventory and energy, and the
    move costs 1,000 FE.
  - Its old position becomes an Electric Cable, so the trail stays connected to
    power. It can't move back along the cable.
  - The block in front of its old position becomes a Power Advantage steel
    frame. This overwrites whatever was there, bedrock included.
- **Export.** Every power update it pushes one item into an inventory directly
  behind it, opposite its facing. That inventory doesn't move with the drill.
  Once the drill has moved, items stay in its slots until they fill and it
  stalls.
- **Redstone.** A signal stops the drill, resets progress, drops the target and
  stops export.
- **Beam damage.** While the drill is working on a face, every living entity in
  the box from the face's corner to the far corner of the drill is set on fire
  for 3 s and takes 2 `electricadvantage:laser_drill` damage each power update.
  The box is EA2's: for a drill facing down, north or west it includes the
  drill and the target block; facing up, south or east it covers only the gap
  between them.
- **Comparator.** None. EA2's comparator counted input slots and the drill has
  none, so it always read 0.
- **Screen.** Energy, mining progress, the six output slots, and status: idle,
  mining, output full, end of range, waiting for power, or redstone.

## Rendering

[LaserDrillRenderer](../src/main/java/zone/moddev/mc/electricadvantage/client/LaserDrillRenderer.java)
draws EA2's beam: two crossed full-bright quads from the centre of the drill to
the centre of the face, using EA2's `laser_beam` texture. The server sends only
the beam length, when it changes. The block model, textures and the six
`facing` × `active` blockstates are EA2's.

## Differences from EA2

- **Progress survives a reload.** EA2 saved the target but started its
  progress from zero after a reload.
- **Moving costs energy.** EA2 took the move cost from the old drill after
  copying its energy to the new one, so in practice moving was free.
- **No overflow loss.** EA2 checked each drop on its own and threw away
  whatever didn't fit. EA3 waits until all the drops fit.
- **No chunk loading.** EA2's scan loaded chunks ahead. EA3 waits until the
  chunk ahead is loaded.
- **Redstone takes effect at once.** EA2 read redstone only on power updates.
- **Death message.** EA2's "laser" message was shared with the turret. The
  drill now has its own: "X was vaporized by a mining laser".

## Configuration

Every number is `block_entity_data` in
`mmdlib/standalone/block/electric_drill.json`: capacity, input rate, mining and
move costs, range, mining time factor, power update interval, beam damage and
burn time. The recipe is EA2's: power supply unit and block of diamond over
steel plate, steel frame, steel plate.

## Verification

`DrillGameTests` covers:
- targeting the first solid block, the 3×3 face's mining time, and 96 FE per
  tick
- all nine drops reaching the drill, and one item per power update going to a
  chest behind it
- bedrock ending the range with no scaffold present
- progress falling while the outputs are full, a changed block resetting the
  face, and redstone stopping the drill without using power
- refusing to move below 1,000 FE, then moving onto scaffold: cable left
  behind, bedrock in front replaced with a steel frame, inventory and energy
  kept less the move cost, and nothing dropped
- the beam reaching the face and burning a pig standing in it
- target, progress, inventory and energy persisting

Each test runs in its own batch and points at a bedrock wall so the 64-block
scan stays inside the test structure. The client bootstrap smoke passes with no
EA3 model or texture warnings.

Not yet verified in game: how the beam and model look, screen sync, real power
delivery to a moving drill through its cable trail, and long scaffold runs.
