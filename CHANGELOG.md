# ZombieRool 26.7 Beta 1
Minecraft 1.20.1 | Forge 47.1.3 | Java 17

## Map entities and encounters


- Map Entity Workshop: create/import world-owned enemy profiles, obtain named
  eggs, and edit normal settings through forms. Advanced JSON configures models,
  bones, textures and animation; Lua supplies custom attacks.
- Enemy Spawners: game-start, wave-start and interval triggers; batch size,
  population, radius, redstone, independent aggro/patrol/leash settings, movement,
  round or fixed health, knife resistance and optional wave quota. Ctrl + middle-
  click copies complete configuration. Live behavior edits update living enemies.
- Hostile factions can fight one another and players. Profiles can select from a
  default appearance and up to four alternatives, preserving the choice on reload.
- Music Volumes: per-player playlist switching on entry, previous-music restore
  on the next entry, priority and fades. Creative traversal previews audio only.
- Hidden-spawn APIs find loaded, supported, reachable path-block positions out of
  sight and return failure if none exists. Lua hooks connect music and encounters.

## Weapons, armor and combat


- World-owned weapon packs carry balance, models, textures, animations and sounds.
  Assets synchronize to clients without a full resource-pack reload and are cleared
  when leaving the map. Maps can enable their own themed arsenals.
- Bows draw/release; crossbows and powder guns use normal fire/reload controls.
  Melee supports repairable durability, light combos and charged heavy attacks.
  Scarlet Sword and Shield shares one slot, PAP upgrade and durability pool.
- Gambeson, chainmail and plate stations sell and repair armor. Protection applies
  after normalized enemy damage and is configurable by the map pack.
- Ray Gun and Mark II have revised recoil, projectile and impact effects. Mark II
  retains three burst impulses. Compatible TaCZ Wunderwaffe chains electricity and
  stuns instead of exploding. Whisper's final shot releases crows from the target.
- Nuke clears tracked wave enemies, preserving map residents, hidden encounter
  enemies and bosses. Its 400-point reward still applies to every player.

## Buildables and survival


- Collect components and assemble recipes at workbenches. Presets include Zombie
  Shield, Turbine, Sentry Turret and Pack-a-Punch; map authors enable the recipes.
- Shield front/back protection, temporary turbine power and recoverable,
  destructible sentry turrets. Portable equipment and environmental suits use
  separate slots.
- Astronaut, diving and hazmat suits, with stations and environmental protection;
  vacuum, reduced gravity and underwater routes are configurable.
- Warden Locks require a team-shared key. Maps choose boss drops; Brutus enables
  the key by default. The team keeps it after a player's death.
- Door-channel perk roulettes and periodic sky delivery of perks or Pack-a-Punch.

## Enemies and round flow


- Panzer, Brutus, George Romero, Astronaut, Pentagon Thief and Abomination have
  distinct attacks and encounter settings. Map authors configure timing and loot.
- Scourge appearance mixes Ghouls, Zombies and Skeletons. Crawler/Geist appearance
  is independent of ground/gas or leaping behavior.
- Outnumbered supports continuous survival before normal rounds or during a
  suspended round. Teleporters can end/enter it and change spawner channels.

## Atmosphere and scenery


- Import skies and Ogg ambience; edit fog, color, particles, sounds and custom
  presets. Included themes include Nuketown, Moon, Nacht, Die Rise, Origins,
  Revelations, The Giant, Shangri-La, Blizzard and Zenith.
- Sky scenes, customizable moon objects, decorative aircraft, airships, tornadoes,
  anti-aircraft guns and a Dwarven Bombard model. Necropolis can fly or hover.
- Expanded furniture and static vehicles, model/state/orientation settings and
  Buyable Obstacle integration. Flags and wall standards provide 17 patterns and
  34 props, with complete-footprint placement and removal.
- Music playlists shuffle through all tracks before repeating. Ambient loops are
  independent, and easter egg music takes playback priority.

## Menus, catalogue and creator workflow


- Map Config search, reorganized Creative categories, integrated Lua editor,
  guided rules, searchable API help and live console.
- Catalogue opens on Official with thumbnails, search, filters, compatibility,
  dependencies and reviews. Get a link shows a selectable address and Copy link.
- One editable in-game review per verified Minecraft account and map; submission
  requires a compatible community service. Map publication uses Discord on the site.
- Career challenge progress in Tab; heat/durability HUD bars; health display hidden
  by default with a client setting to show it. Injury feedback remains active.
- Creator home keeps the reward-code generator. Camo Studio is retired; camo
  appearances use built-in defaults rather than per-player editor preferences.

## World files and lua


- Map content is organized under zombierool/ for easier editing and sharing.
  Existing maps are supported, and automatic migration keeps backups.
- Canonical scripts use zombierool/scripts/. A matching canonical filename wins
  over legacy zr_scripts/, avoiding duplicate callback loading.
- Lua exposes map actors, armor, music triggers, hidden spawning and match state.
  Player-context timers inherit gameplay eligibility, including chained timers.
- Gameplay spawning requires an active, unpaused match and eligible players;
  Creative music previews do not start encounters. Lua uses supported APIs and
  libraries without direct Java, OS or arbitrary file access.

## Fixes and performance


- Spawner batches share fair appearance/placement budgets. Patrol and hidden-spawn
  pathfinding have their own allowances. Living map-actor limits reserve capacity
  for scripted encounters and bosses; corpses do not consume living slots.
- Horde navigation is distributed to reduce movement stalls; ordinary crawlers
  can attack Defense Walls and navigate stairs. Stuck patrols request new paths.
- Corpse, blood and gore cleanup is staggered and fades under population pressure.
  Gore pieces settle instead of spinning indefinitely when blocked.
- Vanilla containers, doors, trapdoors, fence gates, item frames and hoppers are
  usable during matches; player ownership rules for dropped weapons remain.
- Positional enemy voices and Whisper/Wunderfizz audio; quieter power-up loops,
  continuous barricade-repair audio and zombie_ambient during pause.
- Shader/TaCZ compatibility, combat effects, placement/collision, wave accounting
  and match reset improvements. Updated text in all six supported languages.

## Guides


Open the [English setup guides](docs/guides/INDEX.md) for step-by-step instructions. New guides cover
Map Entity Workshop, music/hidden encounters, weapon packs/armor and migration.
The Lua reference includes the available functions and their parameters.
