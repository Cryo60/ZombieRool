# Map Entity Workshop and Enemy Spawners

Use the workshop to create enemies for your map and configure their spawners, patrols and combat behavior.

## Create or import an enemy

1. In Creative as an operator, open **Map Config → Enemies/Bosses → Map Entity Workshop**.
2. Select an existing native enemy or a map-owned profile. Ordinary forms handle common settings; Advanced JSON handles custom cuboid models, bones, textures and animation clips.
3. Use **Import JSON** after adding profile files under `<world>/zombierool/gameplay/entities/`.
4. Use **Get egg** to obtain the selected profile. Named eggs appear under **Spawn and spawners**; the generic selector opens the catalogue.
5. Save the profile and include its textures and scripts when sharing the map. Map profiles travel with the world; installing the mod alone does not install Stratholme's characters.

## Place and configure an Enemy Spawner

Place an **Enemy Spawner** from Machines (`/give @s zombierool:map_actor_marker`) and right-click it in Creative. Select the enemy before opening its setting pages.

- **Spawning:** trigger, quantity, maximum alive, spawn radius, repeat delay in seconds and optional redstone requirement. Triggers include Game start, Wave start and Interval.
- **Behavior:** aggro radius, idle wander radius, pursuit leash, behavior and whether the enemy counts toward wave completion.
- **Statistics:** health mode, damage, movement and knife resistance. Ordinary enemies default to round-scaled health; boss profiles retain authored fixed health.

Walk uses normal round-zombie speed (0.19), Trot uses 0.23, Run uses the map's sprint speed, and Custom uses the entered value. Knife resistance is independent of health: Current round, Fixed normal-knife hits, or Health and armor. Bowie keeps its bonus.

**Melee** acquires eligible hostile targets. **Guard** returns to its post after pursuit. A positive idle radius lets guards patrol; zero keeps them stationary. **Wander without aggro** does not attack spontaneously, but a successful hit provokes the struck enemy. **Script** delegates custom movement to Lua.

The idle anchor keeps wandering around the original spawn; the separate leash controls pursuit distance. Aggro radius controls initial detection, not the full chase. Blocked patrols request another path rather than teleporting.

Changing the enemy preserves shared spawner settings. Saving behavior settings also updates its living enemies. **Ctrl + middle-click** copies complete block data; ordinary middle-click picks a blank spawner. Breaking the marker removes its spawn rule. Spawners do not keep distant chunks loaded.

## Factions, appearances and combat

Profiles can assign hostile factions. In Stratholme, Scourge and Risen fight one another as well as eligible players. Creative, spectator, downed and Zombie Blood-protected players are excluded from targeting. Spawner enemies resist knockback; ordinary enemies have no name tags.

A profile supports a default appearance plus up to four alternatives. Each spawn selects one uniformly and retains it after saving/reloading. Appearance can change model, texture, animation and voices while sharing combat logic. Stratholme's six Risen roles choose male/female appearances this way.

Use native enemy references to keep their existing models and animations. For custom models, standard clips include idle, walk, run, attack, cast and death. An attack animation alone does not apply damage.

## Add scripted behavior

**Create Lua** writes a skeleton without replacing an existing file. A chosen prefix produces `<prefix>_spawn(uuid)`, `<prefix>_tick(uuid)` and `<prefix>_death(uuid)`. Tick callbacks run every ten unpaused server ticks; three callback errors disable that callback.

Use `getMapActor`, `setMapActorTarget`, `moveMapActor`, `setMapActorCasting`, `playMapActorAnimation`, `shootMapActor`, `mapActorAttack`, `mapActorEffect` and `healMapActor`. Read exact signatures in the [Lua reference](12-Lua-and-Addons.md). Telegraph attacks before applying damage; keep callbacks short.

Test initial spawning, patrol, provocation, leash return, faction combat, wave accounting, death and match reset with two players. Keep bosses and non-wave residents outside wave quota unless their death should be required to finish the round.

[Music and hidden encounters](17-Music-Volumes-and-Hidden-Encounters.md) · [Enemies and bosses](06-Enemies-and-Bosses.md) · [All guides](INDEX.md)
