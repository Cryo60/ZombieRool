# Map folder layout

All map-owned authoring content lives under `zombierool/`:

- `config/settings.json`: resource override switches and discovery counters.
- `gameplay/entities/`: entity models, skeletons, animations and behavior definitions.
- `gameplay/weapons/`: individual weapon balance overrides.
- `gameplay/weapon_packs/<id>/`: themed arsenals, including balance, models, textures, animation curves and sounds.
- `assets/audio/`: music presets, voices, effects and custom audio.
- `assets/textures/`: entity skins, `chalks/` and `skyboxes/`.
- `assets/blocks/`: custom block textures and their manifest.
- `assets/particles/`: custom particle presets.
- `scripts/`: Lua, including main.lua.
- `docs/`: map authoring guides.

The engine reads previous layouts for existing maps. Migration copies each file into a separate backup and verifies SHA-256 hashes before removing the old copy. Minecraft region files, level.dat and player data are outside this migration.

Map arsenal assets are decoded directly and do not reload Minecraft resource packs on join or disconnect.
