# Stratholme ambushes

The map-owned `stratholme_scourge_ambush.lua` starts hidden encounters for all active survival/adventure players on game start. No additional music volume or encounter marker is required. Spawn positions still require loaded map path blocks, solid support, room and visibility checks supplied by the engine.

- First group after 4–8 seconds; subsequent groups of 2–4 every 6–12 seconds. Failed placements retry after 4–8 seconds.
- Hidden position searches cover 6–24 blocks for Scourge and 6–18 for Risen. The old Risen 4–8 block ring had no valid hidden position in parts of the actual map.
- Weighted pool: two zombie entries, two ghoul entries, one skeleton and one Geist. The four `scourge_ambush_*` entity profiles belong to the map, use the Scourge faction and wave health, and do not count toward wave completion.
- Maximum 4 living ambush enemies per player and 16 across all players, including surviving enemies owned by disconnected/dead players.
- The player's active Risen music toggle suspends their Scourge appearances. Both scripts discover map music zones using the `damned` preset; moving or replacing the zone no longer breaks the encounter. The explicit `stratholme_risen` ID and the legacy `music_74_82_-46` ID also work. Leaving the volume alone does not resume Scourge. Restoring the previous music resumes appearances after 6–12 seconds; existing Scourge remain alive.
- Downed/dead players receive no new enemies. Engine rules enforce pause, game mode, dimension and safe placement. New matches reset the scheduler and rosters.

Timing uses server ticks, sampled every 40 ticks. Configuration constants and profile weights are at the top of the script.

Hidden spawn route validation has a separate allowance of two path searches per tick (6 ms), so ordinary enemy pursuit cannot consume all of its pathfinding allowance. Safe placement, visibility and reachability checks remain required.
Both Lua schedulers register their next retry before calling native pathfinding, so a slow first search or a transient callback failure cannot permanently stop appearances.

Spawner patrol radius applies to local guards as well as melee/wandering enemies. Radius zero keeps a stationary sentry. Patrol candidates use supported ground around the enemy, including height changes, and require a reachable navigation path within the spawn tether/leash.

Standalone checks (from the repository root, with Java 17):

```powershell
javac -cp libs/luaj-jse-3.0.1.jar -d tools/local-test/scourge-lua tools/tests/MapAmbushLuaTest.java
java -cp 'tools/local-test/scourge-lua;libs/luaj-jse-3.0.1.jar' MapAmbushLuaTest examples/stratholme/zombierool/scripts/stratholme_scourge_ambush.lua tools/tests/scourge_ambush_test.lua
```

The personal Risen encounter is capped at four living enemies, with 15�30 seconds between successful appearances. `stratholme_balance.lua` retains all authored spawner placements and wall weapons, selects normal wave intensity, delays the first sprinters to wave ten (15% chance), and spaces recurring round bosses from wave six every four rounds.
