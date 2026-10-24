# MCC atmosphere skies

Sources: the user's installed Halo: The Master Chief Collection. Game files are read only.

- **Blizzard**: Halo 3 Snowbound (`halo3/maps/snowbound.map`), sky model tag 11.
- **Zenith**: Halo 2 Anniversary multiplayer Ascension (`groundhog/maps/ca_ascension.map`), sky model tag 92.

The final panoramas are 2048 x 1024 PNGs in `assets/zombierool/textures/environment/halo_*_sky.png`.
They combine extracted sky geometry, textures and material constants offline: one regular panorama in Minecraft, no new scene entities or runtime ray tracing. Their ambience uses existing ZombieRool wind sounds; these are not extracted Halo recordings. The presets use the standard map config, Lua and teleporter atmosphere APIs and are protected as official presets.

## Reproduce

Use Reclaimer v2.1.1644 from https://github.com/Gravemind2401/Reclaimer/releases/tag/v2.1.1644.
`ExportHaloSky.cs` is a small .NET console entry point referencing Reclaimer.Blam and Reclaimer.Core (System.Drawing.Common is a DLL dependency). It exports only sky-tag textures/materials and the selected sky model, not a whole map dump.

```
extractor.exe "GAME/halo3/maps/snowbound.map" "TEMP/blizzard" 11 17
extractor.exe "GAME/groundhog/maps/ca_ascension.map" "TEMP/zenith" 92 100,101,114,118,125,126
python bake_sky.py TEMP/blizzard blizzard halo_blizzard_sky.png
python bake_sky.py TEMP/zenith zenith halo_zenith_sky.png
```

Baker dependencies: numpy, Pillow, trimesh, embreex, scipy. Use disposable dependencies/export folders outside the project; delete them after copying the final PNGs. Do not retain game dumps, SDKs or extracted tool DLLs here.

## Rendering limits

This is an offline conversion of the source materials, not the Halo rendering engine. Material color, procedural haze and emission are approximated; animated clouds/aurora become a fixed frame. Reclaimer cannot decode Zenith's Unknown30 light-volume glow texture; the extracted secondary flare is used instead. Open source meshes below the playable horizon are faded or extended to avoid hard borders when viewed from Minecraft. Blizzard's cloud mask must use its alpha channel, not its grayscale RGB. Bilinear sampling and ray intersections preserve the source UV/mesh placement without planar triangle projection seams.
