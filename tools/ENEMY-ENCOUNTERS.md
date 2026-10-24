# Enemy encounters and test map

The test save is installed in `run/saves/ZombieRool - Enemy Encounter Lab`.
Start a match normally, then right-click the eight floor buttons along the south
edge. The rear central button clears encounters and returns weapons before removal.
The north gallery shows all eight appearances before match start. It is cleared by
the ordinary match reset. There is no embedded resource pack or resource reload.

## Map Config

- **Enemies / Encounters and variants / Special round enemy:** Hellhound or
  Pentagon Thief. Thief maps reject all Hellhound spawns, including normal rounds
  and scripts. Existing incompatible entities are removed before a new encounter
  can join. A Thief round contains one Thief, scaled with the round/player count.
  It uses Hellhound spawners, with ordinary zombie spawners as a fallback.
- **Enemies / Boss:** George Romero, Astronaut or Abomination use the existing
  boss spawner positions. Custom profiles are normal datapack JSON files under
  `data/<namespace>/mini_bosses/`. Romero arrives from match start, is unique while
  alive, never blocks regular wave completion, and returns after the configured
  number of rounds following death. Water clears his rage.
- **Enemies / Crawler appearance:** Nova or Geist selects only the model.
- **Enemies / Crawler behavior:** Ground + gas or Leaper, independently of appearance.
  Both share the same telegraphed, locked-direction leap with one physical impact.
  Leapers never release death gas. Ground mode uses the existing gas policy and
  global gas-explosion toggle. The choice is also exposed as `crawlerBehavior`
  (`gas` / `leaper`) through `configureSpecialEnemies` and its getter.
- **Mobs / Appearance / Scourge:** one mixed horde contains Ghouls, Zombies and
  Skeletons. Every spawn picks one of the three with equal probability, then one
  of its source-referenced colour variants: 4 Ghoul, 3 Zombie and 4 Skeleton skins.
  Old split-preset settings/scripts are mapped to Scourge; existing explicit
  saved skin IDs remain readable.

Abomination hook distance is configurable and bounded. The projectile is blocked
by solid geometry; its pull stops at an obstacle or before entering melee range.
The vomit cone requires line of sight, marks exposed players, and applies a short
damage-over-time effect. The Astronaut chooses only destinations reached by a
bounded flood through standing, dry, loaded collision volumes; if no suitable
destination exists, the headbutt has no teleport. This deliberately favors safe
progression over long-distance relocation.

The Thief saves complete stolen ItemStacks, including upgrades and custom NBT.
Death returns stacks once. A full/disconnected owner's inventory falls back to
an owner-targeted, non-expiring item. Script removal and match reset return the
stacks; a successful escape does not. It immediately excludes the robbed victim from targeting and flees along a
reachable route. After a teleporter escape hop, another armed, unvisited player
may become its target. No player loses a second weapon to the same Thief.
The alarm loop and ordinary round state are independent of the Hellhound fog.

## Lua

`ZombieroolAPI:configureSpecialEnemies({...})` and `getSpecialEnemiesConfig()` expose
the mode and balance settings. New calls:

```lua
local enemy = ZombieroolAPI:spawnEncounter("abomination", 0.5, 65, 4.5)
local state = ZombieroolAPI:getEncounterState(enemy)
ZombieroolAPI:despawnEncounter(enemy)
ZombieroolAPI:setRomeroEnraged(romeroUUID, false)
local safe = ZombieroolAPI:findSafeEnemyTeleport(playerUUID)
```

`listSpecialEnemies`, `getSpecialEnemy`, boss profiles, damage functions and
`setEnemyAppearance` also cover these enemies/appearances. Events are
`OnThiefWeaponStolen`, `OnThiefWeaponReturned`, `OnThiefEscaped`,
`OnRomeroRageChanged`, `OnRomeroRoar`, `OnRomeroGroundSlam`,
`OnRomeroZombieBuff`, `OnAstronautGrab`, `OnAstronautTeleport`,
`OnAbominationHook`, `OnAbominationPlague`, `OnGeistLeap`, `OnGeistLeapHit`, `OnCrawlerLeap`, `OnCrawlerLeapHit`.
Common interaction events are offered in the guided Lua editor; existing event
indices have been preserved. `tools/enemy_assets/lab.lua` is the complete editable
script delivered with the test map.

## Assets and temporary storage

Raw-source work originally used `%LOCALAPPDATA%/Temp/ZombieRool-enemy-work`;
the cuboid redesign uses `%LOCALAPPDATA%/Temp/ZombieRool-enemy-blocky`.
Remove them with `tools/clean-enemy-work.ps1` / `tools/clean-blocky-enemy-work.ps1`. It validates the exact folder and
refuses linked paths. Final textures, converted audio and provenance stay in the
project. Source game archives are opened read-only.

