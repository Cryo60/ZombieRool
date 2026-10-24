# Lua scripting and addons

This guide explains the runtime, authoring workflow, common patterns and the full callable API. Start with a small rule on a copy of your map. Use the menus for ordinary configuration; Lua connects that configuration to objectives, progression and unusual events.

## Contents

1. Files, permissions and editor workflow
2. Lua essentials and API values
3. Match lifecycle, state and timers
4. Event composition and return values
5. Practical examples
6. Systems, addons and packaging
7. Debugging, performance and multiplayer validation
8. Complete API reference and event call sites

## 1. Files and editor workflow

Scripts live in `<world>/zombierool/scripts/` on the server. Legacy `<world>/zr_scripts/` remains readable; when filenames match, the canonical file takes precedence. `main.lua` loads first; other `.lua` files in that directory load in name order. They share the same global Lua environment. A name such as `20_objectives.lua` makes its position clear. Use UTF-8. Shipping a file on a player's computer does not install a server script: include it in the saved world.

Open **Map Config → Scripting**. The four tools are Guided rules, Code editor, API reference and Live console. Editing requires operator permission. The in-game editor restricts filenames to letters, digits, underscores and hyphens followed by `.lua`; it does not edit arbitrary server files. A file may contain up to 120,000 characters. The file list shows up to 128 matching names.

**Check** validates syntax. **Save** writes the file but does not execute it. **Reload** loads all saved map scripts and resets Lua state and timers. **Run** executes the editor buffer immediately in the current runtime. Run can change the game even if you have not saved it. Reloading a file does not undo changes already made to blocks, points or enemies. Test match start/end restoration explicitly.

Saving an existing script records its prior version in `zombierool/scripts/.history/`. The editor refuses to overwrite a file changed on disk since it was opened: reopen it and merge your edits. Keep your own map backups as well. Guided rules generate normal Lua and chain the prior handler; inspect the generated code before adding more rules to the same event.

## Script capabilities

Map scripts use the documented `ZombieroolAPI`, events, timers, and Lua's math, string, table and bit operations. They cannot access Java classes, launch programs, read or write arbitrary files, inspect system settings, or import native modules. `io`, `os`, `debug`, `package`, `require`, `dofile`, `loadfile`, `load` and `loadstring` are unavailable. Split a map's code into the automatically loaded `.lua` files described above; do not use `require`. Use the game API for supported persistence and map changes. Keep event handlers short: execution limits stop runaway scripts.

## 2. Lua essentials

Use `local` for temporary state, `function` for a handler, `if ... then ... end` for conditions, `==` for comparison and `..` for joining strings. Booleans are `true` and `false`, never quoted. `nil` means absent. Zero and empty strings are truthy in Lua, so test numeric/string contents explicitly.

```lua
local kills = 0
local threshold = 10

function OnZombieKill(playerUUID, mobType, isHeadshot)
    kills = kills + 1
    if kills == threshold then
        ZombieroolAPI:broadcastMessage("Ten eliminations reached.")
    end
end
```

Call the game API with a colon: `ZombieroolAPI:addPoints(playerUUID, 500)`. A colon supplies the API object as the receiver. Method names and strings are case-sensitive. Use exact registry identifiers such as `minecraft:stone` or `zombierool:...` where the method asks for an item/entity ID. Weapon definition IDs, preset IDs, network IDs and objective IDs belong to different registries; do not substitute a visible translated label for an ID.

Player and entity identifiers are UUID strings. Obtain them from an event or a lookup, rather than inventing them. Methods may return a Lua table, boolean, number, string or nothing. Read the method entry and check failures. An empty string can mean no result; it is still truthy.

Lua arrays are indexed from 1. Player lookup arrays contain UUID strings:

```lua
local players = ZombieroolAPI:getActivePlayers()
for i = 1, #players do
    local uuid = players[i]
    ZombieroolAPI:showActionBar(uuid, "Welcome to the test.")
end
```

`getActivePlayers()` selects Survival/Adventure participants; it does not return Creative observers. It is not a substitute for checking current health or downed state. If an action needs a nearby player, use `getNearbyPlayers(x, y, z, radius)` and inspect its result. An event's player UUID is normally the best target for rewards.

For configuration methods, pass a Lua table with documented field names:

```lua
ZombieroolAPI:configureMapAtmosphere({
    vignette = 0.15,
    saturation = 0.8
})
```

Do not assume arbitrary fields work. The target system sanitizes its own configuration. Always use the actual method signature below, particularly where coordinates, degrees, seconds and ticks differ.

## 3. Lifecycle, state and timers

At match start, the runtime reloads saved scripts, clears its string data store and cancels existing timers. Top-level code runs during loading. `OnGameStart()` then runs for the match. A manual Reload also clears state; it is not a way to preserve a running quest.

A `local` declared outside a handler belongs to that script chunk. Global names are shared by all loaded files, so prefix your own public helpers and keep most state local. `setData(key, value)` and `getData(key)` store strings in the runtime, not durable world data. They reset at script load. Do not use them as a permanent player database.

One second is normally **20 server ticks**. `registerTimer(id, delayTicks, callback)` is a one-shot timer. Reusing the same ID replaces its pending timer. Prefix timer IDs with your feature name and, for player-specific timers, the UUID. `cancelTimer(id)` cancels one timer. Timers advance on server ticks and wait during the game pause or when no eligible participant is present. Timers created in a player callback inherit that player context, including chained timers; they wait while that player is downed or in Creative/Spectator and are discarded if the player disconnects or the match has ended. A purely visual effect is not itself a gameplay pause.

```lua
function OnGameStart()
    ZombieroolAPI:registerTimer("lab_warning", 20 * 15, function()
        ZombieroolAPI:broadcastMessage("The laboratory is opening.")
        ZombieroolAPI:unlockChannel(2)
    end)
end
```

For repetition, schedule the next one-shot only when the prior callback finishes. Bound the work and provide a cancellation condition:

```lua
local running = false
local function pulse()
    if not running then return end
    ZombieroolAPI:broadcastMessage("The generator is still active.")
    ZombieroolAPI:registerTimer("generator_pulse", 20 * 30, pulse)
end

function OnGameStart()
    running = true
    ZombieroolAPI:registerTimer("generator_pulse", 20 * 30, pulse)
end

function StopGeneratorMessages()
    running = false
    ZombieroolAPI:cancelTimer("generator_pulse")
end
```

The runtime applies an execution budget to callbacks (500,000 instructions and roughly 150 ms). This is a last-resort guard against runaway scripts, not a useful per-tick budget. An infinite `while true do` loop will fail. Break expensive work into short timer steps and avoid scanning large regions on every hit.

## 4. Events and composition

An event handler is a global function with the exact event name and parameter order. Ordinary notifications ignore its return value. Some hooks inspect the return value through the runtime's `eventResult` path. Do not return `false` hoping every event is cancellable. The event reference and source call site distinguish notifications from result hooks.

Assigning the same global handler twice replaces the first. Chain it when extending an existing map:

```lua
local previous = OnWaveStart
function OnWaveStart(wave)
    if type(previous) == "function" then previous(wave) end
    if wave == 5 then
        ZombieroolAPI:broadcastMessage("Reinforcements detected.")
    end
end
```

Use one central handler calling local functions if you control the whole map. Avoid accidental recursion: do not call `OnWaveStart` from inside the new `OnWaveStart` unless you retained the old function under a different variable. State-changing API calls can emit more events; set your one-time guard **before** performing the action.

## 5. Practical examples

### A. A once-only reward

```lua
local rewarded = false
function OnWaveStart(wave)
    if wave < 10 or rewarded then return end
    rewarded = true
    local players = ZombieroolAPI:getActivePlayers()
    for i = 1, #players do
        ZombieroolAPI:addPoints(players[i], 1000)
    end
end
```

This rewards active participants once per runtime. It does not track late joiners permanently. Add a UUID-keyed reward set if your map requires a reward for each individual player.

### B. Headshot progress with a safe numeric counter

```lua
local progress = {}
function OnZombieKill(playerUUID, mobType, isHeadshot)
    if not isHeadshot or not playerUUID or playerUUID == "" then return end
    local total = (progress[playerUUID] or 0) + 1
    progress[playerUUID] = total
    if total == 20 then
        ZombieroolAPI:addPoints(playerUUID, 500)
        ZombieroolAPI:showActionBar(playerUUID, "Headshot objective complete")
    end
end
```

### C. Change the whole map's atmosphere

```lua
function OnGameStart()
    ZombieroolAPI:setMapAtmospherePreset("origins")
end
```

Use `getMapAtmospherePresets()` for supplied/custom choices and `getMapAtmosphere()` to inspect current settings. A player-specific preset is a presentation override: `setPlayerAtmospherePreset(uuid, id)` and `resetPlayerAtmosphere(uuid)`. Sky objects such as the nuclear cloud are part of atmosphere settings; changing a preset updates the scene. Gravity and breathing are configured separately with physical environment zones. Test end-of-match restoration instead of using a global preset change as a permanent edit unintentionally.

### D. A route locked during an experiment

```lua
function OnGameStart()
    ZombieroolAPI:setTeleporterCondition("rocket_network", "rocket_test", true)
    ZombieroolAPI:registerTimer("rocket_test_end", 20 * 45, function()
        ZombieroolAPI:setTeleporterCondition("rocket_network", "rocket_test", false)
    end)
end
```

Set the teleporter's Network Channel to `rocket_network` and Blocked condition to `rocket_test`. For a required Condition, `true` permits use instead. Names must match exactly. Inspect `getTeleporter(id)` before configuring or activating a route; the inspection/configuration/activation methods do different things.

### E. Spawner-region changes

```lua
function ActivateMoonRegion()
    ZombieroolAPI:setSpawnerChannels("1", "0", 0)
end
```

The arguments are enable-list, disable-list and duration in seconds. Duration 0 keeps the changes until another action/reset. Positive durations create temporary changes. Configure the spawners and navigation in advance. Teleporter menus already support redistribution; use those fields for a normal transport route instead of duplicating the same action in an event.

### F. Grant a portable buildable

```lua
function GiveTurret(playerUUID)
    local ok = ZombieroolAPI:givePortableEquipment(playerUUID, "sentry_turret")
    if not ok then print("Could not grant turret to", playerUUID) end
end
```

Portable equipment respects its slot and replacement rules. Supported built-ins include `turbine`, `zombie_shield` and `sentry_turret`. Do not hand-create the deployed block to bypass ownership, pickup and durability logic.

### G. Sound from an actual event

```lua
local played = false
function OnPowerActivated()
    if played then return end
    played = true
    ZombieroolAPI:playGlobalSound("minecraft:block.note_block.pling", 0.2, 1.0)
end
```

Global and positional sound have different uses. Use `playSound(x, y, z, soundId, volume, pitch)` for a world emitter, and a player-specific method for private feedback. Lower volume alone is not always equivalent to a distant source; configure ambient emitters for repeated spatial ambience. Sound identifiers must be registered/resolvable by the relevant method.

### H. Inspect rather than assume

```lua
local state = ZombieroolAPI:getTeleporter("earth")
print("Earth teleporter busy:", state.busy)
```

