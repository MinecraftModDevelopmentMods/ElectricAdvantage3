# Laser Turret (`laser_turret`, hidden `laser_turret_evil`)

A port of EA2's turret. Behaviour and numbers are EA2's, with electricity
converted at 4 FE per unit.

## Behaviour

- **Power.** The turret works only while it has stored at least one full
  shot (4,000 FE) and has no redstone signal. While working it drains
  4 FE/tick. It holds 8,000 FE, enough for two shots.
- **Targeting.** Every 4 ticks (staggered per turret) it looks within 16 blocks
  of its optic, 0.75 blocks above the base, for the nearest valid target it can
  see. The target is dropped when it dies, leaves range, goes out of sight, or
  the turret loses power or gets a redstone signal.
  - **Normal turret:** monsters (`Enemy`), and players outside the turret's
    scoreboard team. The team is the owner's team when the turret was placed.
    A turret without a team never targets players.
  - **Hidden `laser_turret_evil`:** every living entity that isn't a monster
    or an ambient mob such as a bat, including players, animals and villagers.
    It has the same name, no recipe, and is in no creative tab, search
    included, as in EA2.
  - **Both:** creative and spectator players, and armor stands, are never
    targeted. EA2 had no armor-stand check.
- **Firing.** The turret charges for 31 ticks. On tick 7 of the countdown it
  fires along the line to the target's centre, out to 32 blocks or the first
  solid block. It then fires every 30 ticks while the lock holds. Every living
  entity the beam crosses takes 7 `electricadvantage:laser` damage and burns
  for 3 seconds. Fire is placed under victims standing on solid ground. That
  is EA2 behaviour; set `ignite_ground` to false to stop it.
- **Owner.** Victims are set to retaliate against the owner, as EA2 did. The
  death message then names the owner ("X was vaporized by Y's laser turret").
  Kills don't count as player kills.
- **Comparator.** 15 while locked on a target, otherwise up to 8 in
  proportion to stored energy.
- **Screen.** Energy, status (scanning, target name, waiting for power,
  redstone) and team.

## Rendering

[LaserTurretRenderer](../src/main/java/zone/moddev/mc/electricadvantage/client/LaserTurretRenderer.java)
draws EA2's half-block head from `textures/entity/laser_turret.png`. The lens
is dark when the turret isn't working. While locked, the beam is two crossed
full-bright quads. Animation runs on the client, as in EA2: an idle sweep at
2°/tick, tracking the target at 9°/tick, and drooping to -30° when off. The
server sends only the target ID and the charge timer, when they change. The
block model is EA2's base plate and shaft.

## Configuration

- Server config `turret.laserSound` (EA2's `laser_sound` option): the sound
  played when a turret fires, default `minecraft:block.note_block.bass`.
  Unknown IDs fall back to the enderman teleport sound, as in EA2.
- Everything else is `block_entity_data` in
  `mmdlib/standalone/block/laser_turret*.json`: costs, damage, burn time,
  ranges, timings, scan interval, `ignite_ground` and `evil`.

## Verification

`TurretGameTests` covers:
- locking the nearest monster
- the beam hurting every entity in line and setting them on fire
- ground ignition, shot cost, and standing down below a full shot
- passive mobs ignored, walls blocking sight, and redstone stopping both
  targeting and the idle drain
- the evil variant preferring a cow over a closer zombie, and appearing in no
  creative tab
- team rules, creative-player immunity, and owner/team persistence

Each test runs in its own batch so turrets can't reach neighbouring tests. The
client bootstrap smoke passes with no EA3 model or texture warnings.

Not yet verified in game: how the head, beam and animation actually look, the
death messages, and multiplayer team behaviour.
