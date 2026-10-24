# Stratholme weapon pack

Stratholme owns its arsenal. Copying the save carries its balance, weapon models, texture atlases, sound variants and weapon pose curves. Other maps do not offer these weapons unless their own pack enables the corresponding identities.

The authoritative folder is `zombierool/gameplay/weapon_packs/stratholme/`. `pack.json` enables the pack and defines armor protection, purchase prices and repair prices. `weapons/` contains the weapon definitions, including the `animation` channels. `assets/zombierool/` follows Minecraft resource-pack layout for models, textures, sounds and translations. The server sends the map assets automatically. Models, images, translations and audio are decoded directly without reloading Minecraft resources. Clients discard these assets on leaving the world. Stable item identities remain registered so existing wall purchases and saved stacks continue to resolve.

The pack contains 24 ordinary weapons. All consume the normal weapon slots and support Pack-a-Punch and applicable perks. Melee uses repairable durability. Ranged weapons use loaded ammunition and reserve ammunition.

- Early: Militia Shortbow, Hunting Crossbow, Dwarven Flintlock, Worn Dagger, Ranger's Hunting Knife, Militia Arming Sword.
- Mid: Longbow, Scarlet Crossbow, Ironforge Musket, Dwarven Blunderbuss, Runed Combat Knife, Lordaeron Claymore, Scarlet Sword, Scarlet Axe, Scarlet Halberd, Scarlet Sword and Shield.
- Late: Quel'Thalas Warbow, Siege Crossbow, Mountain Guard Rifle, Deathstalker Dagger, Lordaeron Runeblade, Runed Greatsword.
- Existing ordinary spell weapons: Fire Wand and Shadow Wand.

Hold the normal use button to draw a bow, then release to fire. A very short tap does not consume an arrow. Crossbows and powder firearms use the normal firing and reload controls. Powder firearms emit smoke and have deliberate reloads; the blunderbuss fires multiple pellets. Daggers reward attacks from behind. Greatswords use both hands, longer reach and a sweeping hit. The Scarlet shield remains an attached offhand guard accompanying its main-hand sword.

The recorded `GunFire`, `GunLoad`, `BowRelease` and `BowPullback` clips are stored in this map pack. Pose channels have three coefficients: idle, windup/reload and strike/recoil. First-person rotations are degrees; third-person arm rotations are radians.

## Armor

Armor protection is applied after ZombieRool normalizes enemy damage. This avoids vanilla armor being bypassed by fixed damage. Protection does not block forced kills or environmental hazards. The protection of each complete set is divided across its four pieces and only applies while the pieces remain intact.

- Gambeson: 20% protection, 500 points to buy, 150 to repair.
- Lordaeron Chainmail: 35% protection, 1,500 points to buy, 300 to repair.
- Lordaeron Plate: 50% protection, 3,000 points to buy, 500 to repair.

Place `map_gambeson_station`, `map_chainmail_station` or `map_plate_station` from the creative Machines category. Use the normal purchase key (F by default), or right-click during a match to buy/equip the set; returning with the same damaged set repairs it. Intact armor never charges again. Creative interaction previews its configured prices. Stations do not insert themselves into existing map geometry.

Lua offers `ZombieroolAPI:buyMapArmor(playerUUID, set)` for scripted purchases, returning `equipped`, `repaired`, `full`, `funds` or `unavailable`. `equipMapArmor(playerUUID, set)` equips a set as a scripted reward without charging. Set names are `gambeson`, `chainmail` and `plate`.

## The Risen

All Risen remain map-owned enemy profiles in `zombierool/gameplay/entities/`, with cuboid models, source player animation tracks and green eyes. Six roles exist in both male and female forms: Crusader, Swordsman, Axeman, Halberdier, Archer and Mage. Their Scarlet tabards and shields carry the flame emblem. They use English undead-player exertions and separate male/female voices.

The unified role identities remain `scarlet_zombie`, `scarlet_zombie_swordsman`, `scarlet_zombie_axeman` and `scarlet_zombie_pikeman`. New identities are `scarlet_zombie_archer` and `scarlet_zombie_mage`. Male/female appearance is chosen automatically and independently at spawn, including its model, animation tracks and voice. The chosen appearance survives saving and reloading. Separate `_female` entries are removed from the catalogue; old spawners remain compatible. Named eggs appear in Spawn and spawners. The Enemy Spawner selects the same profiles. Ordinary ghoul/zombie/skeleton catalogue entries still use the original mod entities.

Archers draw and release physical arrows; mages telegraph their fire projectile. Both keep their normal melee attack when approached. They share the engine's targeting, retaliation, Zombie Blood, faction, path-block, knockback and round-health rules. Their map script is `zombierool/scripts/stratholme_risen.lua`. The existing personal music-trigger ambush chooses among these six roles.

## Save layout and music

Documentation belongs in `zombierool/docs/`. All map scripts now live under `zombierool/scripts/`, beside `entities/`, `audio/`, `skins/`, `particles/`, `assets/` and `weapon_packs/`. The runtime can still read legacy `zr_scripts/`; a canonical script with the same name takes precedence, preventing duplicate callback loading. The editor lists both locations for maps that have not been migrated.

`tools/migrate-world-scripts.py <save> --apply` backs up all legacy script files, checks SHA-256 before and after copying, and removes the old copies only after verification. Conflicting destinations stop migration without overwriting either file. Existing user `main.lua` is preserved byte for byte. World geometry, `level.dat`, region files, player data and music playlists are not moved.

Music volumes can be previewed by walking through them in creative, outside a match. This sends audio changes only; it does not call the Lua gameplay hooks or start enemy encounters. During gameplay, existing entry-toggle semantics remain unchanged. The easter egg song has priority over music-player selections; the latest map selection resumes when the song ends. Match lifecycle resets still restore the map's base preset.

Melee: chain light attacks for alternating cuts and a third-hit finisher. With an empty offhand, hold right-click for at least 16 ticks, then release for a heavy strike (1.8x damage, 3 durability). The shield kit keeps its usual offhand blocking.

Folder layout: `config/settings.json`, `gameplay/entities/`, `gameplay/weapons/`, `gameplay/weapon_packs/`, `assets/audio/`, `assets/textures/`, `assets/particles/`, `assets/blocks/`, `scripts/`, `docs/`. Old save layouts remain readable.

Automatic cleanup: every three minutes the server schedules existing corpses, blood pools and gore for removal. Starts are staggered over thirty seconds and each object fades out over three seconds. Living enemies and players are excluded.

Map armor pieces share the Map Weapons creative category. Armor stations remain in Machines. Dead map actors are noncolliding and cannot push players while awaiting cleanup.

The personal music ambush chooses the combat role at random; the engine independently chooses male or female at each spawn. Archer and mage projectiles aim at the target's torso; their unarmored player damage is respectively 2 and 3. Map armor then applies its configured protection.

Faction projectiles pass through dead bodies and friendly entities. Arrows anticipate a moving enemy's position; fire and shadow spells can follow a hostile faction entity during flight. Player-directed attacks retain their original dodgeable trajectory.

Geists belong to Scourge both as ordinary round enemies and as map-spawner enemies. Selecting Geist instead of Crawler or Pentagon Thief instead of Hellhound in map configuration replaces the corresponding creative egg, without adding a duplicate.