A lookup can return an empty table if the ID does not resolve. Test the relevant field before editing. A config call returning `false` means no successful update; do not silently advance your quest state on failure.

## 6. Systems and addon-friendly maps

**Buildables:** configure recipes in the workbench UI; scripts can inspect recipes and respond to `OnBuildablePartPickedUp`, `OnBuildableCompleted`, `OnBuildableClaimed` and placement events. Recipe templates in the preset library are distinct from active map recipes. Use normal addon items as components/results.

A datapack recipe template belongs at `data/example/buildable_presets/generator.json`:

```json
{
  "name": "Custom Generator",
  "mode": "item",
  "ticks": 40,
  "limit": 1,
  "reward": {"item": "minecraft:clock", "count": 1},
  "parts": [
    {"id": "example:coil", "item": "minecraft:copper_ingot", "count": 1},
    {"id": "example:frame", "item": "minecraft:iron_ingot", "count": 1}
  ]
}
```

At the datapack root, `pack.mcmeta` contains `{"pack":{"pack_format":15,"description":"Map recipes"}}`. Reload the datapack, select the template in the bench library and create/place its recipe components. It does not silently create an active recipe.

Additive item tags (`replace: false`) include `zombierool:buildable_components`, `zombierool:upright_components` and `zombierool:equipment_replaceable`. They control component selection/posing and suitable temporary placement surfaces. These tags do not implement a new Java entity or an entirely new portable mechanic on their own.

**Enemies:** use `getSpecialEnemiesConfig()`, `configureSpecialEnemies(table)` and encounter methods for the systems they expose. `spawnEncounter` returns an identifier; validate it and clean up scripted encounters at the correct point. Astronaut destinations must respect map progression. Do not manually mark special bosses as ordinary round kills.

**Sky deliveries and objectives:** inspect/configure Sky Delivery separately from Landing Sites. Use exact objective IDs for Soul Chests/Cerberus. A completed event should trigger your reward once; use guards and normal reward methods.

**Scenery:** inspect with `getAmbientDevice(x, y, z)`, configure the device, then enable/disable it deliberately. Route points, device settings and world effects are not interchangeable. Prefer the menu route tools for building paths.

**Environment:** map atmosphere, player atmosphere overrides and breathable/vacuum/gravity zones are separate layers. Keep physical rules and presentation changes explicit. Use the inspect/configure methods below rather than editing server NBT files during a live match.

**Packaging:** include the world's complete `zombierool/` folder (including `scripts/`) and datapacks. Declare required mods and gun packs. A map resource folder does not distribute addon Java code. Do not include personal account tokens, launcher profiles or machine-specific absolute paths in a shared map.

## 7. Debugging and performance

Use `print(...)` and the Live console to verify whether the event fired and which values arrived. Console history is bounded, so keep messages meaningful. Syntax errors are caught by Check; a valid file can still fail at runtime because of an invalid ID, missing player or wrong argument type.

- **Unknown method:** use the exact signature below; Lua names are case-sensitive.
- **No players:** test Survival/Adventure, not only Creative.
- **Timer never fires:** check script load/reload, duplicate timer IDs and delay units.
- **State disappears:** reload/match start resets the runtime; implement durable progress through a supported world system if needed.
- **Handler disappeared:** another file assigned the same function; chain or centralize handlers.
- **Action repeats:** set a one-time guard before the call, and avoid placing the same action in both menus and scripts.
- **Script exceeds budget:** remove loops/scans; use short delayed steps.
- **Changed block never resets:** not every arbitrary Lua world edit is a journalled gameplay action; test restoration or explicitly restore your own changes.

Run tests in this order: a clean match; the event's happy path; repeated activation; failure/missing ID; two players; death/disconnect; match end; restart the world. For transport test outward and return trips, including presentation, channels, Outnumbered and cooldown. For rewards verify recipient and amount with two different UUIDs. For per-player visuals verify an observer outside the event keeps their own settings.

A Java return type `void` means no Lua result. `String` maps to a Lua string, `boolean` to true/false, numeric primitives to numbers, `LuaTable` to a table, and `LuaValue` to a Lua value/callback. Parameter names provide intent but do not replace the detailed entry where available.

## Map actors, music hooks and hidden encounters in 26.7

See [Map Entity Workshop](16-Map-Entity-Workshop.md), [Music Volumes and hidden encounters](17-Music-Volumes-and-Hidden-Encounters.md) and [Map weapon packs and armor](18-Map-Weapon-Packs-and-Armor.md) for complete setup examples.

`isGameRunning()` and `isGamePaused()` expose match state. Gameplay callbacks for Creative/Spectator players are filtered centrally; death/respawn cleanup remains available. Enemy-spawning APIs require an active, unpaused match and eligible participants. Creative music previews change audio without invoking gameplay hooks. Player timers inherit eligibility context; still cancel feature timers and clear your rosters in lifecycle handlers.

Hidden spawns return an empty string if no loaded, supported, hidden and reachable path-block position exists. Compare explicitly with `""`. Do not replace a failed search with an unconditional visible spawn. Keep per-player and team caps for recurring encounters, and reuse/preserve earlier event handlers.

The world layout is documented in [Map files and migration](19-Map-Files-and-Migration.md). All paths in the reference below describe the current canonical layout. Legacy paths remain readable for existing maps.

## 8. Complete callable API

Available functions and parameters. Overloads are listed separately. Return types: `LuaTable` is a Lua table, `LuaValue` a Lua value/callback, and `void` has no result.

### activateTeleporter

```text
ZombieroolAPI:activateTeleporter(String id,String playerUUID) -> String
```

Expected parameters and types are listed in the signature.

### activateTrap

```text
ZombieroolAPI:activateTrap(int x,int y,int z,String playerUUID,boolean charge) -> String
```

Expected parameters and types are listed in the signature.

### addPoints

```text
ZombieroolAPI:addPoints(String playerUUIDStr, int amount) -> void
```

Gives points to the player UUID provided by an event.

```lua
ZombieroolAPI:addPoints(playerUUID, 500)
```

### applyEffect

```text
ZombieroolAPI:applyEffect(String playerUUIDStr, String effectId, int durationTicks, int amplifier) -> void
```

Expected parameters and types are listed in the signature.

### areSoulObjectivesComplete

```text
ZombieroolAPI:areSoulObjectivesComplete(String ids) -> boolean
```

Expected parameters and types are listed in the signature.

### bindBuildableBench

```text
ZombieroolAPI:bindBuildableBench(String recipe, int x, int y, int z) -> void
```

Benches are always unrestricted; the recipe argument is ignored. Right-click the physical block to edit shared recipes.

```lua
ZombieroolAPI:bindBuildableBench("", 14, 64, 10)
```

### bindBuildableDestination

```text
ZombieroolAPI:bindBuildableDestination(String recipe,int x,int y,int z) -> void
```

Binds a deployment marker to a recipe before the match.

```lua
ZombieroolAPI:bindBuildableDestination("switch", 10, 65, 4)
```

### bindBuildableItemPart

```text
ZombieroolAPI:bindBuildableItemPart(String recipe, String part, int x, int y, int z, String itemSnbt) -> void
```

Replace a marker block with any item model during the match, preserving its custom NBT.

```lua
ZombieroolAPI:bindBuildableItemPart("shield", "door", 10,64,10, '{id:"minecraft:iron_door",Count:1b}')
```

### bindBuildablePart

```text
ZombieroolAPI:bindBuildablePart(String recipe, String part, int x, int y, int z) -> void
```

Bind an existing physical block to a unique part. Multiple positions for the same part become randomized alternatives.

```lua
ZombieroolAPI:bindBuildablePart("shield", "door", 10, 64, 10)
```

### broadcastMessage

```text
ZombieroolAPI:broadcastMessage(String message) -> void
```

Shows a chat message to all players.

```lua
ZombieroolAPI:broadcastMessage("The power is on!")
```

### buyMapArmor

```text
ZombieroolAPI:buyMapArmor(String uuid,String set) -> String
```

Buy or repair a map armor set at the map-defined price. Returns equipped, repaired, full, funds or unavailable.

```lua
ZombieroolAPI:buyMapArmor(playerUUID, 'gambeson')
```

### cancelMachineDelivery

```text
ZombieroolAPI:cancelMachineDelivery(int x,int y,int z) -> int
```


### cancelSkyDelivery

```text
ZombieroolAPI:cancelSkyDelivery() -> int
```


### cancelTimer

```text
ZombieroolAPI:cancelTimer(String id) -> void
```

Expected parameters and types are listed in the signature.

### clearUnlockedChannels

```text
ZombieroolAPI:clearUnlockedChannels() -> void
```

Expected parameters and types are listed in the signature.

### clearUnlockedZones

```text
ZombieroolAPI:clearUnlockedZones() -> void
```

Expected parameters and types are listed in the signature.

### collectPackAPunchWeapon

```text
ZombieroolAPI:collectPackAPunchWeapon(String playerUUIDStr, int x, int y, int z) -> boolean
```

Expected parameters and types are listed in the signature.

### configureAmbientDevice

```text
ZombieroolAPI:configureAmbientDevice(int x,int y,int z,LuaTable changes) -> boolean
```


### configureAmbientSource

```text
ZombieroolAPI:configureAmbientSource(String id,LuaTable changes) -> boolean
```

Creates or patches a source: sound, enabled, loop, global, x/y/z, volume, pitch, pitchVariation, near, radius, occlusion, intervalMin/Max, fadeIn/Out, flash, soundSpeed. Time values use ticks; soundSpeed uses blocks/s.

```lua
ZombieroolAPI:configureAmbientSource("alarm", {sound="zombierool:ambient_distant_alarm", x=30, y=80, z=-20, volume=0.7, radius=150, loop=false, intervalMin=900, intervalMax=1800})
```

### configureAtmosphereZone

```text
ZombieroolAPI:configureAtmosphereZone(String id,LuaTable patch) -> boolean
```

Updates an existing zone by ID. vacuum=false makes it breathable.

```lua
ZombieroolAPI:configureAtmosphereZone("pressure_lab", { vacuum = false })
```

### configureIntroPhase

```text
ZombieroolAPI:configureIntroPhase(boolean enabled,int rampSeconds,int houndsAfterSeconds) -> void
```

Configures Outnumbered: enabled, time to full intensity and hellhound delay.

```lua
ZombieroolAPI:configureIntroPhase(true, 90, 45)
```

### configureIntroPhaseSettings

```text
ZombieroolAPI:configureIntroPhaseSettings(LuaTable settings) -> void
```

Expected parameters and types are listed in the signature.

### configureLunarEnvironment

```text
ZombieroolAPI:configureLunarEnvironment(double gravity,boolean vacuum,int oxygenSeconds) -> void
```

Expected parameters and types are listed in the signature.

### configureMachineDelivery

```text
ZombieroolAPI:configureMachineDelivery(int x,int y,int z,LuaTable settings) -> boolean
```


### configureMap

```text
ZombieroolAPI:configureMap(LuaTable settings) -> void
```

Expected parameters and types are listed in the signature.

### configureMapAtmosphere

```text
ZombieroolAPI:configureMapAtmosphere(LuaTable changes) -> void
```

