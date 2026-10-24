# Spawner load and patrol behavior

Map spawners share an appearance budget: at most eight successful appearances and 128 position attempts per server tick, with a soft two-millisecond scheduling budget. A rotating cursor visits markers fairly. Unfinished groups retain their remaining amount and placement cursor; quota is consumed only by successful appearances.

The living map-actor limit is 512. Floor spawners use at most 480 slots, leaving 32 available for scripted encounters and bosses. Ordinary wave enemies retain their own wave population rules. Corpses do not occupy living slots. A population snapshot is built once per tick and updated as actors spawn, die or leave. Spawn-position offset lists are cached rather than sorted for every spawner.

Idle patrol requests use a fair queue and a separate pathfinding allowance, independent of pursuit and hidden encounters. A positive wander radius permits local guards to patrol; zero keeps them stationary. Requests are staggered across entities. A patrol with no progress for two seconds requests a replacement path. Collision, ground, navigation restrictions and spawn leash remain enforced. No enemy is teleported to resolve a blocked patrol.

Saving a placed spawner's behavior settings updates its currently living enemies. Reloaded native and custom enemies also restore current radius, behavior, speed and leash settings, including marker overrides.

Gore is indexed separately from living enemies. New piece/pool allocation is capped at 1,024 gore pieces and 512 blood decals per level. Older pieces begin fading under pressure at 75% of these limits. More than 256 lingering corpses also triggers pressure fading. Normal three-minute cleanup remains staggered; cleanup never removes living enemies or consumes wave kills. Existing saved/external visual entities above the cap fade progressively rather than all disappearing on load.

## Verification

`python tools/tests/run-stratholme-probe.py --stress` runs a disposable production Forge server with 500 placed spawners, batches of eight, radius-four guards (one quarter of the spawners use Geist), an aggro radius of six, 24 real wave zombies, 300 initial corpses, repeated bursts requesting 1,100 gore pieces and 600 blood decals, and 32 reserved scripted appearances. It measures 400 real server ticks after warmup in each of two phases; the second phase follows wave kills and 128 freed map-actor slots. It verifies actual patrol movement, replacement groups, corpse pressure, allocation caps, reserved slots and wave accounting.

The current run is retained in `build/spawner-load/probe.log`. Tick durations measure server simulation; they do not measure client FPS or GPU rendering. A 50 ms tick budget corresponds to 20 server ticks per second.

`python tools/tests/run-stratholme-probe.py` checks hidden encounters and live spawner edits on an isolated copy of Stratholme. Neither test writes to the player's world.