The ten original `fxt_misc_nixnumber_*pc` masks supply the red Thief digits.
All eight enemies use individually authored Minecraft cuboids and pixel atlases,
following the Panzer/Brutus style. Original source meshes only served as visual
and joint-motion references. The ten-joint Minecraft armatures retain retargeted
source timings without displaying source polygons. Abomination keeps its third
arm, open belly, stitches, hook and cleaver; Romero carries a block-built projector.
The model JSONs and atlases are under `models/entities/blocky`,
`textures/entities/blocky`; retargeted motion is under `animations/blocky`.

Original audio now includes the Thief alarm, ambient, sprint, steal, anger and
death sets plus Five announcements; Romero voices, steps, projector loops,
swings, impacts and electrical sounds; the Astronaut loop/pop/teleport; and
WoW creature idle/attack/wound/death sets. Audio is mono Ogg, with a global round-owned Thief alarm and positional
creature loops that stop on death, match end, disconnect and sound reload.

Romero has separate activation, melee, ground-slam and roar states. The slam
electrifies nearby visible zombies; the roar accelerates them from round 6.
Rage persists on dry ground and water calms him. Hit timing is taken from the
original `fire`/`wrench_hit` animation notifications, including the Astronaut's
headbutt frame. Navigation, collision-safe teleports and ability balancing
remain Minecraft adaptations. Abomination hook/vomit use authored cuboid poses, a three-dimensional metal
chain/hook, a visible windup and gradual plague pulses.

Source receipts: `blocky-designs.json`, `scourge-blocky-variants.json`,
`source-meshes-{cod,wow}.json` and `source-audio.json` under
`assets-source/26.7/enemy-skins/`. Final cuboid models/atlases are authored by `build_blocky_enemies.py` and
`build_scourge_variants.py`. The renderer loads original per-creature bone tracks through full quaternion
retargeting, retaining pelvis translation, head motion and limb twist. Hip
anchors keep the upper body and thighs attached. The early direction-only
retargeter and the subsequent procedural replacement are superseded.
`rebuild_blocky_motion.py --extract-cod` regenerates animation-only assets from
selected local reference inputs. `retarget_blocky_enemies.py` delegates to it.
Source geometry is not rendered or included in these animation assets.
`import_enemy_audio.py` preserves the original sound sets.

Format readers reference OpenAssetTools and wow.export. The limited CASC reader
only opens selected FileDataIDs, checks BLTE block hashes, and does not extract
the game wholesale. Windows blocked the downloaded CASCConsole executable; it
was not used. The task used a separate read-only Python format reader instead.

## Verification

`tools/tests/run-portable-equipment-tests.py --enemies` exercises configuration,
exclusive spawning, real weapon NBT retention, idempotent restoration, persistent
boss wave accounting, collision-safe destinations, dodgeable jump contact, hook
distance, headbutt teleport, Lua and the delivered map button script.
`tools/tests/render-map-atmosphere.py --enemies` renders all eight appearances in
an isolated client. These checks cover the implementation boundaries; a long
multiplayer balance playtest remains useful.

The server regression now advances actual Thief AI ticks on path markers and
checks victim exclusion, second-weapon protection and water calming. The native
client test captures each model close up and distinguishes Thief fog from dog fog.

The redesigned models, mixed horde and crawler behavior are covered by the
current 51-check encounter test and the native eight-model/variant gallery test.
The disposable extraction workspace was deleted (1,839,273,995 bytes).

Cuboid revision validation: 51 encounter checks, 28 native client checks and
311 existing regression checks passed. Temporary cuboid-reference exports were
removed (959,224,953 bytes); imported polygon resources are no longer shipped.

Animation/balance revision: whole-body falls finish before the common corpse
deathTime cap, continuous gait, a covered Geist waist and bounded headbutt poses.
All new enemy hurt vocals have a 40-tick cooldown. Default Abomination health
is 850 and Astronaut health 420; map-specific overrides remain available. The
hook reaches 24 blocks, plague warns for 20 ticks and deals 0.5 health points
every 30 ticks after contact (40-tick refreshed duration). Ability casts exclude
melee. Astronauts choose a persistent name from 64 aliases plus online players,
and their death blast knocks nearby players back without damaging terrain.
Thief theft directs the victim camera for 20 ticks; escape pads require a
32-tick windup and have a 120-tick cooldown, persisted across entity saves.

Revision verification: 62 server encounter checks and 44 native client checks
passed. Captures include headbutt/recovery, the metal chain/electrical arcs,
falling bodies and settled corpses.