Patches enabled, skyEnabled, skyTexture, skyLayout, skyCoordinates, skyYaw, skyPitch, skyBrightness, red, green, blue, exposure, contrast, saturation, gamma, vignette, vignetteSoftness, dirt, grain and ambientVolume. Sources are edited with configureAmbientSource.

```lua
ZombieroolAPI:configureMapAtmosphere({enabled=true, saturation=0.7, vignette=0.3})
```

### configureMapDevice

```text
ZombieroolAPI:configureMapDevice(int x,int y,int z,LuaTable config) -> boolean
```

Expected parameters and types are listed in the signature.

### configureMrChief

```text
ZombieroolAPI:configureMrChief(LuaTable config) -> void
```

Expected parameters and types are listed in the signature.

### configureNavigationLink

```text
ZombieroolAPI:configureNavigationLink(int x,int y,int z,LuaTable settings) -> boolean
```

Expected parameters and types are listed in the signature.

### configurePlayerAtmosphere

```text
ZombieroolAPI:configurePlayerAtmosphere(String playerUUID,LuaTable changes) -> boolean
```

Temporary local presentation override for one player; map-wide edits and dimension changes restore the map settings.

```lua
ZombieroolAPI:configurePlayerAtmosphere(playerUUID, {saturation=0.2, vignette=0.6})
```

### configureSkyDelivery

```text
ZombieroolAPI:configureSkyDelivery(LuaTable settings) -> boolean
```


### configureSkyObject

```text
ZombieroolAPI:configureSkyObject(String id,LuaTable changes) -> boolean
```

Creates or patches a sky object. Types: nuclear_cloud, earth, dogfight, eclipse, floating_rocks, meteor, custom. Parameters: yaw, elevation, distance, size, rotation, tilt, roll, spin, orbit, bob, speed, phase, opacity, enabled, name, boxes. Maximum 12 objects.

```lua
ZombieroolAPI:configureSkyObject("earth", {type="earth", yaw=45, elevation=35, size=18, spin=0.5})
```

### configureSoulObjective

```text
ZombieroolAPI:configureSoulObjective(String id,LuaTable settings) -> boolean
```

Expected parameters and types are listed in the signature.

### configureSpecialEnemies

```text
ZombieroolAPI:configureSpecialEnemies(LuaTable settings) -> void
```

Expected parameters and types are listed in the signature.

### configureSpecialSpawner

```text
ZombieroolAPI:configureSpecialSpawner(int x,int y,int z,LuaTable settings) -> boolean
```

Expected parameters and types are listed in the signature.

### configureTeleporter

```text
ZombieroolAPI:configureTeleporter(String id,LuaTable settings) -> boolean
```

Expected parameters and types are listed in the signature.

### configureZombieCat

```text
ZombieroolAPI:configureZombieCat(LuaTable settings) -> void
```

Expected parameters and types are listed in the signature.

### createExplosion

```text
ZombieroolAPI:createExplosion(double x, double y, double z, float radius, float damage) -> void
```

Expected parameters and types are listed in the signature.

### damageGlassDefenseDoor

```text
ZombieroolAPI:damageGlassDefenseDoor(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### damagePlayer

```text
ZombieroolAPI:damagePlayer(String playerUUIDStr, float amount) -> void
```

Expected parameters and types are listed in the signature.

### damageSpecialEnemy

```text
ZombieroolAPI:damageSpecialEnemy(String id,double damage,double x,double y,double z,String playerUUID) -> boolean
```

Expected parameters and types are listed in the signature.

### defineBuildable

```text
ZombieroolAPI:defineBuildable(String id, String name, String partIds, String rewardSnbt, String mode, int ticks, int limit) -> void
```

Define a map-local recipe before OnGameStart. Comma-separated unique part IDs, full item SNBT, result item/block/event, at least 40 ticks, 0 copies = unlimited. Modes: item, deploy (carry and install at a deployment marker), block (legacy fixed placement), event.

```lua
ZombieroolAPI:defineBuildable("shield", "Shield", "door,frame", '{id:"minecraft:shield",Count:1b}', "item", 40, 0)
```

### defineMapActor

```text
ZombieroolAPI:defineMapActor(String id,LuaTable definition) -> boolean
```


### deliverMachineAt

```text
ZombieroolAPI:deliverMachineAt(int x,int y,int z,String machine,int targetX,int targetY,int targetZ) -> String
```


### deliverSkyMachineAt

```text
ZombieroolAPI:deliverSkyMachineAt(String machine,int x,int y,int z) -> String
```


### despawnEncounter

```text
ZombieroolAPI:despawnEncounter(String id) -> boolean
```


### despawnMapActor

```text
ZombieroolAPI:despawnMapActor(String uuid) -> boolean
```

Retire l’entité et son éventuel comptage de manche.

```lua
ZombieroolAPI:despawnMapActor(uuid)
```

### destroyBlock

```text
ZombieroolAPI:destroyBlock(int x, int y, int z, boolean dropItems) -> void
```

Expected parameters and types are listed in the signature.

### disableTurbines

```text
ZombieroolAPI:disableTurbines(double x,double y,double z,double radius) -> void
```


### dropBuildablePart

```text
ZombieroolAPI:dropBuildablePart(String uuid) -> void
```

Return the carried component to its selected source.

```lua
ZombieroolAPI:dropBuildablePart(playerUUID)
```

### endGame

```text
ZombieroolAPI:endGame(String message) -> void
```

Expected parameters and types are listed in the signature.

### endTeleporterTrip

```text
ZombieroolAPI:endTeleporterTrip(String channel) -> void
```

Expected parameters and types are listed in the signature.

### equipEquipment

```text
ZombieroolAPI:equipEquipment(String playerUUID,String kind) -> boolean
```

Expected parameters and types are listed in the signature.

### equipMapArmor

```text
ZombieroolAPI:equipMapArmor(String uuid,String set) -> boolean
```

Equip a map armor set as a scripted reward. Sets: gambeson, chainmail, plate.

```lua
ZombieroolAPI:equipMapArmor(playerUUID, 'chainmail')
```

### excludeMysteryBox

```text
ZombieroolAPI:excludeMysteryBox(int x, int y, int z, boolean exclude) -> void
```

Expected parameters and types are listed in the signature.

### excludeWunderfizz

```text
ZombieroolAPI:excludeWunderfizz(int x, int y, int z, boolean exclude) -> void
```

Expected parameters and types are listed in the signature.

### fillBlocks

```text
ZombieroolAPI:fillBlocks(int x1, int y1, int z1, int x2, int y2, int z2, String blockId) -> void
```

Expected parameters and types are listed in the signature.

### findSafeEnemyTeleport

```text
ZombieroolAPI:findSafeEnemyTeleport(String playerUUID) -> LuaTable
```


### finishIntroPhase

```text
ZombieroolAPI:finishIntroPhase() -> boolean
```

Ends Outnumbered and resumes normal rounds.

```lua
ZombieroolAPI:finishIntroPhase()
```

### forceMysteryBox

```text
ZombieroolAPI:forceMysteryBox(int x, int y, int z, boolean isLocked) -> void
```

Expected parameters and types are listed in the signature.

### forceWunderfizz

```text
ZombieroolAPI:forceWunderfizz(int x, int y, int z) -> void
```

Expected parameters and types are listed in the signature.

### getActivePlayers

```text
ZombieroolAPI:getActivePlayers() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getAddonMapConfig

```text
ZombieroolAPI:getAddonMapConfig() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getAmbientDevice

```text
ZombieroolAPI:getAmbientDevice(int x,int y,int z) -> LuaTable
```


### getAmbientSoundLibrary

```text
ZombieroolAPI:getAmbientSoundLibrary() -> LuaTable
```

Lists bundled sound IDs and map OGG files from zombierool/assets/audio/custom/.

```lua
local entries = ZombieroolAPI:getAmbientSoundLibrary()
```

### getAmbientSource

```text
ZombieroolAPI:getAmbientSource(String id) -> LuaTable
```

Inspects a source; returns an empty table if absent.

```lua
local alarm = ZombieroolAPI:getAmbientSource("alarm")
```

### getAmbientSources

```text
ZombieroolAPI:getAmbientSources() -> LuaTable
```

Lists persistent ambience definitions.

```lua
local sources = ZombieroolAPI:getAmbientSources()
```

### getAtmosphereZone

```text
ZombieroolAPI:getAtmosphereZone(String id) -> LuaTable
```

Returns the zone settings for the specified ID.

```lua
local zone = ZombieroolAPI:getAtmosphereZone("pressure_lab")
print(zone.vacuum)
```

### getAtmosphereZones

```text
ZombieroolAPI:getAtmosphereZones() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getBlock

```text
ZombieroolAPI:getBlock(int x, int y, int z) -> String
```

Expected parameters and types are listed in the signature.

### getBlockPowerMode

```text
ZombieroolAPI:getBlockPowerMode(int x,int y,int z) -> String
```

Expected parameters and types are listed in the signature.

### getBlockState

```text
ZombieroolAPI:getBlockState(int x, int y, int z) -> String
```

Expected parameters and types are listed in the signature.

### getBuildableInstalledCount

```text
ZombieroolAPI:getBuildableInstalledCount(int x,int y,int z) -> int
```

Read shared team progress at a workbench.

```lua
local count = ZombieroolAPI:getBuildableInstalledCount(14,64,10)
```

### getCarriedBuildablePart

```text
ZombieroolAPI:getCarriedBuildablePart(String uuid) -> String
```

Read recipe:part or an empty string when the player carries no part.

```lua
local part = ZombieroolAPI:getCarriedBuildablePart(playerUUID)
```

### getCarriedBuildableResult

```text
ZombieroolAPI:getCarriedBuildableResult(String uuid) -> String
```

Returns the carried finished object recipe, or an empty string.

```lua
local result = ZombieroolAPI:getCarriedBuildableResult(playerUUID)
```

### getCurrentWave

```text
ZombieroolAPI:getCurrentWave() -> int
```

Expected parameters and types are listed in the signature.

### getCustomEnemySkins

```text
ZombieroolAPI:getCustomEnemySkins(String kind) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getData

```text
ZombieroolAPI:getData(String key) -> String
```

Reads a script value. Runtime data is reset when scripts are reloaded.

```lua
local value = ZombieroolAPI:getData("pressure_quests")
```

### getEncounterState

```text
ZombieroolAPI:getEncounterState(String id) -> LuaTable
```


### getEnemyAppearances

```text
ZombieroolAPI:getEnemyAppearances() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getEnemySkinPresets

```text
ZombieroolAPI:getEnemySkinPresets() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getEnemyStatus

```text
ZombieroolAPI:getEnemyStatus(String entityUUID) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getEntityCount

```text
ZombieroolAPI:getEntityCount(String entityTypeStr) -> int
```

Expected parameters and types are listed in the signature.

### getEntityEyeSkin

```text
ZombieroolAPI:getEntityEyeSkin(String uuid) -> String
```

Expected parameters and types are listed in the signature.

### getEntitySkin

```text
ZombieroolAPI:getEntitySkin(String uuid) -> String
```

Expected parameters and types are listed in the signature.

### getEquipmentLoadout

