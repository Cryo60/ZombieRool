# 🧟‍♂️ ZombieRool

<div align="center">

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)
![Modloader](https://img.shields.io/badge/Modloader-Forge%2047.1.3-orange?style=for-the-badge)
[![License](https://img.shields.io/badge/License-MIT-red?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Discord](https://img.shields.io/badge/Discord-Join_Us!-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/HGv2r44hXM)

**ZombieRool** turns a Minecraft world into a round-based zombies match. Survive the waves, earn points, buy weapons off the walls, open the map, turn on the power, and Pack-a-Punch before the rounds outscale you.

Play a map, or build one. Map makers get a creative tab, an in-game config menu, JSON weapons, Lua, and assets that sync to anyone who joins the world. No command blocks required.

The latest **published** jar is **1.6.3**.

</div>

---

## 📖 Table of Contents

- [Requirements](#-requirements)
- [How to Play](#-how-to-play)
- [Perks](#-perks)
- [Gore](#-gore)
- [TacZ](#tacz)
- [Map Creation](#️-map-creation)
- [Lua Scripting](#-lua-scripting)
- [Data-Driven Customization](#-data-driven-customization)
- [Links](#-links)

---

## 📦 Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.1.3 |
| Java | 17 |
| Required mod | [Player Animator](https://www.curseforge.com/minecraft/mc-mods/playeranimator) |
| Optional | [Timeless and Classics Zero (TacZ)](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero) |
| Languages | English, French, German, Spanish, Brazilian Portuguese, Russian |

ZombieRool has its own guns. TacZ is not required. If TacZ is installed, its guns can be balanced, Pack-a-Punched, and rolled from the Mystery Box.

Lua and Discord Rich Presence are already inside the mod. LuaJ is packed into the jar under another package, so it does not clash with TacZ. Do not install a separate LuaJ mod. Rich Presence does not need an extra mod either.

The easiest way to launch a community map is the [ZombieRool launcher](https://github.com/Cryo60/ZR-Launcher). **Play** builds a separate Forge instance for that map, so its mods do not replace your normal `mods` folder. **Install** only copies the world into the folder shown at the bottom of the launcher. In the launcher, **TaCZ and gun packs** is off unless you turn it on. Downloads are HTTPS only, and only from Modrinth, CurseForge, this GitHub account, or the official Minecraft and Forge servers.

---

## 🎮 How to Play

### A round

| | |
|---|---|
| **Start** | Wave 1, a pistol, and 500 points. Kills pay points. Headshots and knife kills pay more. |
| **Guns** | You carry 2 weapons. Mule Kick raises that to 3. |
| **Buy** | Wall weapons, the Mystery Box, perks, debris, and Pack-a-Punch (5,000 points once the power is on). |
| **Perks** | Juggernog, Speed Cola, Quick Revive, Double Tap, PhD Flopper, Mule Kick, Electric Cherry, Vulture Aid, Blood Rage, and Royal Beer. The Wunderfizz rolls one at random. What each one does is in [Perks](#-perks). |
| **Power** | Perk machines, the Wunderfizz, and Pack-a-Punch need the power switch. Machines placed before the switch still wake up when the power comes on. |
| **Last Stand** | At 0 health you go down. You can crawl and fire a pistol while someone revives you. |
| **Death mid-round** | If you actually die during a round, you spectate until the next wave. You are not sent back to your spawner yet. |
| **End of the game** | When the match ends, you return to your player spawner with full health and a fresh loadout. Creative players who are building the map are left where they are. |
| **Leave and come back** | Rejoin a match that is still running and you come back as a spectator with an empty inventory. Rejoin after the match is over and your inventory, effects, and points are reset to 500. |
| **Dropped guns** | A gun you throw stays on the ground, and only you can pick it up. It disappears when the inventory slot it came from is filled by a new weapon. Hoppers and mobs cannot take it. |
| **Prone** | You can go prone and crawl through low gaps. |

### Sharing a roll

Knife the weapon sitting in a Mystery Box, or the drink showing on a Wunderfizz, to offer it to someone else.

- You cannot take it back after you share it.
- The first other player who takes it gets it. The machine closes. Nobody else can take that roll.
- The offer lasts 45 seconds.

### During a match

Levers, buttons, pressure plates, tripwire, repeaters, comparators, and daylight detectors still work.

Containers stay locked. So do doors, trapdoors, and fence gates, so a match cannot be used to stash guns or to open decorative gates. Redstone can still power those blocks.

Bullets and blood pass through invisible map-maker blocks such as Restrict. Players are still stopped by Restrict.

### Fog

The map fog uses Minecraft's fog, so a shader pack can draw it. In the map config, **fog outdoors only** is optional and off by default. When it is on, indoor fragments stay clear.

---

## 🥤 Perks

You can hold **4 perks** at once. Going down removes every perk you have. The prices below are the default machine prices. A map can set a different price.

| Perk | Id | Default price |
|---|---|---|
| Juggernog | `juggernog` | 2,500 |
| Speed Cola | `speed_cola` | 3,000 |
| Double Tap | `double_tap` | 2,000 |
| Quick Revive | `quick_revive` | 1,500 |
| Mule Kick | `mule_kick` | 4,000 |
| Vulture Aid | `vulture` | 3,000 |
| PhD Flopper | `phd_flopper` | 2,000 |
| Electric Cherry | `cherry` | 2,000 |
| Blood Rage | `blood_rage` | 2,500 |
| Royal Beer | `royal_beer` | 2,500 |

### Juggernog

Health goes from 3 hearts to 5, and buying it fills you to full.

### Speed Cola

Reload, repairs, and a little movement.

- Reload time is cut in half, including shell-by-shell reloads.
- Barricades and defense walls repair twice as fast.
- Movement speed is 5% higher.

### Double Tap

Fire rate and a second shot, together.

- Built-in guns fire 2 bullets per trigger pull and only spend the ammo of 1. The delay between shots is 25% shorter.
- TacZ guns deal double damage, fire about 50% faster, and cycle pump and bolt actions faster.

### Quick Revive

- In co-op, reviving a teammate takes 3 seconds instead of 6. There is no buy limit.
- In solo, a fatal hit puts you down and you stand back up after 10 seconds. The perk is used up. You can buy it 3 times per game.
- The down still removes every other perk you were holding.

### Mule Kick

You can carry a third gun.

### Vulture Aid

- Each time you gain points, there is a 30% chance to gain 10 more.
- Throwing an item on the ground pays 100 to 300 points.
- When a power-up drops, Zombie Blood, On the House, and Gold Rush are 3 times more likely to be the one that rolls.

### PhD Flopper

Explosions, falls, and fire.

- Explosions do not hurt you, including your own grenades and wonder weapons. Fire, lava, magma, and falling blocks do not either.
- A fall of more than 3 blocks deals no fall damage and creates a small explosion where you land. That blast does not break blocks and does not hurt players.
- A crawler bite does not slow you. Crawler gas does not affect you. Flamethrower ground fire does not hurt you.

### Electric Cherry

Reloading releases a shock about 3 blocks around you. Monsters in it take a small hit and Slowness V for 4 seconds. This works on built-in guns and on TacZ.

### Blood Rage

Dealing damage can heal you. The chance rises as your health falls, and it cannot happen more than once a second.

| Health | Chance |
|---|---|
| Full | about 5% |
| Half | about 10% |
| A quarter | about 20% |
| Almost empty | nearly certain |

A proc heals 15% of the damage you just dealt. Healing past full becomes absorption, up to 5 hearts. You also get Strength I for 5 seconds.

### Royal Beer

A White Knight follows you and fights zombies, crawlers, and hellhounds.

- He has 15 hearts, heavy armor, a sword, and a shield. Player damage does not hurt him.
- Every 3 seconds he sweeps up to 6 zombies.
- You gain 10 points for each hit he lands, and 50 more if that hit kills.
- If he dies, he comes back while you still have the perk. He disappears if you lose it.
- You can buy this perk 5 times per game. A golden apple heals him.

---

## 🩸 Gore

Full gore is the default. Change it in **Options → ZombieRool Options**, not in the map config menu.

- Bullets and headshots tear off arms and heads. The pieces stay in the world. A lost limb sometimes sprays blood, and the spray follows the body.
- A knife cut still kills, and it still pays the melee bonus, but it is not a headshot and it does not tear anything off.
- The flamethrower, fire, and lava char the body. A charred corpse does not burst into blood and meat. A bullet that finishes a zombie who is already on fire still uses normal gore.
- Bodies fall and stay for about three minutes. The pose is not the same every time. A gas crawler still bursts instead of leaving a corpse.
- Blood sticks to floors, walls, and ceilings. Puddles on the same block blend instead of flickering. A puddle fades after about two minutes, and every puddle is cleared when the match ends.
- Corpses are not stuck red, and a dead zombie does not leave a shadow blob on the ground.

---

## TacZ

TacZ is optional. With it installed, ZombieRool can balance TacZ guns, Pack-a-Punch them, and offer them from the Mystery Box. Without it, the built-in arsenal still works. Player Animator stays required either way.

---

## 🛠️ Map Creation

Open the config GUI with the config key (see Controls) or `/zombierool menu` (OP).

- **General:** day/night, starting weapon, death rules, music preset, fog color, and outdoors-only fog.
- **Waves:** health scaling, crawler gas, super sprinters, Hellhound rounds.
- **Weapons and drops:** which guns can come out of the box, and which power-ups can drop.

Creative blocks are in the **ZombieRool Creator Tools** tab.

| Block | What it does |
|---|---|
| **Player spawner** | Where that player stands at the end of a match. |
| **Universal spawner** | Shift+right click. Mob type (zombie, hellhound, crawler), zone, and channels. |
| **Obstacle door / debris** | Blocks a path. Set the price and the channel. Buying it opens everything on that channel. The price, the text, and the look survive the end-of-match reset. |
| **Defense door / defense wall** | Barricade the players repair. A dead zombie does not break them. |
| **Wall weapon** | Put a weapon in the slot and set the price. The **Chalk** item draws the gun on the wall. |
| **Barricade** | Windows. Zombies tear them down. Hold interact to repair them for points. |
| **Perk, Mystery Box, Wunderfizz, Pack-a-Punch, power switch** | Place them in any order. Turning the power on wakes the machines that were already placed. |
| **Meteorite** | Hidden song easter egg. |
| **Restrict** | Solid for players. Bullets and blood pass through. |
| **Zombie Pass, Path, Limit** | Invisible logic blocks. Particles and bullets do not treat them as solid. |

The hidden 100-point crawl under a perk machine is forgotten when the match ends, so the next game can find it again. Blocks that changed during the match are put back.

### Files that travel with the map

Everything below lives in the world save and is sent to clients who join.

```
saves/<YourWorld>/
├── zr_scripts/
│   └── main.lua                 Lua. /zombierool reload applies it.
└── zombierool/
    ├── config.json              Written by the in-game menu.
    ├── weapons/                 JSON overrides for guns that already exist. Same file name as the weapon id.
    ├── custom_blocks/           Up to 32 PNGs. Each one becomes a block, stairs, a slab, a fence, a wall, and a pane.
    ├── placements/              Optional .txt files that place addon machines. See below.
    ├── skins/
    │   ├── zombie/  crawler/  hellhound/
    │   └── zombie_eyes/  crawler_eyes/  hellhound_eyes/
    ├── assets/chalks/           PNG overlays for the Chalk item. 128×128 recommended.
    └── audio/
        ├── music/               default/  damned/  secret/  menu/
        ├── voices/              player voice lines, .ogg
        └── sfx/                 start/  round_change/  special_change/  special_start/  ambient/  misc/
```

Audio must be `.ogg` and under 15 MB. Skins, chalks, and custom-block textures must be `.png` and under 2 MB.

Each custom-block texture can use a vanilla sound (stone, wood, metal, glass, and the other presets in the texture kit). The kit is in the decoration tab of the creative menu. The folder travels with the map, so someone who downloads the world sees your textures.

A first launch of the world writes `zombierool/README.md` next to these folders, with the same layout.

### Addon placements

An addon can drop machines into a map from a text file, without opening the world. ZombieRool does not ship extra machine types. A line whose type no addon has registered is skipped.

Read from any of these:

- `config/zombierool/placements/*.txt`
- `saves/<YourWorld>/zombierool/placements/*.txt`
- `data/<modid>/zombierool_placements/*.txt` inside a mod or datapack

```
# Type x y z [facing] [on|off] key=value
# facing: north south east west up down
# map=WorldFolder limits the line to that world folder name
MyMachine 12 65 -8 north on price=500 channel=1 map=Dark Origin
```

The addon registers the type in Java with `AddonPlacements.register`. A line that already ran is not applied again.

---

## 📜 Lua Scripting

Scripts live in `saves/<YourWorld>/zr_scripts/`. Run the world once, edit `main.lua`, then `/zombierool reload`.

UUIDs are strings. Coordinates are numbers. Tick delays are 20 ticks per second.

### Events

```lua
-- Round
function OnGameStart() end
function OnWaveStart(wave) end
function OnWaveEnd(wave) end
function OnAllZombiesDead() end
function OnPowerActivated() end

-- Combat
function OnZombieKill(playerUUID, mobType, isHeadshot) end
function OnEntityKilled(playerUUID, entityUUID, isHeadshot) end
function OnBlockShot(playerUUID, x, y, z) end
function OnProjectileHit(playerUUID, x, y, z) end
function OnMeleeStrike(playerUUID, x, y, z) end
function OnExplosion(playerUUID, x, y, z, radius) end

-- Players
function OnPlayerDown(playerUUID) end
function OnPlayerRevive(reviverUUID, revivedUUID) end
function OnPlayerDeath(playerUUID) end
function OnPlayerRespawn(playerUUID) end
function OnBlockInteract(playerUUID, x, y, z) end
function OnActionKeyPressed(playerUUID) end          -- interact key, not aimed at a machine
function OnCustomInteract(playerUUID, interactableId) end
function OnItemUsed(playerUUID, itemId) end

-- Buys
function OnPerkBought(playerUUID, perkId) end
function OnMysteryBoxUsed(playerUUID, weaponId) end
function OnBonusCollected(playerUUID, bonusId) end
function OnLookTriggerActivated(playerUUID, triggerId) end

-- Barriers. kind is "death" or "damage".
function OnBarrierSpotted(playerUUID, kind, x, y, z) end  -- countdown starts
function OnBarrierExecute(playerUUID, kind, x, y, z) end  -- timer hits zero
function OnBarrierMiss(playerUUID, kind, x, y, z) end     -- left during the last half-second, no hit
```

### ZombieroolAPI

<details>
<summary><b>Waves</b></summary>

| Function | |
|---|---|
| `setWave(wave)` | Jump to a wave |
| `pauseWave()` / `resumeWave()` | Hold the round |
| `setMaxZombiesAlive(count)` | Cap how many are up at once |
| `getCurrentWave()` | |
| `endGame(message)` | End the match |
| `killAllZombies()` | |
| `setSpecialWavesEnabled(enabled)` | Hellhound-style rounds |
| `setSpawnIntensity(intensity)` | Spawn pressure |
| `isPowerActivated()` | |

</details>

<details>
<summary><b>Players, points, items</b></summary>

| Function | |
|---|---|
| `getActivePlayers()` | Table of UUID strings |
| `getNearbyPlayers(x, y, z, radius)` | |
| `getPlayerName(uuid)` | |
| `addPoints(uuid, amount)` / `removePoints(uuid, amount)` / `getScore(uuid)` | |
| `getPlayerHealth(uuid)` / `getPlayerMaxHealth(uuid)` | |
| `healPlayer(uuid, amount)` / `damagePlayer(uuid, amount)` | |
| `isPlayerDown(uuid)` / `isPlayerCrawling(uuid)` | |
| `getPlayerYaw(uuid)` / `getPlayerPitch(uuid)` | |
| `setPlayerGameMode(uuid, mode)` | `SURVIVAL`, `CREATIVE`, `ADVENTURE`, `SPECTATOR` |
| `teleportPlayer(uuid, x, y, z)` / `teleportAllPlayers(x, y, z)` | |
| `isPlayerInRadius(uuid, x, y, z, radius)` | |
| `giveItem(uuid, itemId, count)` / `removeItem(...)` / `hasItem(...)` | |
| `getInventory(uuid)` | |
| `giveWeapon(uuid, weaponId)` | |
| `giveRandomWeapon(uuid, tier)` | `WONDER`, `PAP`, `ASSAULT_RIFLE`, or a JSON tag |
| `packAPunchWeapon(uuid)` | Upgrade the held gun |
| `refillAmmo(uuid)` | |
| `givePerk(uuid, perkId)` / `hasPerk(uuid, perkId)` | |
| `resetPlayerPerkPurchases(uuid, perkId)` | |
| `giveBowieKnife(uuid)` / `hasBowieKnife(uuid)` | |
| `performMeleeAttack(uuid)` | |
| `triggerAmmoCrate(uuid)` | |
| `applyEffect(uuid, effectId, durationTicks, amplifier)` | |
| `setGlowing(uuid, glowing)` | |

</details>

<details>
<summary><b>Text, sound, fog</b></summary>

| Function | |
|---|---|
| `showTitle(uuid, title, subtitle, fadeIn, stay, fadeOut)` | |
| `showActionBar(uuid, message)` | |
| `setObjective(uuid, text)` | Boss bar. Empty text removes it |
| `sendMessageToPlayer(uuid, message)` / `broadcastMessage(message)` | |
| `playSound(x, y, z, soundId, volume, pitch)` | |
| `playGlobalSound(soundId, volume, pitch)` | |
| `playSoundForPlayer(uuid, soundId, volume, pitch)` | |
| `playDynamicSoundForPlayer(uuid, soundId, volume, pitch)` | A sound shipped in the map folder |
| `setMusicPreset(preset)` | |
| `setFog(preset)` / `setCustomFog(r, g, b, near, far)` | |
| `triggerScopeScreamer(uuid)` | |
| `triggerElderGuardianOverlay(uuid)` | |
| `spawnParticle(particleId, x, y, z)` | |
| `spawnParticleAdv(particleId, x, y, z, count, dx, dy, dz, speed)` | |
| `setWeather(type)` | |
| `printConsole(message)` | Server log |

</details>

<details>
<summary><b>Map, doors, machines</b></summary>

| Function | |
|---|---|
| `getBlock(x, y, z)` / `getBlockState(x, y, z)` | |
| `setBlock(x, y, z, blockId)` / `fillBlocks(x1, y1, z1, x2, y2, z2, blockId)` | |
| `destroyBlock(x, y, z, dropItems)` | |
| `openObstacle(channel)` | Open every door on that channel |
| `unlockChannel(channel)` / `lockChannel(channel)` | |
| `clearUnlockedChannels()` / `clearUnlockedZones()` | |
| `registerInteractable(id, x, y, z, radius, langKey)` | Fires `OnCustomInteract` |
| `removeInteractable(id)` | |
| `placePerkMachine(x, y, z, facing, perkId)` | `facing`: `north` `south` `east` `west` |
| `placeMysteryBox(x, y, z, facing, active)` | |
| `placeWunderfizz(x, y, z, facing)` | |
| `placePowerSwitch(x, y, z, facing, powered)` | |
| `placeMeteorite(x, y, z, active)` | |
| `placeAmmoCrate(x, y, z, facing)` | |
| `setAmmoCratePrice(price)` | |
| `forceMysteryBox(x, y, z, locked)` / `excludeMysteryBox(x, y, z, exclude)` | |
| `forceWunderfizz(x, y, z)` / `excludeWunderfizz(x, y, z, exclude)` | |
| `getPackAPunchState(x, y, z)` | `0` idle, `1` upgrading, `2` ready |
| `insertPackAPunchWeapon(uuid, x, y, z, free)` | |
| `collectPackAPunchWeapon(uuid, x, y, z)` | |

</details>

<details>
<summary><b>Mobs and timers</b></summary>

| Function | |
|---|---|
| `spawnMob(x, y, z, mobType)` | `"zombie"`, `"hellhound"`, … |
| `spawnBoss(x, y, z, mobType)` | |
| `spawnCustomBoss(x, y, z, mobType, hp, speed, scale)` | |
| `spawnCrawler(x, y, z)` / `makeZombieCrawler(uuid)` | |
| `getEntityCount(entityType)` / `getZombiesInRadius(x, y, z, radius)` | |
| `killNearbyEntities(x, y, z, radius, entityType)` | |
| `spawnThrowable(ownerUuid, type, x, y, z, vx, vy, vz, fuseTicks)` | `grenade`, `molotov`, `stielhandgranate`, `monkey_bomb` |
| `spawnBonus(bonusId, x, y, z)` / `spawnBonusAtPlayer(uuid, bonusId)` | |
| `strikeLightning(x, y, z)` | |
| `createExplosion(x, y, z, radius, damage)` | |
| `registerTimer(id, delayTicks, function() end)` | |
| `cancelTimer(id)` | |
| `registerLookTrigger(id, x, y, z, radius, seconds, requireScope)` | Fires `OnLookTriggerActivated` |
| `removeLookTrigger(id)` | |
| `setData(key, value)` / `getData(key)` | Strings kept for the session |
| `getRandomInt(min, max)` | |
| `setConfigFlag(key, value)` / `setConfigString(key, value)` | |

</details>

### Example

```lua
function OnBlockShot(playerUUID, x, y, z)
    if x == 10 and y == 5 and z == -20 then
        ZombieroolAPI:playGlobalSound("minecraft:entity.ghast.scream", 1.0, 0.5)
        ZombieroolAPI:triggerScopeScreamer(playerUUID)
        ZombieroolAPI:destroyBlock(x, y, z, false)

        ZombieroolAPI:registerTimer("scare_reward", 40, function()
            ZombieroolAPI:addPoints(playerUUID, 1000)
            ZombieroolAPI:showActionBar(playerUUID, "You found a secret!")
        end)
    end
end
```

---

## 📂 Data-Driven Customization

Weapons, perks, and power-ups are JSON.

| Where | What it does |
|---|---|
| Inside the mod, `data/zombierool/gameplay/weapons/` | The built-in guns |
| `.minecraft/data/zombierool/gameplay/weapons/` | Extra definitions for that whole instance |
| `saves/<YourWorld>/zombierool/weapons/` | Overrides a gun that already exists, for that map only. The file name is the weapon id. An unknown id is skipped. |

Perks and bonuses follow the same idea under `data/zombierool/gameplay/perks/` and `data/zombierool/gameplay/bonuses/`.

A map override can change damage, fire rate, ammo, Pack-a-Punch behavior, and sounds without shipping a new mod. A TacZ gun can be pointed at from JSON when TacZ is installed.

---

## 🔗 Links

| | |
|---|---|
| Discord | [Join the community](https://discord.gg/HGv2r44hXM) |
| CurseForge | [ZombieRool](https://www.curseforge.com/minecraft/mc-mods/zombierool) |
| Modrinth | [ZombieRool](https://modrinth.com/mod/zombierool) |
| Launcher | [ZR-Launcher](https://github.com/Cryo60/ZR-Launcher) |
| Bugs and ideas | The **Issues** tab on this repository |
