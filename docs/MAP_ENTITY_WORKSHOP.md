# Map Entity Workshop

The workshop and this guide use English. Open **Map Configuration → Enemies/Bosses → Map Entity Workshop**. The regular forms require no JSON or Lua. Advanced JSON controls appearance and animations; Lua controls custom attacks.

## Map content and mod features

The mod provides a generic encounter engine, the enemy spawner, native enemy references, weapon support and the editor. Stratholme owns its boss and enemy profiles, textures and Lua callbacks. Moving the save carries these characters with it. The Scourge and Risen examples are not global entity registrations.

Profiles: `<world>/zombierool/entities/*.json`.
Textures: `<world>/zombierool/skins/map_actor/*.png`.
Scripts: `<world>/zombierool/scripts/*.lua`. Legacy `<world>/zr_scripts/` remains readable; a matching canonical filename takes precedence.

**Import JSON** reloads profile files. **Get egg** supplies the selected profile's egg. Named map eggs appear in **Spawn and spawners**; the generic Enemy Selector opens the catalogue. Native enemies use their existing entity classes and animations. **Zombie** is the ordinary native zombie; **Scourge Zombie** is the WoW variant.

The mod's **Spawn and spawners** category also includes the existing native enemies: Scourge Ghoul, Scourge Zombie, Scourge Skeleton, Scourge Abomination, Geist, Panzer, Brutus, Pentagon Thief, Napalm Minion, Skeleton Minion, George Romero and Astronaut. Each has one named, colored egg, alongside the original Zombie, Crawler, Hellhound and White Knight eggs. The map's configured Geist/Crawler and Pentagon Thief/Hellhound replacements still apply to the original eggs.

## Enemy spawner

Place an **Enemy Spawner** from **Machines**, or use `/give @s zombierool:map_actor_marker`. Right-click in creative as an operator.

- **Spawning**: enemy, trigger, quantity, maximum alive, radius, repeat timing and redstone requirement.
- **Behavior**: aggro radius, idle wander radius, independent pursuit leash, behavior and wave quota.
- **Statistics**: health, damage, movement and knife resistance.

Changing the selected enemy preserves all shared settings. Copying/pasting the block preserves the complete spawn rule, including cloning through block-entity data. The marker has no collision and refuses overlapping spawn positions.

**Keep idle wandering near spawn** only anchors idle movement. It does not shorten pursuit or change aggro radius. The independent **Leash radius** still governs when a chasing enemy returns. Disable the idle anchor to let the center of its idle wandering move with the entity.

**Wander without aggro** does not acquire targets spontaneously. A successful hit provokes only the struck entity, even outside its aggro radius or line of sight. It does not alert neighbors. The attacker can be a player or a hostile faction member. The attacker stays targeted outside detection range and behind cover. Only the configured pursuit leash, an invalid/dead target or a game pause ends pursuit.

**Melee** acquires the nearest eligible hostile target. **Guard** watches its post and returns after a chase. **Script** delegates movement to Lua.

**Movement** defaults to **Walk** (ordinary round-zombie speed, 0.19). **Trot** uses 0.23 and a faster walk cycle. **Run** uses the map's configured round-zombie sprint speed and run animation. **Custom speed** uses the numeric speed field.

**Health scaling** defaults to **Round enemy health** for new spawners and ordinary enemies. Boss profiles retain fixed authored health. It selects fixed authored health or **Round enemy health**, using the map's zombie base health, current round progression and maximum cap at spawn time. Knife resistance is independent: **Current round**, **Fixed normal-knife hits**, or **Health and armor**. Bowie retains its bonus.

Spawner enemies cannot be knocked back. There is no recoil toggle. Ordinary enemies have no nametags. A map actor counts toward the wave only when its wave-quota option is enabled.

Triggers are **Game start**, **Wave start**, and **Interval**. The repeat delay is displayed in seconds. Spawners do not keep distant chunks loaded. Breaking a spawner removes its rule.

## Factions

Stratholme's Scarlet undead belong to **The Risen**, with green glowing eyes and green smoke. Balnazzar belongs to the Risen. Scourge Ghoul, Scourge Zombie, Scourge Skeleton, Scourge Abomination, Crypt Fiend and Rivendare's minions belong to the Scourge; Baron Rivendare keeps his name.

Risen and Scourge can acquire and damage one another. Acquisition uses the same radius, line-of-sight, idle/guard/wander behavior and pursuit rules as player acquisition. They also remain hostile to eligible players. Creative, spectator, downed and Zombie Blood-protected players are excluded from enemy targeting and attacks.

## Models and animation

Advanced JSON contains `bones`, `boxes`, `texture`, `textureWidth`, `textureHeight` and `animations`. Bones support parents, pivots and rotations. Cuboids reference a bone and UV coordinates. Animation clips contain duration, loop state and keyframes. Standard clips are `idle`, `walk`, `run`, `attack`, `cast` and `death`. Rendering an attack animation alone does not inflict damage.