Source-motion restoration: all eight models use their own original idle,
locomotion, attack and death clips. Quaternions interpolate between samples and
clips; torso/leg anchors follow the same pelvis. Corpse playback has its own
clock so it continues beyond the common deathTime=19 cap, then holds the real
last frame. No universal face-down rotation is applied. The Geist has longer
limbs, a narrower head, more cuboid details and a calibrated crouched bind
stance; original animated deltas are retained. Its collision height follows
the revised visible silhouette. Balance, audio and ability fixes are retained.

Source-motion validation: 62 encounter checks and 60 native client checks
passed. Native captures include a complete Geist walk/death sequence and all
eight original death poses. Disposable motion references were removed
(998,307,278 bytes). Cleanup: tools/clean-motion-reference-work.ps1.

Follow-up posture/effect revision: Ghoul rest stance is unfolded at thorax,
neck and knees while retaining its source motion deltas. Ghoul and skeleton
death clips carry independent upper/lower offsets, with each half grounded.
Skeleton melee completes its source clip at 0.74x speed instead of fitting it
into the six-tick vanilla swing. Geist locomotion uses a four-point contact rig;
its accepted source death clip is unchanged in its original 47 channels.
All Scourge variants, Geist and Abomination have blue emissive eyes with small
attached wisps (no particle entities). Romero's source death keeps its vertical
root movement; the carried projector does not lift the corpse. Slam radius is
8 blocks and triggers a short electric screen overlay, also on melee and
contact with electrified zombies. Thief rounds have a pulsing red screen tint;
base speed is .34 (was .42), flee navigation multiplier 1.15 (was 1.5), and the
default post-theft escape window is 900 ticks (was 240). Old default settings
migrate once; custom values remain editable in map configuration.

Follow-up validation: 67 server encounter checks and 73 native client checks
passed. In-game captures confirm the pulsing red round tint and electric HUD
arcs through the real GUI event. The updated Geist walk/death preview is
build/map-atmosphere/enemy-encounters/geist-source-motion.gif. Temporary
Romero reference exports were removed (319,997,453 bytes).

Second posture/balance pass: Ghoul locomotion now plants alternating feet with
an authored 6+6 two-bone leg rig. Death separation keeps both thighs together,
settles the lower half, grounds each half independently, and guarantees a 2.5
model-unit opening between visible waist/leg cubes. Skeleton melee playback
is 0.9x source duration (previously 1.35x). WoW zombie death arms settle beside
the body; the torso itself determines floor contact. Geist jump preparation
compresses the same four-contact rig used by its accepted locomotion; its
accepted death data remains unchanged. Thief red tint alpha ranges 42–74
(previously 18–30), and base/special-wave health is 350 rather than 600 with
the existing player/wave scaling retained.

Abomination hook preparation is 12 ticks (previously 16), updates aim until
three ticks before release, then remains a fixed, dodgeable trajectory at
1.9 blocks/tick (previously 1.3). Hit tolerance is 0.35 blocks. Requested
WoW sound kits 329390 and 75097 resolve to FileDataIDs 1042882 and 568346;
two mono Vorbis clips are read directly from the installed CASC and wired to
chain release and plague emission. No temporary unpack directory is needed.
The plague adds a depth-tested, wall-clipped plume capped at 24 textured quads
per caster, without increasing damage or spawning a new cloud of entities.

Validation: 71 server encounter checks, 73 full native-client checks and 10
visible-geometry checks passed. Native captures cover close-up walks, separated
corpses, jump preparation, stronger red tint and the actual plague plume.

Final compact-corpse/horde pass: upper/lower death offsets are now -2/+2 model
units before an exact 1.5-unit visible waist opening. Horizontal root movement
is bounded to 4 model units and upper-body support is measured on the torso,
not on a projecting hand. Geist walk aliases its running clip; its accepted
death data remains unchanged. Scourge locomotion chooses the original run clip
from actual movement attributes (>0.22) or super-sprinter state, so normal
sprinting no longer depends on the old limb-swing threshold. Shared normal
zombie speed scaling is preserved for every Scourge appearance.

The Abomination chain cross-section is 1.75x, the terminal hook 1.4x, and its
swept player-capture tolerance is 0.7 blocks (was 0.35). Astronauts and Romero
are independent of normal-round counters, completion checks and pending
redistribution budgets; their actual encounter/spawner lifecycle is retained.

