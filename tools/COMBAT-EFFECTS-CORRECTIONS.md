# Combat effect corrections

The TacZ upgraded SCAR-H chain effect previously sent each arc both to TRACKING_CHUNK and directly to the shooter. Tracking already included the shooter, so the client could receive each arc twice. Arc particles also scaled linearly with distance, and the target-selection list was sorted on every hop.

The chain packet now has one nearby-recipient broadcast per arc. SCAR-H chaining is bounded to 8 targets (ordinary configuration remains unchanged), and nearest selection is linear rather than sorted. Electric visuals retain the same colors/endpoint sparks while limiting each arc to 36 segments and 6 endpoint sparks. The Wunderwaffe's target limit remains 24.

EXPLOSION now combines one brief flash, 10 flame fragments, 8 short sparks, 16 rising smoke particles and 10 expanding ground dust particles. This shared effect covers frag, stick grenade and monkey bomb detonations, including custom projectiles using EXPLOSION. Molotov adds a separate 22-particle ignition burst, preserving its existing fire patches and damage. No additional terrain destruction or damage is introduced.

Validation: current-source Forge compile. A real client without shaders equips an upgraded TacZ SCAR-H, invokes 40 EntityHurtByGunEvent.Pre impacts via the actual Forge bus and applies their resulting damage to a registered zombie. It renders the received electrical effects alongside generated blood/gore. After warm-up: max event-handler time 1.48 ms; 453 measured frames, p95 19.68 ms and max 32.42 ms. This fixture validates the event/effects path, not every aspect of the user's full live map or the complete ballistics input loop. Grenade smoke/flash and Molotov screenshots are recorded by CombatEffectsVisualTest.

Vehicle revision validation: model collisions, rays beyond the anchor, rotation/scale transforms, placement yaw, valid entity navigation obstacles and saved settings are covered by the equipment fixture. Photon visual fixture passed 103 checks for state/era/model combinations, menu persistence and yaw/pitch/roll sliders. All temporary clients are disposed by their runners; no new game extraction directories were created for this revision.

## 2026-10-19 — fixed trajectories, aircraft combat and F1

DCA tracer launch angles are captured once per shot in a weakly keyed, expiring device cache. Previously every live tracer used the current gun transform, bending the entire salvo while tracking. The new renderer cancels the current transform and applies each saved launch transform. Tracers keep their original spread, velocity, height range and fade.

Spawner aircraft follow paired pursuit lanes with climbs/dives and overtaking. They select forward opponents within 100 blocks, including aircraft belonging to other active generators, and fire intermittent bursts with sparse impact smoke. Each projectile stores its launch origin and intercept point once; target searches happen at burst cadence rather than for every live tracer on every frame. These remain decorative effects, without new projectile entities or gameplay damage. Airships retain their straight flight.

SCAR-H now has its own SCAR_CHAIN VFX packet type, in both native and TacZ paths. Each arc is capped at 11 tiny dust particles lasting 3 ticks and two impact sparks lasting 4 ticks. A 1.5-block exclusion around the camera keeps effects out of the sight. Wunderwaffe effects and weapon damage/chaining are unchanged.

The F1 fallback now runs world scenery effects after atmosphere compositing, preserving copied world/hand depth and shader state. Tornadoes, DCA streaks and aircraft fire no longer depend on the GUI render event.

Validation: current-source compile; 36 real-client Photon scenery checks including F1, opponent acquisition and the camera exclusion zone; four no-shader TacZ combat checks with 40 upgraded SCAR-H hits. Measured impact-handler maximum 0.457 ms; client frame p95 10.84 ms, maximum 16.97 ms in this isolated fixture. These timings are fixture measurements, not guarantees for a populated user map.