Use the existing Scourge, Abomination, Pentagon Thief and Romero cuboid conventions when building characters. The Risen use male and female cuboid models driven by the original undead player skeletons and animation tracks. Their green eyes, Scarlet flame tabards, armor and equipment remain map-owned. Native ghoul/zombie/skeleton references still reuse the original mod entities.

Baron Rivendare's mounted death uses the local WoW Death sequence (FileDataID 124984, animation 1, 2.333 seconds), including rider translation and ground contact. Selected local model/audio provenance is recorded under `assets-source/26.7/map-actors`.

## Lua

**Create Lua** writes a callback skeleton without replacing an existing script. Callback names use `<prefix>_spawn(uuid)`, `<prefix>_tick(uuid)` and `<prefix>_death(uuid)`. Tick callbacks run every ten server ticks while unpaused. Save and reload scripts in the Lua editor.

Available APIs include `getMapActor`, `getMapActorProfiles`, `defineMapActor`, `spawnMapActor`, `despawnMapActor`, `setMapActorTarget`, `moveMapActor`, `setMapActorCasting`, `playMapActorAnimation`, `getMapActorData`, `setMapActorData`, `shootMapActor`, `mapActorAttack`, `mapActorEffect` and `healMapActor`. Projectiles support `shadow`, `fire` and `web`. Use timers to delay a telegraphed attack. Callbacks stop after three errors; consult Lua diagnostics.

## Stratholme balance

Rivendare retains melee, summons and shadow projectiles. Balnazzar retains his existing validated attack script. Rivendare's minions resist knockback and use ordinary round knife balance. Crypt Fiend has 40 fixed HP by default, 2 melee damage and at least 1.5 seconds between hits. Its web deals no damage, slows for two seconds, and has a seven-second interval.

## Weapons

The Risen equipment uses ordinary ZombieRool weapon slots, Pack-a-Punch, Double Tap and repairable durability, like Old Sword. The sword cleaves, the axe temporarily reduces armor, the halberd has extra reach, and the shield blocks front-facing hits. The shield has no dash. HUD labels show **Durability**. Wall purchases derive their drawing from the actual 3D model rather than the texture atlas.

The Stratholme weapon pack contains 24 ordinary weapons, including `fire_wand`, `shadow_wand`, `longbow` and `scarlet_crossbow`. Balance, models, animation curves and sound variants live in `zombierool/weapon_packs/stratholme/`. They are not offered in other maps without a map pack enabling them. See `STRATHOLME_ARSENAL.md` for the arsenal and armor stations.

## Necropolis and music

Choose **Necropolis** in the Aircraft Spawner variant menu. Altitude, size, heading and speed are editable; speed zero hovers. The model is rendered in the world and respects fog. It is decorative and has no aircraft weapons or engine sound.

Ogg files in `zombierool/audio/music/<preset>` play in a shuffled cycle. Every available track plays before the next shuffle, with no immediate repeat at a cycle boundary. Resynchronizing the same preset preserves the current track. Ambient loops are independent.

The Scarlet Sword and Shield is one weapon slot with one PAP upgrade and durability pool. Selecting it automatically equips its guard in the offhand. Switching weapons restores the previous offhand item. Left-click uses the sword; right-click blocks.

## Music volumes

Find **Music Volume** in the creative **Ambience** category, or use `/give @s zombierool:music_volume`.
Place the block at the bottom of a room and right-click it in creative. Set X/Z half-width and height, then choose a playlist. The purple outline shows the complete cuboid while editing or holding the block.

- `default` and `damned` use the normal map music presets.
- Create `zombierool/audio/music/tavern/` in the save and put OGG tracks there for a `tavern` playlist. All tracks rotate without repeating until the playlist is exhausted. Reload the world's resources/rejoin after adding files.
- The catalogue also allows an exact custom audio identifier. An exact file loops; a folder rotates its playlist.
- Music volumes are entry triggers. On first entry of the player hitbox, the selected music replaces the previous selection. Leaving does nothing. On the next entry, the saved previous selection is restored. Further entries alternate between the two selections. Each player has independent trigger state.
- If several triggers are entered together, higher priority wins, then the smallest volume. Each trigger remembers the music it replaced. Movement outside a trigger does not undo its change.
- Playback controls provide enable/disable, volume multiplier and fade duration (20 ticks = one second). `none` silences background music inside the volume.
- The cube has no collision and is invisible in survival. Copying the block with data retains every setting. Definitions remain specific to this world and dimension.

Select an enemy before opening Spawning, Behavior or Statistics. Rivendare is Scourge; Balnazzar and The Risen are Risen. Acquisition is checked every two ticks using hitbox distance and visibility. Melee reach uses the nearest points of the two hitboxes, with a shared attack cooldown.


## Music triggers and hidden encounters (Lua)