Across all maps, legacy active-mob caps below 40 normalize to 50 and values
above 50 to 50. The menu accepts 40–50 per player; flood/chaos change spawning
cadence without exceeding that ceiling. Missing/dead tracked enemies release
their active slots on every server tick. Old Encounter Lab settings get a
one-time migration enabling sprint/super-sprint and a 50-enemy cap before the
match configuration snapshot. The lab generator uses the same corrected cap.

Native client validation: 76 checks passed, including actual sprint selection
for all three Scourge archetypes, with close corpse/motion captures. Visible
geometry validation: 10 checks passed, including bounded torso travel and
independent torso/leg floor contact.

Final server validation: 85 encounter checks passed, including a real round 32 reaching fifty living active enemies after injecting fifty stale tracking entries.

Ghoul/Geist locomotion readability pass: Ghoul chest detail is retained with
a narrower connected waist and a 1.5-unit higher torso anchor exposing the
upper thighs. Running foot trajectories now use the asymmetric original WoW
run positions, retargeted through two-bone endpoint constraints instead of a
symmetric sine cycle. At death the lower half settles away from the head along
the source torso's horizontal fall axis, with a 1.5-unit gap measured against
both torso and head. This avoids hiding the legs behind the head without
letting the torso travel farther.

Geist walking/running clips are no longer aliases. Running has wider strides
(3.8 versus 1.8 model units), higher foot/hand recovery and a small body bob.
Movement amplitude selects the gait with hysteresis; a continuous per-entity
phase and smoothed cadence avoid restarting the motion at speed changes.
The accepted Geist death data is unchanged. Astronaut random names now use
80 fictional Xbox 360/PS3-era gamer tags; existing persistent names and the
online player-name pool retain their prior behavior.

Validation before final Ghoul directional adjustment: 85 server checks and
79 native client checks passed, including Geist slow/fast gait selection and
at least 1.5x cadence change. Visible geometry checks pass after the adjustment.
No temporary game extraction/export directory was created.

Final Ghoul adjustment validated by 60 native posture/motion checks and close angled captures: both corpse halves remain visible. Console alias pool verified at 80 names; changed Java/resource mirrors agree.

Abomination names remain available internally, but profile visibility and both
native renderer name predicates suppress their overhead nametags, even while
targeted. Astronaut aliases now include all 233 unique user-supplied gamer tags;
existing persistent names are retained.

A real Thief death before its first theft grants every connected player one
Sale ingot. The historical stoleWeapon flag is independent of the current stolen
item list, so returning equipment cannot restore clean-kill eligibility. Both
the theft flag and reward-granted flag survive serialization. Repeated death
callbacks cannot duplicate the team reward; escape/despawn/reset grant nothing.
Full inventories use the existing owned-drop fallback. Downed/dead/spectating
players queue the item until their playable inventory has been restored;
queued rewards are cleared at match start/end. Lua receives OnThiefCleanKill
(thief UUID, connected player count) once on the successful clean kill.

Validation: 94 server encounter checks passed, including two-player clean-kill grants, no reward after theft/restitution, repeated-death and reload idempotence, full-inventory owned drops, deferred spectator delivery, and Abomination nametag visibility.

## 2026-10-19 — Geist wall routes, corpses and Cerberus

CrawlerLeap.isCrawler centralizes crawler-family eligibility for vanilla crawlers and Geists, including profile-based Geists. Crawler wall route selection, nearby-wall approach and anchor acceptance use it. Traversal refreshes Geist wall dimensions on enter/leave and resets leap state before taking the route. Ordinary zombies remain excluded from crawler-only wall links. Geists do not inherit the permanent boss traversal history restriction.

Geist deaths now retain the normal lingering corpse, following the existing reduced-gore setting and corpse lifetime, instead of discarding at 60 ticks. Ground-mode gas is still released at tick 45 without deleting the corpse; the existing source death animation remains unchanged. The feeding guard runs before corpse settling/gas, and accepting a Geist meal cancels pending gas. Soul objectives now accept Geists alongside zombies and crawlers.

Blocky Scourge rendering now uses Cerberus jaw rotation, current jaw offset and the same tooth-plane clipping as vanilla bodies. During feeding it uses an intact idle pose instead of the dismemberment/death track, and anchors the centre of the actual model head at the moving mouth before advancing the swallow. Each model retains its scale, skin and detailed source animations outside feeding. Directly registered Geists apply jaw interpolation locally; Scourge zombies reuse the outer zombie renderer offset.

Validation: 104 isolated Forge encounter checks passed, including an actual completed wall descent, normal physics restoration, retained dead Geist beyond three seconds, real Cerberus acceptance, survival through repeated bite/death ticks, and exactly one soul on consumption. Native client fixture renders all four Scourge feeding sequences and passes 13 lifecycle/render checks.
