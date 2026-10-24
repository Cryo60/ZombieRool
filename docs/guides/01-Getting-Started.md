# Getting started

## Install and play

1. Use Minecraft **1.20.1**, Forge **47.1.3**, and Java **17**.
2. Install matching ZombieRool versions on server and clients, and **Player Animator** on clients. Install the map's required addons/gun packs too. TaCZ itself is optional.
3. Open the map and use Map Config to start a match. Operators open it with **P** by default. Check **Options → Controls → ZombieRool** for conflicts.

The [launcher](https://github.com/Cryo60/ZR-Launcher) can prepare a map's separate Forge instance. Install only copies the world; Play prepares and launches it.

When you enter a world, ZombieRool silently checks the latest public GitHub release in the background. A chat notice with installed/latest versions and a download link appears only when a newer release exists. Matching versions, older releases and an unavailable connection produce no message.

## Controls

- **F:** buy, repair, collect components, retrieve equipment, interact.
- **R:** reload; **V:** melee; **C:** prone/crawl.
- **H:** hold/stow portable equipment; **J:** equip/remove an environmental suit.
- **G:** lethal grenade; **Y:** tactical grenade; **X:** equipment slot.
- **Tab:** match information, including buildable progress, the shared Warden Key, and current career challenges with their progress.

Rebound keys and on-screen prompts take priority. Portable devices, suits and tactical/lethal equipment use separate systems.

## Make a test arena

1. In Creative, add player and enemy spawners from the ZombieRool inventory.
2. Configure their zones, channels and navigation. Give enemies connected paths/allowed terrain to the playable area.
3. Add a wall weapon, perk machines, power switch and an affordable Buyable Door.
4. Set starting weapon, points and wave rules in Map Config, then save.
5. Start a real match. Confirm spawning, pathfinding, purchases and wave completion.
6. End it and verify that doors, temporary machines and objectives reset.

Test new systems individually before combining them on a large map. Creative editing and match interactions differ.

## Map Config navigation

**General** contains map identity and shortcuts to the main sections. Configure player equipment in **Player**, round flow in **Waves**, common enemies in **Mobs**, and special encounters in **Enemies**. **Weapons** and **Extras** manage equipment and perks/power-ups.

**Crafting & delivery** groups buildable recipes and Sky Delivery. **Environment** groups the atmosphere editor, world settings, audio and ambient particles; **Fog** has its own section and a shortcut from Environment. **Scripting** groups the Lua editor, guided tools, API help and console. **System** contains match start/end and wave controls.

Apply your changes before starting a match. Sub-editors have their own save controls; verify both the edited system and the main settings when changing several sections.

[All guides](INDEX.md)

## Find a map setting

Use the search field above the categories on the left of **Map Config**. Results cover every category, including addon settings. Clear the field to return to the selected category. **Sprint Ambience** is under Environment; it is disabled by default. **Reduced Gore** remains a client option. The in-game **Maps** catalogue keeps its Official and Community tabs. **Get a link** shows a selectable address and a Copy link button. It does not install maps; installation remains available through the launcher.

The health panel is hidden by default. **Hide health display** is a client HUD option; turn it off to show the panel again. Injury feedback is enabled with either setting: camera shake, blood, subtle darkening and low-health audio recover as health returns.

Weapons that use heat or durability show their status bars instead of conventional magazine/reserve ammunition numbers. Ordinary firearms keep their ammunition counters. F1 hides the HUD while retaining the custom sky and sky objects, including with shaders.

Barricade repair uses a continuous floating-board sound while holding the interaction key; it trails off about 1.25 seconds after repair ends. The Ballistic Vest retains its existing protection rules. Power-ups now use rotating golden 3D objects and a green halo; collection and gameplay effects are unchanged.


## Browse maps

The map catalogue opens on **Official**. Open a card to inspect its requirements, compatibility and reviews before using **Get a link** to copy its download address. Community map publication uses Discord on the website; ratings submitted in game use a verified Minecraft account. See [Map catalogue and reviews](14-Map-Catalogue-and-Reviews.md) for account rules and service availability.


## New authoring systems

Use [Map Entity Workshop](16-Map-Entity-Workshop.md) for custom enemies and placed encounters, [Music Volumes](17-Music-Volumes-and-Hidden-Encounters.md) for local playlists and scripted appearances, and [Map weapon packs](18-Map-Weapon-Packs-and-Armor.md) for themed arsenals and armor stations. Authoring files now use the [canonical world layout](19-Map-Files-and-Migration.md).

Vanilla containers, doors, trapdoors, fence gates, item frames and hoppers are usable during matches. Dropped guns still keep player ownership rules. Nuke clears tracked wave enemies while preserving map encounter residents and bosses.

Large spawner setups share spawn/pathfinding budgets and population limits. A queued group can finish over several ticks. Corpse, blood and gore cleanup fades old visuals under load; it does not remove living enemies or count as wave kills.
