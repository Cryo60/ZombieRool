# Teleporters and enemy traversal

## Start here

Place each platform on a supported 3 × 3 area, leaving headroom and a clear arrival space. Right-click its centre in Creative to edit it. In a match, use the action key (F by default) or the automatic trigger configured for that source. Each platform needs a unique Platform ID in its dimension. Network Channel groups a transport network; Destination ID selects one particular platform. A spawner channel is a separate numeric system.

Configure both ends, save each one, and test a real match. Creative travel preview is useful for orientation and safe landing, but does not replace a match test of costs, conditions, group travel and round actions. These teleporters do not cross Minecraft dimensions: build the regions in the same dimension.

The pages are Route, Activation, Linking, Journey, Advanced, Spawner channels and Timing. Presets fill defaults; review those defaults before saving. In particular, several presets require power or linking, and some are automatic.

## Example 1 — one-way corridor

Place platforms A and B. On A choose **Direct**, role **Source**, ID `entrance`, Network Channel `corridor`, Destination ID `exit`. Set Require power OFF, Require link OFF, Automatic OFF, Cost 0 and Charge time 3 seconds.

On B choose Direct, role Receiver, ID `exit`, Network Channel `corridor`, Require power OFF. Keep both enabled. Start a match, stand on A and press F. B receives the players but cannot launch a return journey. Give B the Source role and Destination ID `entrance` if you want an interactive return route.

## Example 2 — Earth ↔ Moon with atmosphere and progression

Build two distinct regions in the same dimension.

Earth platform: Direct / Source, Platform ID `earth`, Destination ID `moon`, Network Channel `earth_moon`, Require power OFF, Require link OFF, Automatic OFF. Choose **Moon** under Journey → Arrival atmosphere preset. Under Spawner channels, Enable `1`, Disable `0`, Duration `0`, Redistribute enemies ON. In Advanced, Finish Outnumbered ON if leaving the opening survival region should begin numbered rounds. Under Timing, Unlock after match start can be `60` seconds.

Moon platform: Direct / Source, Platform ID `moon`, Destination ID `earth`, Network Channel `earth_moon`, power/link OFF. Arrival atmosphere: **Restore map atmosphere**. Enable `0`, Disable `1`, Duration `0`, Redistribute enemies ON. Enable Enter Outnumbered on arrival only if returning to Earth should enter that mode. Leave it OFF for an ordinary return.

Use normal zombie spawners in the Earth region on channel 0 and Moon spawners on channel 1. The destination should be built and navigable. Duration 0 keeps channel changes until another action or match reset. Atmosphere only changes the travellers' sky, sky objects and ambience. Configure vacuum and gravity separately using environmental zones.

On reciprocal Sources, put outward settings on the outward Source and return settings on the return Source. The source's arrival preset has priority; a Receiver can supply the fallback. **Keep current** preserves a player's override; **Restore map atmosphere** removes it. Those options have different uses.

## Example 3 — a paid route unlocked after a delay

Use the Direct route from Example 1. On the Source set Cost `500`, Unlock after match start `90`, Charge time `3`, Cooldown `20`, Automatic OFF. Keep Require power OFF for a time-only gate, or ON to require both the timer and global power. Give the destination a unique ID and matching network.

The unlock timer counts from match start, not world load. A countdown and cooldown are runtime state, not an edit to the saved settings. Validate with an actual match: before 90 seconds access is locked; afterwards one activation can pay for the eligible occupants selected by the transport rules. Keep the platform clear throughout charging.

## Example 4 — linked mainframe / Der Riese

Create a mainframe using role Receiver, ID `mainframe`, Network Channel `factory`. Add three Sources with IDs `lab_a`, `lab_b`, `lab_c`, all on `factory`, each Destination ID `mainframe`, preset **Der Riese**. Its defaults require linking and power, charge 1,500 points, and use three Pack-a-Punch links. Adjust the price or disable power deliberately for a smaller test.

On a Source, begin linking, then reach and confirm at the Receiver before Link timeout expires (default 30 seconds). Repeat for the other Sources. Return ID is optional; when blank the destination is used as the link receiver. If you set it, use the exact mainframe ID. A Receiver's F interaction confirms pending links. Merely matching the network does not complete linking.

Test one Source first, then add the others. A missing or duplicate Receiver ID, mismatched network, disabled power or expired timeout prevents confirmation.

## Example 5 — a timed upgrade room / Kino

Create Source `theatre`, Receiver `link_console`, and Receiver `upgrade_room`, all on network `theatre_route`. On `theatre` choose **Kino**, Destination ID `upgrade_room`, Return ID `link_console`, Require link ON, Relink ON, Stay `30`, Cooldown `60`. Keep secret destinations empty for the first test. Set power and cost to your preferred requirements.

