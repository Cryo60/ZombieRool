# ZombieRool 1.6.3

## Menus

- The title screen is a World at War style menu. The background is a dark grayscale block village, drawn on a grid and sliding in two layers. Entries sit on the left. The selected line is white. Solo, multiplayer, maps, career, guns (if TaCZ is loaded), options, mods, and quit.
- Menu sounds: moving the cursor plays the slider, confirming a main entry plays the main click, options and the other mod screens play the secondary click, and opening the title menu plays the button sound.
- Escape during a game opens a pause screen over the level, with black bars and a thin line at the top and bottom. On the right: PAUSED, then Resume, Options, Restart level, and Quit. The selected line is white, with a light bar behind it. Restart starts the match over without the end-of-game recap. On a server, only the host or an operator can restart. Quit saves and returns to the title menu.

## Discord

- Rich Presence is built into the mod. No extra jar. It shows ZombieRool, the map name, the round, and the player count, in the language selected in the game. It keeps refreshing so Discord does not drop it. The application id is in `config/zombierool-client.toml`.

## Included library

- LuaJ 3.0.1 is packed inside the mod jar, moved to `me.cryo.zombierool.shadow.luaj`, so it does not clash with the copy TaCZ already ships. Do not install a separate LuaJ mod.

## Barriers

- Stepping into a death barrier or a damage barrier starts a 5 second warning, with a countdown and a screen of static. Leaving in the last half second cancels it. Staying deals the hit once per second, or kills you when the death timer ends.
- The normal return-to-combat voice plays with the warning. A longer variant plays about one time in twenty, cut so it ends with the warning.
- Lua can listen with `OnBarrierSpotted`, `OnBarrierExecute`, and `OnBarrierMiss` (`playerUUID`, `kind` is `death` or `damage`, then x, y, z).

## Match rules

- Chests, barrels, shulkers, hoppers, and the ender chest can be opened during a match. Guns bought on the map (wall, mystery box, and the other map weapons) cannot be put inside. Normal items still can. A weapon already in the container can be taken out. Hoppers cannot move map weapons into storage either.

## TaCZ and zombies

- Speed Cola speeds up the TaCZ reload animation.
- Pack-a-Punch clip bonus and Max Ammo refill TaCZ guns.
- Eye skins no longer have to match the body name.
- Crawlers and legless zombies have a hitbox you can actually shoot, including a corpse that is still lying there.

# ZombieRool 1.6.2

- Lua scripts load when TaCZ is not installed.

# ZombieRool 1.6.1

For players on the last public build, **1.6.0-stable** (8 April 2026). The hotfixes before that (through hotfix 6) stay included: the TaCZ startup crash, and careers no longer wiping themselves.

## Playing a match

- If you die during a round, you wait as a spectator until the next wave. At the end of the game you come back on your player spawner, with full health and a fresh loadout. Creative map makers are left where they are.
- Leave in the middle of a match and come back later: if the match is still going, you return as a spectator with an empty inventory. If the match is over, your inventory, effects, and points are reset (500 points).
- A gun you throw stays on the ground, and only you can pick it up. It disappears when the slot it came from is filled by a new weapon. Hoppers and other mobs cannot take it.
- Knife a Mystery Box weapon or a Wunderfizz drink to offer it to someone else. You cannot take it back. The first other player who takes it gets it, and the machine closes. The offer lasts 45 seconds.
- During a match you can still use levers, buttons, pressure plates, tripwire, repeaters, comparators, and daylight detectors. Containers stay locked, and so do doors, trapdoors, and fence gates, so a match cannot be used to store guns or to open decorative gates. Redstone can still power those blocks.
- Bullets and blood pass through invisible map blocks such as Restrict. Players are still stopped by them.
- A dead zombie no longer breaks a defense door or a defense wall. Defense walls draw their full length again.
- The map fog works with a shader pack. In the map settings, fog can be limited to the outdoors. That option is off unless the map maker turns it on.
- How much gore you see is in Minecraft Options, under ZombieRool Options. Full gore is the default.

## Gore

The 1.6.0 gore was replaced. What you should see now:

- A zombie that dies falls onto its side or its back and stays there for about three minutes. The pose is not the same every time. Crawlers use the same fall, except a gas crawler, which still bursts.
- Bullets and headshots tear off arms and heads. The pieces land in the world. A lost limb sometimes sprays blood, and the spray follows the body.
- A knife cut still kills, but it does not count as a headshot and it does not tear anything off.
- Kills with the flamethrower, fire, or lava char the body. Charred corpses do not explode into blood and meat. A bullet that kills a zombie already on fire still uses normal gore.
- Blood lands as puddles on floors, walls, and ceilings. Several puddles on the same block blend together instead of flickering when you move. A puddle fades after about two minutes, and all of them clear when the match ends.
- Corpses are not stuck red, and a dead zombie does not leave a round shadow on the ground.

## For map makers

- The texture kit is in the decoration tab of the creative menu. Put PNG files in `zombierool/custom_blocks/` inside the map (up to 32). Each texture becomes a block, stairs, a slab, a fence, a wall, and a pane, and it travels with the map. Each one can use a vanilla sound (stone, wood, metal, glass, and the other kit sounds).
- New footstep sounds for grass, concrete, wood, gravel, snow, ice, mud, dirt, stone, and brick.
- An addon can place machines from a text file without opening the map. ZombieRool does not ship Gobblegum; that machine type does nothing until an addon registers it.
- Weapon JSON files can live in the map folder, and they override the mod defaults for that world.
- At the end of a match, blocks that changed are put back. Defense doors keep their look, their price, and their text. The hidden 100-point perk crawl is forgotten, so the next game can find it again.
- Turning the power on updates perks, Pack-a-Punch, and the Wunderfizz even if those machines were placed before the switch.

## Launcher

Play and Install are different. Play builds a separate Forge instance for that map, so one map's mods do not replace your usual `mods` folder. Install only copies the world into the folder shown at the bottom.

- Sign in with Microsoft before Play. The name used is the Minecraft account. Without a Java license, the game does not start.
- Video, controls, and sound are copied once from your normal Minecraft, then shared between maps.
- **My mods** is a folder. Jars you put there are added to every map. The whole `mods` folder of your normal Minecraft is no longer copied on its own, which is what was installing TaCZ everywhere.
- **TaCZ and gun packs** is off by default. Leave it off and TaCZ, plus gun packs listed by the map, are not installed. Player Animator is still installed, because the mod needs it.
- Downloads are HTTPS only, and only from Modrinth, CurseForge, the Cryo60 GitHub, or the official Minecraft and Forge servers. A map archive cannot contain a jar, an executable, or a script, and it cannot write outside its own folder.
- CurseForge links are resolved to the real file, so the name you see is the mod, not `curseforge.com`.
- A mod that cannot load is skipped instead of taking the whole launch down.