```text
ZombieroolAPI:getEquipmentLoadout(String playerUUID) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getGlassDefenseDoor

```text
ZombieroolAPI:getGlassDefenseDoor(int x,int y,int z) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getIntroElapsedSeconds

```text
ZombieroolAPI:getIntroElapsedSeconds() -> double
```

Expected parameters and types are listed in the signature.

### getIntroPhaseState

```text
ZombieroolAPI:getIntroPhaseState() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getInventory

```text
ZombieroolAPI:getInventory(String playerUUIDStr) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getLunarEnvironment

```text
ZombieroolAPI:getLunarEnvironment() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getLunarHelmetState

```text
ZombieroolAPI:getLunarHelmetState(String uuid) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getMachineDelivery

```text
ZombieroolAPI:getMachineDelivery(int x,int y,int z) -> LuaTable
```


### getMapActor

```text
ZombieroolAPI:getMapActor(String uuid) -> LuaTable
```

Vie, position, poste et cible de l’entité.

```lua
local actor = ZombieroolAPI:getMapActor(uuid)
```

### getMapActorData

```text
ZombieroolAPI:getMapActorData(String uuid) -> LuaTable
```

Lit les variables Lua de l’entité.

```lua
local state = ZombieroolAPI:getMapActorData(uuid)
```

### getMapActorProfiles

```text
ZombieroolAPI:getMapActorProfiles() -> LuaTable
```

Liste les profils propres à la map. / Lists map-owned profiles.

```lua
for _, id in ipairs(ZombieroolAPI:getMapActorProfiles()) do print(id) end
```

### getMapActorTime

```text
ZombieroolAPI:getMapActorTime() -> long
```


### getMapAtmosphere

```text
ZombieroolAPI:getMapAtmosphere() -> LuaTable
```

Returns a copy of the complete map sky, grading, screen effects and source definitions.

```lua
local settings = ZombieroolAPI:getMapAtmosphere()
```

### getMapAtmospherePresets

```text
ZombieroolAPI:getMapAtmospherePresets() -> LuaTable
```

Returns preset IDs and display names.

```lua
local entries = ZombieroolAPI:getMapAtmospherePresets()
```

### getMapConfig

```text
ZombieroolAPI:getMapConfig() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getMapDevice

```text
ZombieroolAPI:getMapDevice(int x,int y,int z) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getMapSkyboxes

```text
ZombieroolAPI:getMapSkyboxes() -> LuaTable
```

Lists bundled skies and PNG files from zombierool/assets/textures/skyboxes/.

```lua
local entries = ZombieroolAPI:getMapSkyboxes()
```

### getMiniBossKillCount

```text
ZombieroolAPI:getMiniBossKillCount() -> int
```

Expected parameters and types are listed in the signature.

### getMiniBossType

```text
ZombieroolAPI:getMiniBossType() -> String
```

Expected parameters and types are listed in the signature.

### getMrChiefConfig

```text
ZombieroolAPI:getMrChiefConfig() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getMusicVolume

```text
ZombieroolAPI:getMusicVolume(String id) -> LuaTable
```

Read one named volume configuration.

### getMusicVolumes

```text
ZombieroolAPI:getMusicVolumes() -> LuaTable
```

Table keyed by persistent volume ID, containing configuration and coordinates.

### getNavigationLink

```text
ZombieroolAPI:getNavigationLink(int x,int y,int z) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getNearbyPlayers

```text
ZombieroolAPI:getNearbyPlayers(double x, double y, double z, double radius) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getPackAPunchState

```text
ZombieroolAPI:getPackAPunchState(int x, int y, int z) -> int
```

Expected parameters and types are listed in the signature.

### getPlayerHealth

```text
ZombieroolAPI:getPlayerHealth(String uuidStr) -> float
```

Expected parameters and types are listed in the signature.

### getPlayerMaxHealth

```text
ZombieroolAPI:getPlayerMaxHealth(String uuidStr) -> float
```

Expected parameters and types are listed in the signature.

### getPlayerName

```text
ZombieroolAPI:getPlayerName(String playerUUIDStr) -> String
```

Expected parameters and types are listed in the signature.

### getPlayerPitch

```text
ZombieroolAPI:getPlayerPitch(String uuidStr) -> float
```

Expected parameters and types are listed in the signature.

### getPlayerX

```text
ZombieroolAPI:getPlayerX(String playerUUIDStr) -> double
```

Expected parameters and types are listed in the signature.

### getPlayerY

```text
ZombieroolAPI:getPlayerY(String playerUUIDStr) -> double
```

Expected parameters and types are listed in the signature.

### getPlayerYaw

```text
ZombieroolAPI:getPlayerYaw(String uuidStr) -> float
```

Expected parameters and types are listed in the signature.

### getPlayerZ

```text
ZombieroolAPI:getPlayerZ(String playerUUIDStr) -> double
```

Expected parameters and types are listed in the signature.

### getPortableEquipment

```text
ZombieroolAPI:getPortableEquipment(String uuid) -> LuaTable
```


### getRandomInt

```text
ZombieroolAPI:getRandomInt(int min, int max) -> int
```

Expected parameters and types are listed in the signature.

### getRemainingEnemies

```text
ZombieroolAPI:getRemainingEnemies() -> int
```

Expected parameters and types are listed in the signature.

### getScore

```text
ZombieroolAPI:getScore(String playerUUIDStr) -> int
```

Expected parameters and types are listed in the signature.

### getSkyDelivery

```text
ZombieroolAPI:getSkyDelivery() -> LuaTable
```


### getSkyObjects

```text
ZombieroolAPI:getSkyObjects() -> LuaTable
```

Returns all 3D sky scenery objects in the current atmosphere.

```lua
local objects = ZombieroolAPI:getSkyObjects()
```

### getSoulObjective

```text
ZombieroolAPI:getSoulObjective(String id) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getSpecialEnemiesConfig

```text
ZombieroolAPI:getSpecialEnemiesConfig() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getSpecialEnemy

```text
ZombieroolAPI:getSpecialEnemy(String id) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getSpecialSpawner

