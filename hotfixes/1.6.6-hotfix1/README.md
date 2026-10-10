# ZombieRool 1.6.6-hotfix1

This release starts from source commit `f0bbfce905d244902ef3108478cc2993a95f4431` and the published Minecraft 1.20.1 / Forge 1.6.6 JAR. It excludes the teleporter, traversal system, and other pending 1.7 features.

## Changes

- Preserve Essential's title/pause menus and provide Open to LAN above Quit in the custom pause menu.
- Accelerate regeneration, retaining its original damage delay; clear down/crawl state for every player after a match or respawn.
- Select players through reachable routes instead of straight-line distance. Sandbags block navigation through their collision volume.
- Restore vanilla interactions and hopper collection. Weapon, melee, and TacZ raycasts pass through Restriction Blocks.
- Exclude locked player spawners from starting positions and register copied spawners immediately, preserving their inventory appearance and settings.
- Load all Lua scripts in a stable order with main.lua first, fresh globals/timers for every match, and support legacy manager class names.
- Default omitted Lua sound volume/pitch to 1, preserve supplied zero values, and resolve legacy dynamic paths to registered built-in sounds when available.
- Persist and restore the original editable world configuration across Game Over, Restart Level, and server restart.
- Support transparent Texture Kit shapes, humanized names, nested folders, drag/drop, immediate inventory grants, and the Head shape/skin import.
- Copy both halves of vanilla/Texture Kit doors into stacked mimic blocks. Correct Glass Defense Door facing, open UVs, and double-door outer hinges.

## Artifact and validation

The published artifact is the exact user-validated instance JAR. Its SHA-256 and exhaustive entry diff are recorded in `validation.json`.

188 integration assertions passed on an isolated Forge server. Bytecode analysis checked 842 methods, all 1024 glass-door model references resolve, and 64 intact render states match Minecraft's vanilla door geometry and front UV direction. The user confirmed the final client behavior before publication. All 446 release Java source files compile without development classes; 12 additional Lua binding checks preserve defaults, supplied volume/pitch, and other Java API methods.

`build_hotfix.py`, `Patch166.java`, `src/`, `replacement-src/`, and `resources_hotfix.py` record the surgical build of the shipped JAR. They require the published 1.6.6 JAR at `build/libs/zombierool-1.6.6-published.jar`, Java 17, resolved Forge dependencies (`build/verify-fixes/javac.args`), and official/SRG mappings. The builder enforces a fixed baseline hash and an explicit class/resource whitelist. Test classes are never shipped. Map scripts and world saves are not bundled.

The readable sources in the public `java/` tree also contain these fixes. Normal source builds use `LegacySoundBindings` to dispatch the four Lua sound methods deterministically; the shipped surgical artifact implements the same argument-count selection in its relocated LuaJ class. Textures/sounds remain excluded from the public source tree, as in previous releases; changed model/language JSON is described by `resources_hotfix.py`.
