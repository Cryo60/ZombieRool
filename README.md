# 🧟‍♂️ ZombieRool

<div align="center">

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)
![Modloader](https://img.shields.io/badge/Modloader-Forge-orange?style=for-the-badge)
[![License](https://img.shields.io/badge/License-MIT-red?style=for-the-badge)](https://opensource.org/licenses/MIT)
[![Discord](https://img.shields.io/badge/Discord-Join_Us!-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/HGv2r44hXM)

**ZombieRool** completely transforms your Minecraft world into a fully-fledged, round-based Zombies survival experience. Survive endless hordes of the undead, rack up points, buy weapons off the walls, unlock new areas, and upgrade your arsenal to face increasingly difficult waves!

Whether you are a **player** looking to survive to Round 50, or a **Map Maker** wanting to design the perfect custom zombies map using Lua scripts and data-driven systems — ZombieRool has absolutely everything you need built right in.

</div>

---

## 📖 Table of Contents

- [How to Play](#-how-to-play)
- [TacZ Compatibility](#tacz-compatibility)
- [Map Creation Guide](#️-map-creation-guide-the-workshop)
- [Lua Scripting](#-lua-scripting-tutorial)
- [Data-Driven Customization](#-data-driven-customization)
- [Links & Community](#-links--community)

---

## 🎮 How to Play

### Basic Mechanics

| Step | Description |
|---|---|
| **Waves & Points** | You start at Wave 1 with a pistol and 500 points. Kill zombies to earn points. Headshots and melee (knife) kills grant bonus points! |
| **Buy Weapons & Perks** | Use your points to buy Wall Weapons, roll the Mystery Box, or drink Perks-a-Cola (Juggernog, Speed Cola, Quick Revive, etc.) to increase your survivability. |
| **Open the Map** | Maps are divided into zones. Spend points on Obstacle Doors (Debris) to unlock new areas and find the Power Switch. |
| **Turn on the Power** | Turning on the power enables Perk machines, the Wunderfizz, and the Pack-a-Punch machine! |
| **Pack-a-Punch** | Upgrade your weapons for 5,000 points to deal massive damage, get custom camos, and unlock special ammo types. |
| **Downs & Revives** | When your health drops to 0, you go into "Last Stand". You can crawl and shoot with a pistol while your friends have a limited time to revive you. |

### TacZ Compatibility

ZombieRool is **100% compatible** with the **Timeless and Classics Guns (TacZ)** mod. If you have TacZ installed, ZombieRool will automatically balance TacZ guns, allow them to be Pack-a-Punched, and put them in the Mystery Box!

---

## 🛠️ Map Creation Guide (The Workshop)

ZombieRool provides all the tools you need to create a map **without writing a single command block**.

### 1. The In-Game Config Menu

Press the **Config Menu Key** (check your controls) or type `/zombierool menu` (requires OP) to open the GUI. Here you can configure everything for your map:

- **General:** Day/Night cycle, starting weapon, death penalties, music presets, fog colors.
- **Waves & Mobs:** Zombie health scaling, crawler gas toggles, super-sprinter spawn rates, Hellhound rounds.
- **Weapons & Drops:** Enable/Disable specific weapons from the Mystery Box, configure drops.

### 2. Placing Blocks & Entities

All developer blocks can be found in the **ZombieRool Creator Tools** creative tab.

| Block | Usage |
|---|---|
| **Universal Spawner** | Place it down and Shift+Right Click to open its GUI. Set the Mob Type (Zombie, Dog, Crawler), the Zone, and the "Channels". |
| **Obstacle Doors / Debris** | Place these to block paths. Shift+Right Click to set the Price and the **Channel**. When a player buys this door, the linked Channel is unlocked. |
| **Wall Weapons** | Place the block on a wall. Shift+Right Click, drop a weapon in the slot and set its price. Use the **Chalk** item to draw a dynamic outline of the weapon! |
| **Barricades** | Place these in windows. Zombies will tear them down, and players can hold the interact key to repair them for points. |
| **Perk Machines, Mystery Box, Pack-A-Punch** | Simply place them down — they will automatically link to the global Power Switch. |

### 3. Dynamic Resources (No Resource Pack needed!)

You can easily add custom sounds, music, and mob skins that will **automatically sync to clients** when they join your map.

Navigate to your world folder: `saves/<YourWorldName>/zombierool/`

```
zombierool/
├── skins/          → Drop .png files to randomize zombie, crawler, or dog skins
├── audio/
│   ├── music/      → Drop .ogg files for custom background music
│   └── voices/     → Drop .ogg files for custom player voice lines
```

---

## 📜 Lua Scripting Tutorial

ZombieRool includes a powerful built-in **Lua Engine** for scripting complex Easter Eggs, boss fights, jump scares, and dialogue.

### Setup

1. Run your map once.
2. Navigate to `saves/<YourWorldName>/zr_scripts/`.
3. Open or create `main.lua`.
4. Type `/zombierool reload` in-game to apply changes instantly.

### Available Events

ZombieRool automatically calls these functions in your Lua script when specific actions happen:

```lua
-- Game cycle
function OnGameStart() end
function OnWaveStart(wave) end
function OnWaveEnd(wave) end
function OnAllZombiesDead() end

-- Player actions
function OnZombieKill(playerUUID, mobType, isHeadshot) end
function OnEntityKilled(playerUUID, entityId, isHeadshot) end
function OnBlockInteract(playerUUID, x, y, z) end
function OnActionKeyPressed(playerUUID) end  -- When the interact key (F) is pressed on nothing
function OnItemUsed(playerUUID, itemId) end
function OnBlockShot(playerUUID, x, y, z) end
function OnMeleeStrike(playerUUID, x, y, z) end
function OnExplosion(playerUUID, x, y, z, radius) end
function OnProjectileHit(playerUUID, x, y, z) end

-- Map events
function OnPowerActivated() end
function OnPlayerDown(playerUUID) end
function OnPlayerRevive(reviverUUID, revivedUUID) end
function OnPlayerDeath(playerUUID) end
function OnPlayerRespawn(playerUUID) end

-- Gameplay
function OnBonusCollected(playerUUID, bonusId) end
function OnPerkBought(playerUUID, perkId) end
function OnMysteryBoxUsed(playerUUID, weaponId) end
function OnLookTriggerActivated(playerUUID, triggerId) end
```

### ZombieroolAPI Reference

<details>
<summary><b>🎮 Game & Waves</b></summary>

| Function | Description |
|---|---|
| `ZombieroolAPI:setWave(wave)` | Sets the current wave |
| `ZombieroolAPI:pauseWave()` | Pauses the current wave |
| `ZombieroolAPI:resumeWave()` | Resumes the current wave |
| `ZombieroolAPI:setMaxZombiesAlive(count)` | Caps simultaneous zombie count |
| `ZombieroolAPI:endGame(message)` | Ends the game with a message |

</details>

<details>
<summary><b>👤 Players & Points</b></summary>

| Function | Description |
|---|---|
| `ZombieroolAPI:addPoints(uuid, amount)` | Grants points to a player |
| `ZombieroolAPI:removePoints(uuid, amount)` | Removes points from a player |
| `ZombieroolAPI:getScore(uuid)` | Returns current score |
| `ZombieroolAPI:getPlayerHealth(uuid)` | Returns current HP |
| `ZombieroolAPI:getPlayerMaxHealth(uuid)` | Returns max HP |
| `ZombieroolAPI:isPlayerDown(uuid)` | Checks if player is in Last Stand |
| `ZombieroolAPI:teleportAllPlayers(x, y, z)` | Teleports all players |
| `ZombieroolAPI:getActivePlayers()` | Returns a Lua table of UUIDs |

</details>

<details>
<summary><b>🖥️ UI, Visuals & Audio</b></summary>

| Function | Description |
|---|---|
| `ZombieroolAPI:showTitle(uuid, title, subtitle, fadeIn, stay, fadeOut)` | Displays a title on screen |
| `ZombieroolAPI:showActionBar(uuid, message)` | Displays a message in the action bar |
| `ZombieroolAPI:setObjective(uuid, text)` | Creates a BossBar (leave empty to remove) |
| `ZombieroolAPI:playSound(x, y, z, soundId, volume, pitch)` | Plays a sound at position |
| `ZombieroolAPI:playGlobalSound(soundId, volume, pitch)` | Plays a sound for all players |

</details>

<details>
<summary><b>🗺️ Entities & Map</b></summary>

| Function | Description |
|---|---|
| `ZombieroolAPI:spawnMob(x, y, z, mobType)` | Spawns a mob (`"zombie"`, `"hellhound"`…) |
| `ZombieroolAPI:spawnBoss(x, y, z, mobType)` | Spawns a boss |
| `ZombieroolAPI:killNearbyEntities(x, y, z, radius, entityType)` | Kills nearby entities |
| `ZombieroolAPI:destroyBlock(x, y, z, playSound)` | Destroys a block |
| `ZombieroolAPI:openObstacle(channel)` | Instantly opens all doors linked to this channel |

</details>

<details>
<summary><b>⏱️ Timers & Triggers</b></summary>

| Function | Description |
|---|---|
| `ZombieroolAPI:registerTimer(id, delayTicks, callbackFunction)` | Registers a timer with a callback |
| `ZombieroolAPI:registerLookTrigger(id, x, y, z, radius, seconds, requireScope)` | Fires `OnLookTriggerActivated` if a player looks at this position for X seconds |

</details>

### Example Script: A Simple Jump Scare Easter Egg

```lua
-- When the player shoots a specific block (e.g., a hidden teddy bear head)
function OnBlockShot(playerUUID, x, y, z)
    if x == 10 and y == 5 and z == -20 then
        -- Play a scary sound globally
        ZombieroolAPI:playGlobalSound("zombierool:screamer_sound", 1.0, 1.0)

        -- Trigger a visual screamer on the player's screen
        ZombieroolAPI:triggerScopeScreamer(playerUUID)

        -- Remove the block so it can't be shot again
        ZombieroolAPI:destroyBlock(x, y, z, false)

        -- Give them a reward 2 seconds (40 ticks) later
        ZombieroolAPI:registerTimer("scare_reward", 40, function()
            ZombieroolAPI:addPoints(playerUUID, 1000)
            ZombieroolAPI:showActionBar(playerUUID, "§aYou found a secret!")
        end)
    end
end
```

---

## 📂 Data-Driven Customization

ZombieRool uses **JSON files** for almost everything. You can create your own custom weapons, perks, and power-ups by placing JSON files in:

```
saves/<WorldName>/data/zombierool/gameplay/weapons/
```

> 💡 **Example:** Want a weapon that shoots explosive Ray Gun projectiles with a custom TacZ model? You can do it entirely via JSON!

---

## 🔗 Links & Community

| | |
|---|---|
| 💬 **Discord** | [Join the Community!](https://discord.gg/HGv2r44hXM) |
| 🐞 **Bug Reports & Suggestions** | Use the **Issues** tab on this repository |
