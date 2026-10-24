# Map files, migration and sharing

Authoring content now uses one `zombierool/` root inside the world save.

```text
<world>/zombierool/
  config/settings.json
  gameplay/entities/           Enemy profiles, models and animations
  gameplay/weapons/            Overrides for existing weapons
  gameplay/weapon_packs/<id>/  Themed arsenal definitions and assets
  assets/audio/                Music, voices, effects and imported sounds
  assets/textures/             Entity skins, chalks/ and skyboxes/
  assets/blocks/               Custom texture-block resources
  assets/particles/            Custom particle presets
  scripts/                    main.lua and other Lua files
  docs/                       Your map's documentation
```

The existing `zombierool/placements/` addon placement workflow remains separate. Datapacks stay in the world's normal `datapacks/` directory. Server Lua runs on the server; clients receive the assets they need for presentation.

## Existing maps

The engine can read previous layouts. Before reading resources and scripts, the migration copies each legacy source into a separate backup, verifies SHA-256 hashes, verifies the canonical destination, then removes the old copy. Backups are under `<saves>/.zombierool-backups/<world>/layout-<timestamp>/` beside the saves. Different contents at the same destination stop migration and log the conflict.

Examples: `zombierool/config.json` becomes `config/settings.json`; `zombierool/entities/` becomes `gameplay/entities/`; `zombierool/audio/` becomes `assets/audio/`; `zombierool/skins/` becomes `assets/textures/`; `zr_scripts/` becomes `zombierool/scripts/`.

The loader still reads legacy scripts. If both locations contain the same filename, the canonical script takes precedence and the callback is not loaded twice. `main.lua` loads first, followed by the other names in order. Editor backups use `zombierool/scripts/.history/`.

Minecraft region files, `level.dat` and player data are outside this layout migration. Keep a complete save backup before upgrading and check the log if migration reports a conflict. Resolve conflicting authoring files deliberately rather than deleting an entire legacy folder.

## Share and verify

1. Save the world and end the match. Include its complete `zombierool/` folder and required datapacks.
2. Declare the ZombieRool version, required addon mods and compatible gun packs. Sharing map assets does not install Java addons.
3. Test a copy in a separate instance, ideally on another client. Check entity profiles, music, imported skies, textures, weapon packs and scripts.
4. Start/end a match, reopen the world and confirm reset behavior. Avoid packaging personal tokens, launcher credentials or machine-specific paths.

[Lua](12-Lua-and-Addons.md) · [Weapon packs](18-Map-Weapon-Packs-and-Armor.md) · [All guides](INDEX.md)