Music Volumes have a persistent **Lua ID** under Playback / priority. Existing blocks receive a coordinate-based ID automatically; set a readable ID such as `tavern_door` in that field. A copied duplicate ID is replaced with the new block's coordinate ID. These IDs belong to the map.

`OnMusicVolumeToggle(playerUUID, volumeId, active, preset)` fires on physical entry or a scripted toggle. `active` is true when the volume switches its music on and false when it restores the previous selection. `preset` names the resulting selection (the configured map preset when returning to base). `OnMusicVolumeEnter(playerUUID, volumeId)` and `OnMusicVolumeExit(playerUUID, volumeId)` report physical crossings; exit alone does not change music. Toggle notification precedes the entry notification. Lifecycle resets clear toggles silently, restore the map preset, and require leaving an overlapping trigger before entering it again. Resets occur on match start, end, restart, permanent player death and respawn.

- `getMusicVolumes()` returns a table keyed by ID, with position and configuration.
- `getMusicVolume(id)`, `toggleMusicVolume(playerUUID, id)`, `isMusicVolumeActive(playerUUID, id)` and `setMusicVolumeEnabled(id, enabled)` address one volume.
- `resetPlayerMusic(playerUUID)` restores the map's configured base music.
- `spawnMapActorNearPlayer(profileId, playerUUID, minRadius, maxRadius)` uses a saved entity profile.
- `spawnEnemySpawnerNearPlayer(x, y, z, playerUUID, minRadius, maxRadius)` reuses an existing floor spawner's selected enemy, behavior, health mode, knife settings and other shared stats. It does not consume the spawner's automatic batch or cooldown.

Both spawn methods return the entity UUID, or an empty string if no valid hidden position exists. Distances are horizontal blocks: minimum 4, maximum 64. The search uses loaded, actual map path-block columns, solid support, free room for the full entity, and visibility checks against all living non-spectator players. It never falls back to spawning in view. Behind-player placement is permitted; high-FOV cameras use a conservative visibility cone. The server cannot know third-party free-camera positions. `OnHiddenEnemySpawned(playerUUID, enemyUUID, profileId, x, y, z)` fires only on success. The spawned enemy keeps normal faction and aggro rules; spawning does not force a target.

Example to adapt in your own script (replace the ID and spawner coordinates):

```lua
local previousMusicToggle = OnMusicVolumeToggle
function OnMusicVolumeToggle(playerUUID, volumeId, active, preset)
    if type(previousMusicToggle) == "function" then
        previousMusicToggle(playerUUID, volumeId, active, preset)
    end
    if volumeId == "tavern_door" and active then
        local enemy = ZombieroolAPI:spawnEnemySpawnerNearPlayer(12, 65, -8, playerUUID, 10, 24)
        if enemy == "" then
            -- No hidden path-block position: skip this encounter or retry later.
        end
    end
end
```

Normal middle-click picks a blank Enemy Spawner. **Ctrl + middle-click** copies all block data, including every configured behavior and statistic. Map actor voice files play as positional hostile sounds with distance attenuation and are sent only within 16 blocks, rather than playing at full volume across the map.


### Engine-enforced gameplay eligibility

Player gameplay callbacks are filtered centrally for creative and spectator players, including events with the player UUID in a later argument. Player death/respawn cleanup remains available. Timers inherit their player context, including chained timers, and wait while that player is creative, spectator, downed or paused. Gameplay spawning APIs also reject calls without an active match or eligible participants, including coordinate-based enemy spawns. Music gameplay hooks and hidden spawns enforce the same rule directly in Java. Walking through a music volume in creative previews its audio without dispatching these hooks or starting an encounter. These checks are not Lua options and do not depend on a map author adding a guard. Explicit authoring previews continue through the editor's preview route. `isGameRunning()` and `isGamePaused()` provide read-only match state to scripts.

### Stratholme Risen encounter

`zombierool/scripts/stratholme_risen_ambush.lua` listens to `music_74_82_-46`. On activation during a match it schedules a first appearance after 3–7 seconds, then 1–2 randomly selected ordinary Risen every 10–24 seconds, on hidden path columns 4–8 blocks from the player. Maximum eight living encounter enemies per player, retained across repeated music toggles. No valid hidden space means a skipped appearance and retry after 5–9 seconds. Leaving the music box does not stop or pause the encounter. A second toggle restoring the preceding music stops further spawns; existing enemies stay alive until killed or match cleanup. Death cancels the encounter. The script preserves existing event handlers and is map-specific.

### Random appearances

A profile may embed up to four alternative `appearances` in addition to its default model. Each spawn selects one uniformly, with no user selector. Alternatives may change bones, boxes, animations, texture, scale, eye colors and sounds; role, damage and callbacks remain shared. Only the selected visual is synchronized to clients, and its index is saved so reloading never rerolls it. The six Stratholme Risen roles each embed their female appearance this way.
