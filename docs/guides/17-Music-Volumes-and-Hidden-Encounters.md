# Music Volumes and hidden encounters

Music changes are per player.

## Place a music trigger

1. Obtain **Music Volume** from Creative → Ambience (`/give @s zombierool:music_volume`).
2. Place it at the bottom of the room and right-click in Creative. Set X/Z half-width and height; the purple outline previews the full cuboid.
3. Select a playlist: `default`, `damned`, or a custom folder. For example, put Ogg tracks in `<world>/zombierool/assets/audio/music/tavern/` to make a `tavern` playlist. Reload the world's resources or rejoin after adding files.
4. Set volume, fade duration and priority. Twenty fade ticks equal one second. An exact custom audio ID loops that file; a folder shuffles through its tracks. `none` selects silence.
5. Under Playback / priority, give the block a readable **Lua ID**, such as `tavern_door`, and save.

The first entry switches to the selected music. Leaving does nothing. The next entry restores the previously selected music; further entries alternate. The trigger remembers the selection it replaced. Simultaneous entries prefer higher priority, then smaller volume. Copying duplicate IDs generates a fresh coordinate-based ID.

Playlists use every available track before reshuffling and avoid immediately repeating the last track across cycles. Resynchronizing the same preset preserves its current track. Ambient loops play independently; an easter egg song takes priority and then returns to the latest map music.

Walking through a volume in Creative previews audio without invoking encounter hooks. Match start/end/restart, permanent death and respawn clear toggles and restore the base music. After a reset while inside a trigger, leave it before entering again. The block has no collision and is invisible in Survival.

## Trigger a hidden encounter

Create an Enemy Spawner and configure its enemy, health, behavior and knife rules. Use its coordinates in the example below; replace the example coordinates and music ID with your own.

```lua
local previousToggle = OnMusicVolumeToggle
function OnMusicVolumeToggle(playerUUID, volumeId, active, preset)
    if type(previousToggle) == "function" then
        previousToggle(playerUUID, volumeId, active, preset)
    end
    if volumeId ~= "tavern_door" or not active then return end
    local enemyUUID = ZombieroolAPI:spawnEnemySpawnerNearPlayer(
        12, 65, -8, playerUUID, 10, 24)
    if enemyUUID == "" then
        -- No valid hidden placement; skip this appearance.
        return
    end
end
```

`spawnEnemySpawnerNearPlayer` reuses the placed spawner's settings without spending its automatic batch or cooldown. `spawnMapActorNearPlayer(profileId, playerUUID, minRadius, maxRadius)` uses a saved profile directly. Both return an entity UUID or an empty string; Lua treats an empty string as true, so compare explicitly.

The engine searches loaded path-block columns with solid support, enough space, a valid route and hidden placement relative to all living non-spectator players. Horizontal search distances are limited to 4–64 blocks. It skips the appearance if no safe position exists. Spawning keeps normal faction/aggro rules and does not force a target.

## Events and state

- `OnMusicVolumeToggle(playerUUID, volumeId, active, preset)` reports switching on/restoring previous music. Toggle runs before the physical-entry notification.
- `OnMusicVolumeEnter(playerUUID, volumeId)` and `OnMusicVolumeExit(playerUUID, volumeId)` report crossings; exit does not restore music.
- `OnHiddenEnemySpawned(playerUUID, enemyUUID, profileId, x, y, z)` runs on a successful hidden spawn.
- `getMusicVolumes()`, `getMusicVolume(id)`, `isMusicVolumeActive(playerUUID, id)`, `toggleMusicVolume(playerUUID, id)`, `setMusicVolumeEnabled(id, enabled)` and `resetPlayerMusic(playerUUID)` inspect/control the system.

Gameplay spawning requires an active, unpaused match and eligible participants. Player-context timers wait while that player is downed or in Creative/Spectator. Chained timers inherit that context. Reset notifications are silent; cancel your encounter roster and timers in lifecycle handlers too.

For repeated appearances, cap living enemies, use unique per-player timer IDs, retry failed placements after a delay and preserve existing callbacks. Test two players, repeated entries, pause, death, disconnect and restart. Stratholme encounter pools and timing are map scripts, not global defaults.

[Enemy Spawners](16-Map-Entity-Workshop.md) · [Atmosphere](04-Skies-Sounds-and-Presets.md) · [Lua reference](12-Lua-and-Addons.md) · [All guides](INDEX.md)