Confirm linking at `link_console`, then activate the Source. The group travels to `upgrade_room`, stays for the configured period, and follows the return route. `link_console` is the return/mainframe location in this setup, so build it where the group should return. Relink requires a new link after use. Do not place another automatic Source at the receiving point: that can create an unwanted second departure.

After the basic room works, add **Secret IDs** using exact IDs of additional Receivers on the same network, Secret chance between `0` and `1`, and Secret stay (seconds). Begin with chance `0` while debugging and use `1` for a forced secret-room test.

## Example 6 — team platform / Moon

Choose **Moon** on the outward Source. Its defaults are Automatic ON, All players ON, Require power OFF, Cooldown 15 seconds. Set a unique Source ID, a Destination ID and network shared with the Receiver. Leave Require link OFF unless you explicitly need it.

All eligible living participants must gather on the platform. Creative/spectator test characters do not substitute for active survivors. For a manual team departure set Automatic OFF and use F once the group is assembled. Test with two clients: first leave one survivor outside, then gather both and confirm a single group journey. If only one player should travel, disable All players instead of trying to bypass the team check.

## Example 7 — DEFCON panic room

Use preset **DEFCON** on the Source, a unique Destination ID for the panic-room Receiver, and one shared network. Add platforms with role **Switch**, distinct IDs, and that network. Configure Required switches to match the number of switches you placed (default 4). Give the Switches suitable enabled/power settings.

Switch activation progresses the network's DEFCON condition. Keep the generated switch conditions, or use distinct condition names starting with `defcon`. Once the required count is reached, the Source selects the configured panic-room destination and applies its timed stay. Before that, DEFCON selects a random eligible destination on the network; the switch count alone does not lock the Source. Add an explicit condition if travel must be unavailable beforehand. DEFCON enables automatic travel by default. Start with one Switch and Required switches 1 to validate the layout, then expand to four. Use a proper return location and clear landing areas.

## Example 8 — condition-controlled rocket route

Preset **Rocket** starts with Cost 500 and Blocked condition `rocket_test`. Configure normal Source/Receiver IDs and network. The route is blocked while that named condition is true. An ordinary required **Condition** works the other way: it must be true to permit travel.

A map script can set the condition with `ZombieroolAPI:setTeleporterCondition("rocket_network", "rocket_test", true)` and clear it with `false`. Use the exact network string on the platform and in Lua. Configure cooldown and power independently. See the Lua guide for timers and safe event wrappers.

## Example 9 — temporary channel changes

On an ordinary Source, enable channel `2`, disable `0`, Duration `30`, Redistribute ON. This temporarily switches spawn pressure for 30 seconds, then restores prior channel state. Set Duration `0` if a later return trip should be responsible for switching it back. Place destination-region spawners before enabling redistribution; the setting does not create spawners or terrain.

## Outnumbered arrival actions

**Finish Outnumbered** ends the unnumbered survival phase and starts/resumes normal rounds. **Enter Outnumbered** suspends the numbered phase and enters survival. Both enabled together toggle according to the current phase. Neither enabled leaves progression unchanged. This is a match-wide action; atmosphere is per traveller. Test transitions and the end-of-match restoration, not just the visible travel effect.

## Troubleshooting

- No destination: check unique IDs, exact spelling, shared network and role. Avoid reusing one ID on multiple pads.
- Platform stays locked: inspect enabled, power, required condition, blocked condition, link state, unlock timer and cooldown separately.
- Travel starts automatically: review Automatic; Moon, DEFCON and Denizen presets enable it by default.
- A second trip starts immediately: check whether the arrival pad is an automatic reciprocal Source. Prefer an inactive Receiver unless a return Source is actually needed.
- Nobody moves: ensure the players are on the platform, alive, participating and not already committed to another journey. Check All players.
- Unsafe destination: remove solid blocks in the arrival body volume; keep the destination inside world bounds.
- Sky changes but breathing does not: atmosphere presets and physical environmental zones are separate settings.
- Preview works but a match fails: preview skips ordinary gameplay requirements. Test the saved power, links, price and round state.

## Enemy traversal

Jump Link and Zipline use Entry/Exit endpoints; enable Bidirectional if needed. Traversal Node adds an apex. Crawler Wall Link belongs at a roof edge with a valid wall below. One-way Gate restricts reverse movement. Leave supported, navigable terrain around all endpoints. Zipline player access is configured separately from enemy access.

[Lua guide](12-Lua-and-Addons.md) · [Outnumbered](10-Outnumbered.md) · [All guides](INDEX.md)