```text
ZombieroolAPI:getSpecialSpawner(int x,int y,int z) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getTeleporter

```text
ZombieroolAPI:getTeleporter(String id) -> LuaTable
```

Expected parameters and types are listed in the signature.

### getTeleporterCondition

```text
ZombieroolAPI:getTeleporterCondition(String channel,String condition) -> boolean
```

Expected parameters and types are listed in the signature.

### getTeleporterDefcon

```text
ZombieroolAPI:getTeleporterDefcon(String channel) -> int
```

Expected parameters and types are listed in the signature.

### getZombieCatConfig

```text
ZombieroolAPI:getZombieCatConfig() -> LuaTable
```

Expected parameters and types are listed in the signature.

### getZombiesInRadius

```text
ZombieroolAPI:getZombiesInRadius(double x, double y, double z, double radius) -> int
```

Expected parameters and types are listed in the signature.

### getZombieSwimming

```text
ZombieroolAPI:getZombieSwimming() -> boolean
```

Expected parameters and types are listed in the signature.

### giveBowieKnife

```text
ZombieroolAPI:giveBowieKnife(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### giveEnvironmentalSuit

```text
ZombieroolAPI:giveEnvironmentalSuit(String uuid,String style) -> boolean
```


### giveItem

```text
ZombieroolAPI:giveItem(String playerUUIDStr, String itemId, int count) -> void
```

Expected parameters and types are listed in the signature.

### giveLunarHelmet

```text
ZombieroolAPI:giveLunarHelmet(String uuid) -> boolean
```

Expected parameters and types are listed in the signature.

### givePerk

```text
ZombieroolAPI:givePerk(String playerUUIDStr, String perkId) -> void
```

Expected parameters and types are listed in the signature.

### givePortableEquipment

```text
ZombieroolAPI:givePortableEquipment(String uuid,String itemId) -> boolean
```


### giveRandomWeapon

```text
ZombieroolAPI:giveRandomWeapon(String uuidStr, String tier) -> void
```

Expected parameters and types are listed in the signature.

### giveWeapon

```text
ZombieroolAPI:giveWeapon(String playerUUIDStr, String weaponId) -> void
```

Gives a weapon to a player using its ZombieRool weapon ID.

```lua
ZombieroolAPI:giveWeapon(playerUUID, "glock")
```

### hasBowieKnife

```text
ZombieroolAPI:hasBowieKnife(String playerUUIDStr) -> boolean
```

Expected parameters and types are listed in the signature.

### hasItem

```text
ZombieroolAPI:hasItem(String playerUUIDStr, String itemId, int count) -> boolean
```

Expected parameters and types are listed in the signature.

### hasPerk

```text
ZombieroolAPI:hasPerk(String playerUUIDStr, String perkId) -> boolean
```

Expected parameters and types are listed in the signature.

### hasWardenKey

```text
ZombieroolAPI:hasWardenKey() -> boolean
```


### healMapActor

```text
ZombieroolAPI:healMapActor(String uuid,double health) -> boolean
```


### healPlayer

```text
ZombieroolAPI:healPlayer(String playerUUIDStr, float amount) -> void
```

Expected parameters and types are listed in the signature.

### importSkyObject

```text
ZombieroolAPI:importSkyObject(String id,String json) -> boolean
```

Imports 1–64 colored cuboids from JSON into the current preset. Coordinates are model-local; sizes are full extents.

```lua
ZombieroolAPI:importSkyObject("beacon", [[{"name":"Beacon","boxes":[{"sx":0.3,"sy":1,"sz":0.3,"r":0.2,"g":0.8,"b":1}]}]])
```

### insertPackAPunchWeapon

```text
ZombieroolAPI:insertPackAPunchWeapon(String playerUUIDStr, int x, int y, int z, boolean free) -> boolean
```

Expected parameters and types are listed in the signature.

### isBuildableComplete

```text
ZombieroolAPI:isBuildableComplete(int x,int y,int z) -> boolean
```

Check whether all unique parts are installed at this bench.

```lua
local ready = ZombieroolAPI:isBuildableComplete(14,64,10)
```

### isGamePaused

```text
ZombieroolAPI:isGamePaused() -> boolean
```

Whether the match is paused. Player-owned gameplay timers pause automatically.

### isGameRunning

```text
ZombieroolAPI:isGameRunning() -> boolean
```

Whether a match is running. Gameplay spawns and player-event eligibility are enforced by the engine.

### isIntroPhase

```text
ZombieroolAPI:isIntroPhase() -> boolean
```

Expected parameters and types are listed in the signature.

### isMachineLocked

```text
ZombieroolAPI:isMachineLocked(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### isMusicVolumeActive

```text
ZombieroolAPI:isMusicVolumeActive(String uuid,String id) -> boolean
```

Whether a volume has an active toggle for a player.

### isPlayerCrawling

```text
ZombieroolAPI:isPlayerCrawling(String uuidStr) -> boolean
```

Expected parameters and types are listed in the signature.

### isPlayerDown

```text
ZombieroolAPI:isPlayerDown(String uuidStr) -> boolean
```

Expected parameters and types are listed in the signature.

### isPlayerInRadius

```text
ZombieroolAPI:isPlayerInRadius(String playerUUIDStr, double x, double y, double z, double radius) -> boolean
```

Expected parameters and types are listed in the signature.

### isPowerActivated

```text
ZombieroolAPI:isPowerActivated() -> boolean
```

Expected parameters and types are listed in the signature.

### isSoulObjectiveComplete

```text
ZombieroolAPI:isSoulObjectiveComplete(String id) -> boolean
```

Expected parameters and types are listed in the signature.

### killAllZombies

```text
ZombieroolAPI:killAllZombies() -> void
```

Expected parameters and types are listed in the signature.

### killNearbyEntities

```text
ZombieroolAPI:killNearbyEntities(double x, double y, double z, double radius, String entityType) -> void
```

Expected parameters and types are listed in the signature.

### listBossLootTables

```text
ZombieroolAPI:listBossLootTables() -> LuaTable
```

Expected parameters and types are listed in the signature.

### listDeliveryMachines

```text
ZombieroolAPI:listDeliveryMachines() -> LuaTable
```


### listEmitterEffects

```text
ZombieroolAPI:listEmitterEffects(String kind) -> LuaTable
```

Expected parameters and types are listed in the signature.

### listMachineDeliveries

```text
ZombieroolAPI:listMachineDeliveries() -> LuaTable
```


### listMapDevices

```text
ZombieroolAPI:listMapDevices() -> LuaTable
```

Expected parameters and types are listed in the signature.

### listMiniBossTypes

```text
ZombieroolAPI:listMiniBossTypes() -> LuaTable
```

Expected parameters and types are listed in the signature.

### listSoulObjectives

```text
ZombieroolAPI:listSoulObjectives() -> LuaTable
```

Expected parameters and types are listed in the signature.

### listSpecialEnemies

```text
ZombieroolAPI:listSpecialEnemies() -> LuaTable
```

Expected parameters and types are listed in the signature.

### listSpecialSpawners

```text
ZombieroolAPI:listSpecialSpawners() -> LuaTable
```

Expected parameters and types are listed in the signature.

### listTeleporters

```text
ZombieroolAPI:listTeleporters() -> LuaTable
```

Expected parameters and types are listed in the signature.

### lockChannel

```text
ZombieroolAPI:lockChannel(int channel) -> void
```

Expected parameters and types are listed in the signature.

### lockMachine

```text
ZombieroolAPI:lockMachine(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### makeZombieCrawler

```text
ZombieroolAPI:makeZombieCrawler(String uuidStr) -> void
```

Expected parameters and types are listed in the signature.

### mapActorAttack

```text
ZombieroolAPI:mapActorAttack(String uuid,double radius,double damage,String effect,int effectTicks) -> int
```

Attaque de zone avec ligne de vue.

```lua
ZombieroolAPI:mapActorAttack(uuid, 4, 2, "minecraft:weakness", 40)
```

### mapActorEffect

```text
ZombieroolAPI:mapActorEffect(String uuid,double radius,String effect,int duration,int amplifier) -> int
```


### moveMapActor

```text
ZombieroolAPI:moveMapActor(String uuid,double x,double y,double z,double speed) -> boolean
```


### notifyDeliveryDoorChannel

```text
ZombieroolAPI:notifyDeliveryDoorChannel(int channel) -> void
```


### openObstacle

```text
ZombieroolAPI:openObstacle(String channel) -> void
```

Expected parameters and types are listed in the signature.

### packAPunchWeapon

```text
ZombieroolAPI:packAPunchWeapon(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### pauseWave

```text
ZombieroolAPI:pauseWave() -> void
```

Expected parameters and types are listed in the signature.

### performMeleeAttack

```text
ZombieroolAPI:performMeleeAttack(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### placeAmmoCrate

```text
ZombieroolAPI:placeAmmoCrate(int x, int y, int z, String facingStr) -> void
```

Expected parameters and types are listed in the signature.

### placeGlassDefenseDoor

```text
ZombieroolAPI:placeGlassDefenseDoor(int x,int y,int z,String facing) -> boolean
```

Expected parameters and types are listed in the signature.

### placeMachineDelivery

```text
ZombieroolAPI:placeMachineDelivery(int x,int y,int z,String facing,LuaTable settings) -> boolean
```


### placeMapDevice

```text
ZombieroolAPI:placeMapDevice(int x,int y,int z,String kind,String facing) -> boolean
```

Expected parameters and types are listed in the signature.

### placeMeteorite

```text
ZombieroolAPI:placeMeteorite(int x, int y, int z, boolean active) -> void
```

Expected parameters and types are listed in the signature.

### placeMysteryBox

```text
ZombieroolAPI:placeMysteryBox(int x, int y, int z, String facingStr, boolean active) -> void
```

Expected parameters and types are listed in the signature.

### placePerkMachine

```text
ZombieroolAPI:placePerkMachine(int x, int y, int z, String facingStr, String perkId) -> void
```

Expected parameters and types are listed in the signature.

### placePowerSwitch

```text
ZombieroolAPI:placePowerSwitch(int x, int y, int z, String facingStr, boolean powered) -> void
```

Expected parameters and types are listed in the signature.

### placeWunderfizz

```text
ZombieroolAPI:placeWunderfizz(int x, int y, int z, String facingStr) -> void
```

Expected parameters and types are listed in the signature.

### playDynamicSoundForPlayer

```text
ZombieroolAPI:playDynamicSoundForPlayer(String playerUUIDStr, String soundId) -> void
```

Expected parameters and types are listed in the signature.

### playDynamicSoundForPlayer

```text
ZombieroolAPI:playDynamicSoundForPlayer(String playerUUIDStr, String soundId, float volume) -> void
```

Expected parameters and types are listed in the signature.

### playDynamicSoundForPlayer

```text
ZombieroolAPI:playDynamicSoundForPlayer(String playerUUIDStr, String soundId, float volume, float pitch) -> void
```

Expected parameters and types are listed in the signature.

### playGlobalSound

```text
ZombieroolAPI:playGlobalSound(String soundId) -> void
```

Expected parameters and types are listed in the signature.

### playGlobalSound

```text
ZombieroolAPI:playGlobalSound(String soundId, float volume) -> void
```

Expected parameters and types are listed in the signature.

### playGlobalSound

```text
ZombieroolAPI:playGlobalSound(String soundId, float volume, float pitch) -> void
```

Expected parameters and types are listed in the signature.

### playKnuckleAnimation

```text
ZombieroolAPI:playKnuckleAnimation(String uuidStr) -> boolean
```

Expected parameters and types are listed in the signature.

### playMapActorAnimation

```text
ZombieroolAPI:playMapActorAnimation(String uuid,String clip) -> boolean
```

Joue une animation JSON nommée ; une chaîne vide l’arrête.

```lua
ZombieroolAPI:playMapActorAnimation(uuid, "cast")
```

### playSound

```text
ZombieroolAPI:playSound(double x, double y, double z, String soundId) -> void
```

Expected parameters and types are listed in the signature.

### playSound

```text
ZombieroolAPI:playSound(double x, double y, double z, String soundId, float volume) -> void
```

Expected parameters and types are listed in the signature.

### playSound

```text
ZombieroolAPI:playSound(double x, double y, double z, String soundId, float volume, float pitch) -> void
```

Expected parameters and types are listed in the signature.

### playSoundForPlayer

```text
ZombieroolAPI:playSoundForPlayer(String playerUUIDStr, String soundId) -> void
```

Expected parameters and types are listed in the signature.

### playSoundForPlayer

```text
ZombieroolAPI:playSoundForPlayer(String playerUUIDStr, String soundId, float volume) -> void
```

Expected parameters and types are listed in the signature.

### playSoundForPlayer

```text
ZombieroolAPI:playSoundForPlayer(String playerUUIDStr, String soundId, float volume, float pitch) -> void
```

Expected parameters and types are listed in the signature.

### playVoice

```text
ZombieroolAPI:playVoice(String uuid,String sound) -> boolean
```

Plays a registered voice sound and respects the player cooldown.

```lua
ZombieroolAPI:playVoice(playerUUID, "zombierool:player_us_level_start")
```

### playVoiceEvent

```text
ZombieroolAPI:playVoiceEvent(String uuid,String event) -> boolean
```

Triggers a voice event using the selected player voice preset.

```lua
ZombieroolAPI:playVoiceEvent(playerUUID, "level_start")
```

### printConsole

```text
ZombieroolAPI:printConsole(String message) -> void
```

Expected parameters and types are listed in the signature.

### pulseEmitter

```text
ZombieroolAPI:pulseEmitter(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### queueMrChief

```text
ZombieroolAPI:queueMrChief() -> void
```

Expected parameters and types are listed in the signature.

### refillAmmo

```text
ZombieroolAPI:refillAmmo(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### registerInteractable

```text
ZombieroolAPI:registerInteractable(String id, double x, double y, double z, double radius, String langKey) -> void
```

Expected parameters and types are listed in the signature.

### registerLookTrigger

```text
ZombieroolAPI:registerLookTrigger(String id, double x, double y, double z, double radius, double seconds, boolean requireScope) -> void
```

Expected parameters and types are listed in the signature.

### registerTimer

```text
ZombieroolAPI:registerTimer(String id, int delayTicks, LuaValue callback) -> void
```

Runs a callback after a delay in ticks. 20 ticks = 1 second.

```lua
ZombieroolAPI:registerTimer("pressure_delay", 60, function()
    ZombieroolAPI:broadcastMessage("Three seconds later")
end)
```

### releasePanzerGrab

```text
ZombieroolAPI:releasePanzerGrab(String id) -> boolean
```

Expected parameters and types are listed in the signature.

### removeAmbientSource

```text
ZombieroolAPI:removeAmbientSource(String id) -> boolean
```

Deletes a source and fades its loop out.

```lua
ZombieroolAPI:removeAmbientSource("alarm")
```

### removeAtmosphereZone

```text
ZombieroolAPI:removeAtmosphereZone(String id) -> boolean
```

Expected parameters and types are listed in the signature.

### removeBuildable

```text
ZombieroolAPI:removeBuildable(String id) -> void
```

Remove a recipe and its dedicated bindings outside a match.

```lua
ZombieroolAPI:removeBuildable("shield")
```

### removeInteractable

```text
ZombieroolAPI:removeInteractable(String id) -> void
```

Expected parameters and types are listed in the signature.

### removeItem

```text
ZombieroolAPI:removeItem(String playerUUIDStr, String itemId, int count) -> void
```

Expected parameters and types are listed in the signature.

### removeLookTrigger

```text
ZombieroolAPI:removeLookTrigger(String id) -> void
```

Expected parameters and types are listed in the signature.

### removeLunarHelmet

```text
ZombieroolAPI:removeLunarHelmet(String uuid) -> boolean
```

Expected parameters and types are listed in the signature.

### removePoints

```text
ZombieroolAPI:removePoints(String playerUUIDStr, int amount) -> void
```

Expected parameters and types are listed in the signature.

### removeSkyObject

```text
ZombieroolAPI:removeSkyObject(String id) -> boolean
```

Removes one object without changing the other sky objects.

```lua
ZombieroolAPI:removeSkyObject("earth")
```

### repairGlassDefenseDoor

```text
ZombieroolAPI:repairGlassDefenseDoor(int x,int y,int z,int planks) -> boolean
```

Expected parameters and types are listed in the signature.

### resetMachineDelivery

```text
ZombieroolAPI:resetMachineDelivery(int x,int y,int z,boolean wholeGroup) -> boolean
```


### resetMiniBossLoot

```text
ZombieroolAPI:resetMiniBossLoot() -> void
```

Expected parameters and types are listed in the signature.

### resetPlayerAtmosphere

```text
ZombieroolAPI:resetPlayerAtmosphere(String playerUUID) -> boolean
```

Restores the saved map presentation for one player.

```lua
ZombieroolAPI:resetPlayerAtmosphere(playerUUID)
```

### resetPlayerMusic

```text
ZombieroolAPI:resetPlayerMusic(String uuid) -> boolean
```

Clear toggles and immediately restore the map preset. Overlapping triggers wait for exit/reentry.

### resetPlayerPerkPurchases

```text
ZombieroolAPI:resetPlayerPerkPurchases(String playerUUIDStr, String perkId) -> void
```

Expected parameters and types are listed in the signature.

### resetSkyDelivery

```text
ZombieroolAPI:resetSkyDelivery() -> void
```


### resetSoulObjective

```text
ZombieroolAPI:resetSoulObjective(String id) -> boolean
```

Expected parameters and types are listed in the signature.

### resetTeleporterCooldown

```text
ZombieroolAPI:resetTeleporterCooldown(String id) -> boolean
```

Expected parameters and types are listed in the signature.

### resetTrap

```text
ZombieroolAPI:resetTrap(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### restoreEntityTemplate

```text
ZombieroolAPI:restoreEntityTemplate(String snbt) -> String
```

Expected parameters and types are listed in the signature.

### resumeWave

```text
ZombieroolAPI:resumeWave() -> void
```

Expected parameters and types are listed in the signature.

### saveMapAtmospherePreset

```text
ZombieroolAPI:saveMapAtmospherePreset(String name) -> String
```

Save the current map atmosphere as a new named preset; return its ID.

```lua
local id = ZombieroolAPI:saveMapAtmospherePreset("My atmosphere")
```

### sendMessageToPlayer

```text
ZombieroolAPI:sendMessageToPlayer(String playerUUIDStr, String message) -> void
```

Expected parameters and types are listed in the signature.

### setAmbientDeviceEnabled

```text
ZombieroolAPI:setAmbientDeviceEnabled(int x,int y,int z,boolean enabled) -> boolean
```


### setAmbientSourceEnabled

```text
ZombieroolAPI:setAmbientSourceEnabled(String id,boolean enabled) -> boolean
```

Toggles one source without changing the rest of the map.

```lua
ZombieroolAPI:setAmbientSourceEnabled("alarm", false)
```

### setAmbientVolume

```text
ZombieroolAPI:setAmbientVolume(double volume) -> void
```

Sets the map ambience mixer from 0 to 2.

```lua
ZombieroolAPI:setAmbientVolume(0.6)
```

### setAmmoCratePrice

```text
ZombieroolAPI:setAmmoCratePrice(int price) -> void
```

Expected parameters and types are listed in the signature.

### setAtmosphereZone

```text
ZombieroolAPI:setAtmosphereZone(String id,double x1,double y1,double z1,double x2,double y2,double z2,double gravity,boolean vacuum) -> void
```

Creates a zone between two corners. gravity=1 is normal; vacuum=true removes breathable air.

```lua
ZombieroolAPI:setAtmosphereZone("moon", 0, 50, 0, 20, 80, 20, 0.167, true)
```

### setBlock

```text
ZombieroolAPI:setBlock(int x, int y, int z, String blockId) -> void
```

Expected parameters and types are listed in the signature.

### setBlockPowerMode

```text
ZombieroolAPI:setBlockPowerMode(int x,int y,int z,String source) -> boolean
```

Expected parameters and types are listed in the signature.

### setColorGrading

```text
ZombieroolAPI:setColorGrading(double exposure,double contrast,double saturation,double gamma) -> void
```

Grades the finalized world image, independently of the HUD and shader-pack programs.

```lua
ZombieroolAPI:setColorGrading(-0.2, 1.1, 0.6, 1.0)
```

### setConfigFlag

```text
ZombieroolAPI:setConfigFlag(String key, boolean value) -> void
```

Expected parameters and types are listed in the signature.

### setConfigString

```text
ZombieroolAPI:setConfigString(String key, String value) -> void
```

Expected parameters and types are listed in the signature.

### setCustomFog

```text
ZombieroolAPI:setCustomFog(double r, double g, double b, double near, double far) -> void
```

Expected parameters and types are listed in the signature.

### setData

```text
ZombieroolAPI:setData(String key, String value) -> void
```

Stores text in runtime script data.

```lua
ZombieroolAPI:setData("pressure_quests", "3")
```

### setDeliveryMachinePrice

```text
ZombieroolAPI:setDeliveryMachinePrice(int x,int y,int z,String machine,int price) -> boolean
```


### setEmitterEnabled

```text
ZombieroolAPI:setEmitterEnabled(int x,int y,int z,boolean enabled) -> boolean
```

Expected parameters and types are listed in the signature.

### setEnemyAppearance

```text
ZombieroolAPI:setEnemyAppearance(String uuid,String preset,int variant) -> boolean
```

Expected parameters and types are listed in the signature.

### setEnemySkinPreset

```text
ZombieroolAPI:setEnemySkinPreset(String kind,String preset,boolean updateExisting) -> int
```

Expected parameters and types are listed in the signature.

### setEntityEyeSkin

```text
ZombieroolAPI:setEntityEyeSkin(String uuid,String skin) -> boolean
```

Expected parameters and types are listed in the signature.

### setEntitySkin

```text
ZombieroolAPI:setEntitySkin(String uuid,String skin) -> boolean
```

Expected parameters and types are listed in the signature.

### setEquipmentCharges

```text
ZombieroolAPI:setEquipmentCharges(String playerUUID,int equipment,int tactical) -> boolean
```

Expected parameters and types are listed in the signature.

### setFog

```text
ZombieroolAPI:setFog(String preset) -> void
```

Expected parameters and types are listed in the signature.

### setGlowing

```text
ZombieroolAPI:setGlowing(String uuidStr, boolean glowing) -> void
```

Expected parameters and types are listed in the signature.

### setLunarHelmetEquipped

```text
ZombieroolAPI:setLunarHelmetEquipped(String uuid,boolean equipped) -> boolean
```

Expected parameters and types are listed in the signature.

### setMachineDeliveryEnabled

```text
ZombieroolAPI:setMachineDeliveryEnabled(int x,int y,int z,boolean enabled) -> boolean
```


### setMapActorCasting

```text
ZombieroolAPI:setMapActorCasting(String uuid,int ticks) -> boolean
```

Suspend mêlée/navigation et joue l’animation cast du JSON.

```lua
ZombieroolAPI:setMapActorCasting(uuid, 30)
```

### setMapActorData

```text
ZombieroolAPI:setMapActorData(String uuid,LuaTable values) -> boolean
```

Variables Lua sauvegardées et isolées par entité.

```lua
ZombieroolAPI:setMapActorData(uuid, { nextCast = ZombieroolAPI:getMapActorTime() + 120 })
```

### setMapActorTarget

```text
ZombieroolAPI:setMapActorTarget(String uuid,String playerUUID) -> boolean
```


### setMapAtmosphereEnabled

```text
ZombieroolAPI:setMapAtmosphereEnabled(boolean enabled) -> void
```

Enables or disables the custom presentation.

```lua
ZombieroolAPI:setMapAtmosphereEnabled(true)
```

### setMapAtmospherePreset

```text
ZombieroolAPI:setMapAtmospherePreset(String id) -> void
```

Select nuketown_zombies, moon, nacht_der_untoten, die_rise or none. ashfall remains a compatible alias.

```lua
ZombieroolAPI:setMapAtmospherePreset("moon")
```

### setMaxZombiesAlive

```text
ZombieroolAPI:setMaxZombiesAlive(int count) -> void
```

Expected parameters and types are listed in the signature.

### setMiniBossLootItem

```text
ZombieroolAPI:setMiniBossLootItem(boolean first,String itemId,int count,String nbt) -> boolean
```

Expected parameters and types are listed in the signature.

### setMiniBossType

```text
ZombieroolAPI:setMiniBossType(String type) -> boolean
```

Expected parameters and types are listed in the signature.

### setMusicPreset

```text
ZombieroolAPI:setMusicPreset(String preset) -> void
```

Expected parameters and types are listed in the signature.

### setMusicVolumeEnabled

```text
ZombieroolAPI:setMusicVolumeEnabled(String id,boolean enabled) -> boolean
```

Enable or disable a named music trigger.

### setObjective

```text
ZombieroolAPI:setObjective(String uuidStr, String text) -> void
```

Expected parameters and types are listed in the signature.

### setPlayerAtmospherePreset

```text
ZombieroolAPI:setPlayerAtmospherePreset(String playerUUID,String id) -> boolean
```

Apply a bundled or custom preset to one player; @map restores the map atmosphere.

```lua
ZombieroolAPI:setPlayerAtmospherePreset(playerUUID, "moon")
```

### setPlayerGameMode

```text
ZombieroolAPI:setPlayerGameMode(String uuidStr, String modeStr) -> void
```

Expected parameters and types are listed in the signature.

### setPlayerVoicePreset

```text
ZombieroolAPI:setPlayerVoicePreset(String uuid,String preset) -> boolean
```

Expected parameters and types are listed in the signature.

### setRomeroEnraged

```text
ZombieroolAPI:setRomeroEnraged(String id,boolean enraged) -> boolean
```


### setScreenEffects

```text
ZombieroolAPI:setScreenEffects(double vignette,double dirt,double grain) -> void
```

Screen vignette, dirt and grain strengths, each from 0 to 1.

```lua
ZombieroolAPI:setScreenEffects(0.35, 0.1, 0.03)
```

### setSkybox

```text
ZombieroolAPI:setSkybox(String texture,String layout,double yaw,double brightness) -> void
```

Select a PNG resource and cube (3x2) or panorama (2:1) layout. Enable custom presentation automatically. Accepts zombierool:world_sky/name for PNG files in zombierool/assets/textures/skyboxes/, without a pack reload.

```lua
ZombieroolAPI:setSkybox("example:textures/sky/evening.png", "panorama", 30, 0.8)
```

### setSpawnerChannels

```text
ZombieroolAPI:setSpawnerChannels(String enable,String disable,int durationSeconds) -> void
```

Expected parameters and types are listed in the signature.

### setSpawnIntensity

```text
ZombieroolAPI:setSpawnIntensity(String intensity) -> void
```

Expected parameters and types are listed in the signature.

### setSpecialWavesEnabled

```text
ZombieroolAPI:setSpecialWavesEnabled(boolean enabled) -> void
```

Expected parameters and types are listed in the signature.

### setTeleporterCondition

```text
ZombieroolAPI:setTeleporterCondition(String channel, String condition, boolean active) -> void
```

Expected parameters and types are listed in the signature.

### setWave

```text
ZombieroolAPI:setWave(int wave) -> void
```

Expected parameters and types are listed in the signature.

### setWeather

```text
ZombieroolAPI:setWeather(String type) -> void
```

Expected parameters and types are listed in the signature.

### setZombieSwimming

```text
ZombieroolAPI:setZombieSwimming(boolean enabled) -> void
```

Enables or disables zombie swimming for this map.

```lua
ZombieroolAPI:setZombieSwimming(true)
```

### shakePlayerScreen

```text
ZombieroolAPI:shakePlayerScreen(String playerUUID,int ticks,double strength) -> boolean
```

Expected parameters and types are listed in the signature.

### shockMiniBoss

```text
ZombieroolAPI:shockMiniBoss(String id) -> boolean
```

Expected parameters and types are listed in the signature.

### shootMapActor

```text
ZombieroolAPI:shootMapActor(String uuid,String targetUUID,String style,double damage,double speed) -> boolean
```

Projectile esquivable : shadow, fire ou web.

```lua
ZombieroolAPI:shootMapActor(uuid, target, "shadow", 5, 0.65)
```

### showActionBar

```text
ZombieroolAPI:showActionBar(String uuidStr, String message) -> void
```

Expected parameters and types are listed in the signature.

### showTitle

```text
ZombieroolAPI:showTitle(String uuidStr, String title, String subtitle, int fadeIn, int stay, int fadeOut) -> void
```

Expected parameters and types are listed in the signature.

### spawnBonus

```text
ZombieroolAPI:spawnBonus(String bonusId, double x, double y, double z) -> void
```

Expected parameters and types are listed in the signature.

### spawnBonusAtPlayer

```text
ZombieroolAPI:spawnBonusAtPlayer(String playerUUIDStr, String bonusId) -> void
```

Expected parameters and types are listed in the signature.

### spawnBoss

```text
ZombieroolAPI:spawnBoss(double x, double y, double z, String mobType) -> void
```

Expected parameters and types are listed in the signature.

### spawnCrawler

```text
ZombieroolAPI:spawnCrawler(double x, double y, double z) -> void
```

Expected parameters and types are listed in the signature.

### spawnCustomBoss

```text
ZombieroolAPI:spawnCustomBoss(double x, double y, double z, String mobType, float hp, float speed, float scale) -> void
```

Expected parameters and types are listed in the signature.

### spawnEncounter

```text
ZombieroolAPI:spawnEncounter(String kind,double x,double y,double z) -> String
```


### spawnEnemySpawnerNearPlayer

```text
ZombieroolAPI:spawnEnemySpawnerNearPlayer(int x,int y,int z,String uuid,double minRadius,double maxRadius) -> String
```

Reuse a floor spawner enemy with all behavior, health and knife settings for a hidden encounter. Returns UUID or empty string.

### spawnLookTarget

```text
ZombieroolAPI:spawnLookTarget(double x,double y,double z,String name) -> String
```

Expected parameters and types are listed in the signature.

### spawnMapActor

```text
ZombieroolAPI:spawnMapActor(String id,double x,double y,double z) -> String
```

Fait apparaître un profil ; renvoie son UUID ou une chaîne vide si bloqué.

```lua
local id = ZombieroolAPI:spawnMapActor("crypt_fiend", 0.5, 65, 4.5)
```

### spawnMapActorNearPlayer

```text
ZombieroolAPI:spawnMapActorNearPlayer(String profile,String uuid,double minRadius,double maxRadius) -> String
```

Spawn a configured profile on a loaded path-block column outside all players view. Radius 4..64 blocks. Returns UUID or empty string; never falls back to a visible spawn.

### spawnMob

```text
ZombieroolAPI:spawnMob(double x, double y, double z, String mobType) -> void
```

Expected parameters and types are listed in the signature.

### spawnMrChief

```text
ZombieroolAPI:spawnMrChief(int x,int y,int z) -> String
```

Expected parameters and types are listed in the signature.

### spawnNapalmFire

```text
ZombieroolAPI:spawnNapalmFire(double x,double y,double z,int seconds) -> boolean
```

Expected parameters and types are listed in the signature.

### spawnParticle

```text
ZombieroolAPI:spawnParticle(String particleId, double x, double y, double z) -> void
```

Expected parameters and types are listed in the signature.

### spawnParticleAdv

```text
ZombieroolAPI:spawnParticleAdv(String particleId, double x, double y, double z, int count, double dx, double dy, double dz, double speed) -> void
```

Expected parameters and types are listed in the signature.

### spawnSpecialEnemy

```text
ZombieroolAPI:spawnSpecialEnemy(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### spawnThrowable

```text
ZombieroolAPI:spawnThrowable(String ownerUuidStr, String throwableType, double x, double y, double z, double vx, double vy, double vz, int fuseTicks) -> void
```

Expected parameters and types are listed in the signature.

### spawnZombieCat

```text
ZombieroolAPI:spawnZombieCat(int x,int y,int z) -> String
```

Expected parameters and types are listed in the signature.

### startIntroPhase

```text
ZombieroolAPI:startIntroPhase() -> boolean
```

Expected parameters and types are listed in the signature.

### stopTrap

```text
ZombieroolAPI:stopTrap(int x,int y,int z) -> boolean
```

Expected parameters and types are listed in the signature.

### strikeLightning

```text
ZombieroolAPI:strikeLightning(double x, double y, double z) -> void
```

Expected parameters and types are listed in the signature.

### teleportAllPlayers

```text
ZombieroolAPI:teleportAllPlayers(double x, double y, double z) -> void
```

Expected parameters and types are listed in the signature.

### teleportPlayer

```text
ZombieroolAPI:teleportPlayer(String playerUUIDStr, double x, double y, double z) -> void
```

Expected parameters and types are listed in the signature.

### toggleMusicVolume

```text
ZombieroolAPI:toggleMusicVolume(String uuid,String id) -> boolean
```

Toggle a named volume for one player. Returns success. Emits OnMusicVolumeToggle.

### triggerAmbientSound

```text
ZombieroolAPI:triggerAmbientSound(String id) -> boolean
```

Sends one synchronized cue; optional flash and distance delay are applied on each client.

```lua
ZombieroolAPI:triggerAmbientSound("thunder")
```

### triggerAmmoCrate

```text
ZombieroolAPI:triggerAmmoCrate(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### triggerElderGuardianOverlay

```text
ZombieroolAPI:triggerElderGuardianOverlay(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### triggerMachineDelivery

```text
ZombieroolAPI:triggerMachineDelivery(int x,int y,int z) -> String
```


### triggerScopeScreamer

```text
ZombieroolAPI:triggerScopeScreamer(String playerUUIDStr) -> void
```

Expected parameters and types are listed in the signature.

### triggerSkyDelivery

```text
ZombieroolAPI:triggerSkyDelivery() -> String
```


### unbindBuildable

```text
ZombieroolAPI:unbindBuildable(int x,int y,int z) -> void
```

Remove a binding without breaking its block outside a match.

```lua
ZombieroolAPI:unbindBuildable(10,64,10)
```

### unlockChannel

```text
ZombieroolAPI:unlockChannel(int channel) -> void
```

Unlocks a logic channel. Use the number configured on your blocks.

```lua
ZombieroolAPI:unlockChannel(1)
```

## 9. Event call sites

Read from literal event dispatches in the current local Java sources. Arguments retain their source expressions: player UUID expressions are strings, not Java objects. Dynamically named callback families, including map actor prefix callbacks, are described in their setup guides. Return-valued hooks must preserve the expected result when chaining.

### OnActionKeyPressed

```text
OnActionKeyPressed(player.getUUID().toString())
```

callEvent — ServerInteractionHandler.java

### OnAllZombiesDead

```text
OnAllZombiesDead()
```

callEvent — WaveManager.java

### OnAmbientDcaShot

```text
OnAmbientDcaShot(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),target.x,target.y,target.z)
```

callEvent — MapDeviceSystem.java

### OnAmbientDeviceState

```text
OnAmbientDeviceState(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),kind().name(),active)
```

callEvent — MapDeviceSystem.java

### OnAmbientSound

```text
OnAmbientSound(id,source.getString("sound"))
```

eventResult — MapAtmosphere.java

### OnBlockInteract

```text
OnBlockInteract(player.getUUID().toString(), pos.getX(), pos.getY(), pos.getZ())
```

callEvent — ServerEventHandler.java

### OnBlockShot

```text
OnBlockShot(shooter.getUUID().toString(), impact.blockPos.getX(), impact.blockPos.getY(), impact.blockPos.getZ())
```

callEvent — BallisticManager.java

### OnBlockShot

```text
OnBlockShot(sp.getUUID().toString(), hitPos.getX(), hitPos.getY(), hitPos.getZ())
```

callEvent — CustomProjectileHandler.java

### OnBlockShot

```text
OnBlockShot(sp.getUUID().toString(), pos.getX(), pos.getY(), pos.getZ())
```

callEvent — TacZImpl.java

### OnBonusCollected

```text
OnBonusCollected(player.getUUID().toString(), bonus.id)
```

callEvent — BonusManager.java

### OnBossArmorBroken

```text
OnBossArmorBroken(getUUID().toString(),kind(),flag==1?"helmet":flag==2?"reactor_cover":"reactor_core")
```

callEvent — SpecialEnemyEntity.java

### OnBrutusMachineLocked

```text
OnBrutusMachineLocked(pos.getX(),pos.getY(),pos.getZ())
```

callEvent — BrutusMachineLocks.java

### OnBrutusMachineUnlocked

```text
OnBrutusMachineUnlocked(player.getUUID().toString(),pos.getX(),pos.getY(),pos.getZ())
```

callEvent — BrutusMachineLocks.java

### OnBuildableClaimed

```text
OnBuildableClaimed(p.getUUID().toString(), b.recipe, pos.getX(), pos.getY(), pos.getZ(), b.claims)
```

callEvent — BuildableSystem.java

### OnBuildableClaimed

```text
OnBuildableClaimed(p.getUUID().toString(),b.recipe,pos.getX(),pos.getY(),pos.getZ(),b.claims)
```

callEvent — BuildableSystem.java

### OnBuildableCompleted

```text
OnBuildableCompleted(p.getUUID().toString(), bench.recipe, a.bench.getX(), a.bench.getY(), a.bench.getZ())
```

callEvent — BuildableSystem.java

### OnBuildablePartDropped

```text
OnBuildablePartDropped(p.getUUID().toString(), part.recipe, part.id)
```

callEvent — BuildableSystem.java

### OnBuildablePartDropped

```text
OnBuildablePartDropped(player.toString(), previous.recipe, previous.id)
```

callEvent — BuildableSystem.java

### OnBuildablePartInstalled

```text
OnBuildablePartInstalled(p.getUUID().toString(), bench.recipe, a.part.id, a.bench.getX(), a.bench.getY(), a.bench.getZ())
```

callEvent — BuildableSystem.java

### OnBuildablePartPickedUp

```text
OnBuildablePartPickedUp(player.toString(), part.recipe, part.id)
```

callEvent — BuildableSystem.java

### OnBuildablePlaced

```text
OnBuildablePlaced(p.getUUID().toString(),d.recipe,pos.getX(),pos.getY(),pos.getZ())
```

callEvent — BuildableSystem.java

### OnCerberusFeeding

```text
OnCerberusFeeding(objectiveId,z.getUUID().toString())
```

callEvent — CerberusSystem.java

### OnCerberusMealFinished

```text
OnCerberusMealFinished(objectiveId,collected,required,killer==null?"":killer.toString())
```

callEvent — CerberusSystem.java

### OnCrawlerLeap

```text
OnCrawlerLeap(mob.getUUID().toString(),target.getUUID().toString())
```

callEvent — CrawlerLeap.java

### OnCrawlerLeapHit

```text
OnCrawlerLeapHit(mob.getUUID().toString(),p.getUUID().toString())
```

callEvent — CrawlerLeap.java

### OnCustomInteract

```text
OnCustomInteract(player.getUUID().toString(), inter.id)
```

callEvent — ServerInteractionHandler.java

### OnEmitterPulse

```text
OnEmitterPulse(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),kind().name(),effect)
```

callEvent — MapDeviceSystem.java

### OnEnemyEquipmentEffect

```text
OnEnemyEquipmentEffect(mob.getUUID().toString(),kind.id,owner==null?"":owner.toString())
```

callEvent — EquipmentEffects.java

### OnEntityKilled

```text
OnEntityKilled(player.getUUID().toString(), target.getUUID().toString(), wasHeadshotHit)
```

callEvent — DamageManager.java

### OnEquipmentUsed

```text
OnEquipmentUsed(p.getUUID().toString(),action.kind.id,device.getX(),device.getY(),device.getZ())
```

callEvent — EquipmentSystem.java

### OnExplosion

```text
OnExplosion(ownerId()==null?"":ownerId().toString(),pos.x,pos.y,pos.z,(double)radius)
```

callEvent — EquipmentEntity.java

### OnExplosion

```text
OnExplosion(ownerUuid, pos.x, pos.y, pos.z, (double)outerRadius)
```

callEvent — ExplosionControl.java

### OnExplosion

```text
OnExplosion(ownerUuid, pos.x, pos.y, pos.z, (double)radius)
```

callEvent — ExplosionControl.java

### OnGameStart

```text
OnGameStart()
```

callEvent — WaveManager.java

### OnGeistLeap

```text
OnGeistLeap(mob.getUUID().toString(),target.getUUID().toString())
```

callEvent — CrawlerLeap.java

### OnGeistLeapHit

```text
OnGeistLeapHit(mob.getUUID().toString(),p.getUUID().toString())
```

callEvent — CrawlerLeap.java

### OnGlassDoorDamaged

```text
OnGlassDoorDamaged(pos.getX(),pos.getY(),pos.getZ(),stage,stage==0)
```

callEvent — GlassDefenseDoorBlock.java

### OnGlassDoorPlanksChanged

```text
OnGlassDoorPlanksChanged(pos.getX(),pos.getY(),pos.getZ(),stage)
```

callEvent — GlassDefenseDoorBlock.java

### OnHelmetChanged

```text
OnHelmetChanged(p.getUUID().toString(),target)
```

callEvent — LunarEquipment.java

### OnHiddenEnemySpawned

```text
OnHiddenEnemySpawned(player.getUUID().toString(),uuid,profile,at.x,at.y,at.z)
```

eventResult — HiddenActorSpawn.java

### OnIntroEnd

```text
OnIntroEnd(introTicks/20.0)
```

callEvent — WaveManager.java

### OnIntroStart

```text
OnIntroStart()
```

callEvent — WaveManager.java

### OnItemUsed

```text
OnItemUsed(player.getUUID().toString(), itemId)
```

callEvent — ServerEventHandler.java

### OnLookTriggerActivated

```text
OnLookTriggerActivated(player.getUUID().toString(), t.id)
```

callEvent — LookTriggerManager.java

### OnMapAtmosphereChanged

```text
OnMapAtmosphereChanged()
```

eventResult — MapAtmosphere.java

### OnMeleeStrike

```text
OnMeleeStrike(player.getUUID().toString(), strikePos.x, strikePos.y, strikePos.z)
```

callEvent — MeleeAttackHandler.java

### OnMiniBossKilled

```text
OnMiniBossKilled(killer==null?"":killer.getUUID().toString(),boss.kind(),boss.getUUID().toString(),first,state.kills)
```

callEvent — MiniBossLoot.java

### OnMiniBossLoot

```text
OnMiniBossLoot(boss.getUUID().toString(),"item:"+net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()))
```

callEvent — MiniBossLoot.java

### OnMiniBossLoot

```text
OnMiniBossLoot(boss.getUUID().toString(),id)
```

callEvent — MiniBossLoot.java

### OnMiniBossTypeChanged

```text
OnMiniBossTypeChanged(previous,next)
```

callEvent — SpecialEnemyConfig.java

### OnMinionKilled

```text
OnMinionKilled(getUUID().toString(),kind())
```

callEvent — SpecialEnemyEntity.java

### OnMrChiefEscaped

```text
OnMrChiefEscaped(getUUID().toString())
```

callEvent — MrChiefEntity.java

### OnMrChiefFled

```text
OnMrChiefFled(getUUID().toString(),fleeTicks/20)
```

callEvent — MrChiefEntity.java

### OnMrChiefKilled

```text
OnMrChiefKilled(getUUID().toString(),winner==null?"":winner.getUUID().toString(),winner==null?0:reward,getX(),getY(),getZ())
```

callEvent — MrChiefEntity.java

### OnMrChiefSpawned

```text
OnMrChiefSpawned(cat.getUUID().toString(),cat.getX(),cat.getY(),cat.getZ(),forced)
```

callEvent — MrChiefEasterEgg.java

### OnMusicVolumeEnter

```text
OnMusicVolumeEnter(p.getUUID().toString(),transition.id())
```

eventResult — MusicVolume.java

### OnMusicVolumeExit

```text
OnMusicVolumeExit(p.getUUID().toString(),zone.getString("id"))
```

eventResult — MusicVolume.java

### OnMusicVolumeToggle

```text
OnMusicVolumeToggle(p.getUUID().toString(),t.id(),t.active(),t.preset().isEmpty()?basePreset(p):t.preset())
```

eventResult — MusicVolume.java

### OnMysteryBoxUsed

```text
OnMysteryBoxUsed(player.getUUID().toString(), WeaponFacade.getWeaponId(finalWeapon))
```

callEvent — MysteryBoxSystem.java

### OnPanzerGrab

```text
OnPanzerGrab(getUUID().toString(),player.getUUID().toString())
```

callEvent — SpecialEnemyEntity.java

### OnPanzerRelease

```text
OnPanzerRelease(getUUID().toString(),previousCapture.toString())
```

callEvent — SpecialEnemyEntity.java

### OnPerkBought

```text
OnPerkBought(player.getUUID().toString(), perkId)
```

callEvent — ServerInteractionHandler.java

### OnPerkBought

```text
OnPerkBought(player.getUUID().toString(), perkToPurchase.getId())
```

callEvent — ServerInteractionHandler.java

### OnPlayerDeath

```text
OnPlayerDeath(player.getUUID().toString())
```

callEvent — PlayerDownManager.java

### OnPlayerDown

```text
OnPlayerDown(player.getUUID().toString())
```

callEvent — PlayerDownManager.java

### OnPlayerRespawn

```text
OnPlayerRespawn(player.getUUID().toString())
```

callEvent — ZombieCapabilitySystem.java

### OnPlayerRevive

```text
OnPlayerRevive(player.getUUID().toString(), player.getUUID().toString())
```

callEvent — PlayerDownManager.java

### OnPlayerRevive

```text
OnPlayerRevive(reviver.getUUID().toString(), downPlayer.getUUID().toString())
```

callEvent — PlayerDownManager.java

### OnPortableTurretShot

```text
OnPortableTurretShot(pos.getX(),pos.getY(),pos.getZ(),target.getUUID().toString(),owner==null?"":owner.toString(),DAMAGE)
```

callEvent — PortableTurret.java

### OnPowerActivated

```text
OnPowerActivated()
```

callEvent — PowerSwitchBlock.java

### OnProjectileHit

```text
OnProjectileHit(sp.getUUID().toString(), hitPos.x, hitPos.y, hitPos.z)
```

callEvent — CustomProjectileHandler.java

### OnSoulCollected

```text
OnSoulCollected(objectiveId,collected,required,orb.killer==null?"":orb.killer.toString())
```

callEvent — SoulChestSystem.java

### OnSoulObjectiveCompleted

```text
OnSoulObjectiveCompleted(objectiveId)
```

callEvent — SoulChestSystem.java

### OnTeleporterLinkStarted

```text
OnTeleporterLinkStarted(config.getString("id"),receiver.config.getString("id"))
```

callEvent — TeleporterSystem.java

### OnTeleporterLinked

```text
OnTeleporterLinked(source.config.getString("id"),config.getString("id"))
```

callEvent — TeleporterSystem.java

### OnTeleporterTripEnded

```text
OnTeleporterTripEnded(trip.channel)
```

callEvent — TeleporterTravel.java

### OnTeleporterUsed

```text
OnTeleporterUsed(source.config.getString("id"),target.config.getString("id"),departure.payer.toString())
```

callEvent — TeleporterTravel.java

### OnTrapActivated

```text
OnTrapActivated(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),channel,owner.toString(),duration)
```

callEvent — MapDeviceSystem.java

### OnTrapHit

```text
OnTrapHit(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),target.getUUID().toString(),purchaser == null ? "" : purchaser.getUUID().toString(),damage,!target.isAlive())
```

callEvent — MapDeviceSystem.java

### OnTrapReady

```text
OnTrapReady(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),channel)
```

callEvent — MapDeviceSystem.java

### OnTrapStopped

```text
OnTrapStopped(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),channel)
```

callEvent — MapDeviceSystem.java

### OnTraversalFinished

```text
OnTraversalFinished(f.mob.getUUID().toString())
```

callEvent — TraversalMotion.java

### OnTraversalStarted

```text
OnTraversalStarted(entity.getUUID().toString(),route.anchor().getBlockPos().getX(),route.anchor().getBlockPos().getY(),route.anchor().getBlockPos().getZ())
```

callEvent — TraversalMotion.java

### OnVoiceLinePlayed

```text
OnVoiceLinePlayed(player.getUUID().toString(),event,sound)
```

callEvent — PlayerVoiceManager.java

### OnVoiceLineRequested

```text
OnVoiceLineRequested(player.getUUID().toString(),soundType)
```

eventResult — PlayerVoiceManager.java

### OnVrilConversion

```text
OnVrilConversion(target.getUUID().toString(),human.getUUID().toString(),owner.getUUID().toString())
```

callEvent — WonderWeaponEffects.java

### OnWaveEnd

```text
OnWaveEnd(currentWave)
```

callEvent — WaveManager.java

### OnWaveStart

```text
OnWaveStart(currentWave)
```

callEvent — WaveManager.java

### OnZombieKill

```text
OnZombieKill(player.getUUID().toString(), mobType, wasHeadshotHit)
```

callEvent — DamageManager.java

[All guides](INDEX.md)
