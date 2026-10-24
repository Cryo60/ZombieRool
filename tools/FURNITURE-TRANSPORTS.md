# Furniture, military scenery and buyable obstacles

Furniture is searchable in Decorative Accessories. Vehicles are in Ambience, with one item per family. English/French names are provided.

## Placement

- Complete Bed and Complete Hospital Bed place the entire bed from one item. They occupy two cells like a vanilla bed, reserve both cells and remove both when broken. Legacy half-bed blocks remain registered for existing maps but are hidden in the creative catalogue.
- IV Stand is nearly two blocks tall and reserves the cell above it. Placement refuses an occupied extension cell.
- Wooden Pallet and Trench Duckboard stack four layers per cell with a top-face click. The next pallet starts the next cell. Copies on Buyable Doors can also stack.
- Sofa Module and Sofa Corner connect to adjacent sofa modules, remove their shared end armrests and restore armrests when separated. This also works for ordinary furniture copies on adjacent Buyable Doors.
- Wall art, posters and wall lights face outward from the clicked wall. Ceiling lights and chandeliers attach to the top of their cell. Directional collision follows every model.
- Chandelier, Industrial Ceiling Light, Wall Light and Brazier emit block light.

## Furniture and props

Large Dumpster measures 3 blocks wide, 2 deep and 2.5 tall. Desk, Workbench, Factory Control Console and Hotel Dresser are two blocks wide. Lockers, filing cabinets, medical cabinets and display fridges are two blocks tall; industrial shelves span two blocks in width and height. Each places as one complete item, reserves its collision cells and removes as one object. The brazier uses animated vanilla fire surfaces plus restrained flame/smoke particles, instead of orange/yellow solid blocks.

The original living-room, office and factory collection now also includes waste disposal (Dumpster, Wheelie Bin, Wastebasket), hospital furniture, hotel furniture and reception, restaurant tables/chairs/booths/counters, Cash Register, Pizza Oven, Display Refrigerator, Chandelier, Industrial Ceiling Light, Ceiling Fan, Framed Painting, Safety Poster and Wall Light.

Military additions: Wooden Barrel, Steel Drum, Brazier, Military Equipment Crate, Field Radio, Weather Station, WWI Trench Revetment, Trench Duckboard, Artillery Ammunition, Bunker Ventilation Grille, Prison Bunk Module, Prison Sink and Toilet, Barbed Wire Barricade, Fortification Sandbags.

The existing Barbed Wire uses a new three-dimensional concertina model and keeps its slowdown/damage behavior. Fortification Sandbags is a separate model/item; existing sandbags are retained.

All furniture surfaces are baked from the exterior union of their textured cuboids. Internal/coincident faces are removed, including the previously overlapping armchair, bed, console and sofa surfaces. Standalone furniture uses plain block models and cached collision shapes, without entity ticks.

## Buyable Door / obstacle compatibility

In creative, right-click the lower corner of an obstacle area with a furniture or vehicle item. ONE whole model is copied at its natural scale. Only the rectangular footprint required by that model is reserved, starting at the clicked obstacle; neighboring obstacles outside that footprint are not changed. The full footprint must contain obstacle blocks; an insufficient area produces a dimensions message without changing anything. The maximum footprint is 4096 cells.

A group saves its exact member positions. Buying a member removes only those reserved cells and charges once; directly touching obstacles outside that visual group remain. The price/channel use the existing obstacle interface. Replacing an overlapping decoration clears that old visual's membership while retaining the physical obstacle blocks.

An untextured obstacle has full block selection/collision, with the existing creative mapper collision bypass. Ordinary one-cell furniture retains its copied collision shape. Group geometry uses saved model/transform data and a single render anchor, without extra decoration entities.

Existing wall-weapon furniture copies remain supported, including their silhouette attachment to a real model face.

## Vehicles

City Bus, School Bus, Coach, Box Truck, Pickup Truck, Tanker Truck, Delivery Van, Ambulance, Pave Low, Stryker, BTR-80, BMP-2, Humvee, Snowmobile and Zodiac. Each family has Normal, Broken, Burnt Out and Burning variants in the decoration menu. They use the existing rotation, pitch, roll, scale, activation and collision system. These are decorative vehicles.

Lua example: `configureAmbientDevice(x,y,z,{scenery={vehicle="pave_low",variant="burnt",yaw=45,size=1}})`. Other new family IDs: `stryker`, `btr80`, `bmp2`, `humvee`, `snowmobile`, `zodiac`. Shared resource keys: `scenery/vehicles/<family>_<normal|broken|burnt>.json`; burning reuses the burnt model with the existing bounded flame/sound system.

## Other corrections

Glass Defense Door: zombie hits occur every 30 ticks in the intact glass phase, compared with 60 ticks for the wooden phase. This doubles destruction speed while glass without weakening rebuilt planks.

Texture Kit heads: creative empty-hand right-click rotates clockwise; Shift + right-click rotates counterclockwise.

The generic 26.7 asset generator now preserves the authored zipline files instead of rewriting them on unrelated additions.

## Rebuild and validation

Run `tools/build-furniture-transports.py`, then `tools/tests/build-vehicle-collisions.py`. Sources and generated resources use the project's existing mirrors. No game-asset extraction or downloaded tooling is needed.

`tools/tests/render-map-atmosphere.py --furniture-revision` runs an isolated native client and checks furniture models, rotation/collision, inventory categories, saved copies, sofa connections, pallet stacking, complete bed/IV placement and removal, obstacle groups and real survival purchases, military vehicle variants and glass damage timing. Screenshots and logs are under `build/map-atmosphere/furniture-revision`. Its disposable client is automatically removed.

Validation on 2026-10-19: 879 native-client integration checks passed. Static validation checked 221 mirrored resource files and 5,254 furniture exterior faces with no coplanar overlap. Final source compilation passed.

Follow-up: 1,020 native furniture/vehicle checks passed, including exact dumpster dimensions, limited obstacle membership and purchases preserving adjacent obstacles, multi-cell furniture placement/removal, and duckboard stacking. `--furniture-followup` additionally checks vertical Geist limb motion and profiles match transitions with 32 vehicles. Match reset preserves passive vehicles and their cached geometry; start loadout is synchronized before teleport, and end teleport follows restoration. Slow transition timings are logged as `Match start` and `Match end`.

Transition regression: start announcements now apply in the existing network work queue, without an extra deferred client task. The stop packet clears round announcements, preventing a queued start from bringing the HUD back after match end. Native follow-up also asserts the client stays stopped after rapid start/end. On the initial 32-vehicle fixture, full match start measured 42 ms and full match end 4 ms; these are fixture timings, not timings from the user map.

Final follow-up passed all 12 checks, including client packet ordering and announcement cancellation. The second 32-vehicle run measured 34 ms start / 4 ms end. Four duckboards fill a full block without a gap. Existing saved decoration groups without explicit member lists are detached safely when replaced.

## Reinforced crates

Decorative Accessories includes Reinforced Wooden Crate (`furniture_reinforced_crate`) and Plague Crate (`furniture_plague_crate`). Both are one-cell closed crates with recessed wooden panels, a plank lid, metal edge straps and corner rivets. The plague variant adds stained seams and restrained green toxic-smoke emission from the lid. The gas is decorative and does not apply poison damage. Emission uses client display ticks and existing particles; there is no new entity or block-entity ticker. It also works when the crate is copied onto a Buyable Door. Furniture rotation, collision, saved mimic state and creative search use the existing furniture system.

## Tavern, forge and Scourge scenery

New searchable props: Burning Barrel; Tavern Trestle Table, Tavern Bench, Tavern High-back Chair; Wooden Weapon Rack; Grain Sacks and Supplies; Alchemy Worktable; Smithy Anvil; Forge Bellows; Scourge Gravestone, Scourge Runestone, Plague Cauldron, Scourge Bone Pile and Soul Lantern Pedestal. These are authored Minecraft-style assets inspired by fantasy/WoW set dressing, with no game extraction files. Tables, benches and alchemy tables are two blocks wide; the weapon rack is two blocks tall. Burning Barrel uses animated fire geometry, restrained flame/smoke particles and block light. The cauldron emits decorative toxic smoke. All use existing furniture placement, inventory, rotation and buyable-obstacle support.

Native filtered furniture fixture: 170 checks passed for this collection.

Nova gas-mode crawlers now emit one faint toxic-smoke puff behind their movement at most every 16 ticks, staggered by entity ID. Emission is suppressed while stationary, dead, emerging, paused, frozen, traversing a link, or in leaper mode. The trail is visual; existing death gas behavior is unchanged.

Impact rendering: blood keeps 64 x 64 pixels per block face but shares 1024 x 1024 atlas pages, avoiding a separate texture/render pass for every stained face. Dirty tiles upload only their 64 x 64 region; invisible faces are culled by the view frustum. Atlas slots are reused and native images are released on clear. A real-client MP5/Zenith test cycled ghoul, zombie and skeleton appearances over 40 impacts: 240 blood faces shared one page, maximum server hit time 2.47 ms, frame p95 13.98 ms and maximum 28.04 ms on the fixture without shaders. Temporary local profiling files were removed.

## Follow-up fixes: bone material and hit-time rasterization

Bone Pile now references the real vanilla `bone_block_side` texture; `bone_block` was a block ID, not a texture asset. All 608 vanilla texture references in generated furniture were checked against the Minecraft resource JAR.

Blood rasterization now computes immutable pixel arrays on one daemon worker with a bounded 64-job queue. The render thread uploads at most four completed 64 x 64 regions per frame, retaining existing tiles until replacements are ready. Stale jobs are discarded by signature, queued work is cancelled/purged on world clear, and OpenGL/native-image access remains on the render thread. The first atlas is allocated when entering the world, before the first hit. Nothing is removed from the blood coverage or its resolution.

The impact fixture now supports `ZR_IMPACT_KILLS=1`: fresh 4-HP Scourge targets are actually killed, with persistent corpses and fragments, 100 pre-existing blood pools, MP5 and Zenith. The first run passed 46 checks, with 40 actual deaths, frame p95 11.94 ms and maximum 18.16 ms after initial warmup. Bone textures were also checked on the loaded baked model. These are isolated fixture measurements; they do not establish that every remaining stall on the user map is resolved.

A second cold-impact run, including the first kill and the first frames of newly spawned Scourge appearances, passed all 46 checks: frame p95 11.63 ms, maximum 14.42 ms, server impact maximum 3.60 ms.


### Impact performance investigation (2026-10-19)

The live JFR profile exposed resource-file attribute queries from `MapAtmosphereClient.deferredAmbient`, reached once per flying blood particle. Cache sky resource availability by texture ID/resource manager and invalidate on resource reload/logout; imported world skies remain immediately visible. Check shader use before considering deferred blood. No particle count, blood resolution or gore reduction.

Combat fixture now supports `ZR_REAL_BULLETS=1` with actual TaCZ kinetic bullets and collision/death assertions, alternating ordinary and Scourge zombies under Zenith. With `ZR_SCOURGE_IMPACTS=1 ZR_IMPACT_KILLS=1`, 48 checks passed: 40 projectiles, frame p95 12.33 ms, max 21.50 ms; 10,000 cached sky lookups took 5.49 ms. This isolated result does not establish that every stall in the user world is resolved; the live profile also contained substantial chunk visibility/loading work at render distance 32.
