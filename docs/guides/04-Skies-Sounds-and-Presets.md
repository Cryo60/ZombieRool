# Skies, sounds and atmosphere presets

Open **Map Config → Atmosphere**. Use Presets, Image and Audio/Ambiences. **Done** saves; Cancel discards setting changes.

## Choose or create a preset

Select and apply a preset. Included: Nuketown Zombies, Moon, Nacht der Untoten, Die Rise, Origins, Revelations, The Giant, Shangri-La, Blizzard and Zenith.

**New preset** starts a named blank atmosphere. Editing an official preset creates a custom copy. Editing that custom preset updates the same copy. Official presets cannot be deleted; custom deletion takes effect on save. Libraries belong to the map/dimension.

## Import a sky

1. Open **Image → Import skybox** and select a PNG.
2. Supported layouts: **2:1 panorama**, **3:2 cubemap atlas**, **4:3 horizontal cube cross**. Maximum 4096 pixels per dimension and 8 MB.
3. Adjust orientation and image sliders, then save.

A 4096×3072 cube cross is supported. Arbitrary image ratios are not sky layouts. Files are copied into the world's `zombierool/` resources; cancelling settings keeps the imported file in the resource library.

Map fog hides the custom sky while active. Image grading and sounds remain active. Hellhound white fog is temporary and normal presentation returns afterward.

## Add sounds

1. Open **Audio/Ambiences → Add sound**. Search the list; Listen previews a sound.
2. For your own file, choose **Import sound**: Ogg Vorbis `.ogg`, up to 15 MB. Use mono for positional audio.
3. Choose global or local playback. Use the position button while standing at the desired location; adjust exact coordinates if needed.
4. Set range and volume. Start quietly, around **0.1–0.2**.
5. Choose a continuous loop or random minimum/maximum intervals. Set fades and optional wall attenuation, then save.

For thunder, set **Flash** and sound propagation speed on the source. The flash starts immediately; distant players hear the sound later. Minecraft's ambient volume also affects playback.

## Sky objects

Open **Image → Sky objects and effects**. Add a built-in effect or import a supported model JSON. Select the object and adjust direction, elevation, size, distance, animation and opacity. Save both the object editor and atmosphere editor.

**Custom moon:** add a **Moon** sky object. Adjust yaw and elevation to place it, size and distance to set its apparent scale, brightness (0–5), phase (0–7), and RGB tint. It follows the active atmosphere preset and is available through the same Lua sky-object API.

Objects belong to the preset: switching to a preset without them removes them from that presentation. They are decorative background objects; use world scenery for solid structures.

## Local playlists

Use a Music Volume for per-player room-entry music. Entering toggles its playlist; leaving does not restore the previous selection. Add tracks under `zombierool/assets/audio/music/<preset>/`. See the [Music Volume guide](17-Music-Volumes-and-Hidden-Encounters.md) for priority, fades, Creative preview and encounter hooks.

[Travel presets](09-Teleporters-and-Traversal.md) · [All guides](INDEX.md)


## Sound during play and pause

The `zombie_ambient` music can continue while the pause menu is open. The Whisper's looping sound follows its player in the world, and Wunderfizz audio comes from the machine's location. Distance and direction affect these world sounds. Power-up loops are quieter so announcements remain easier to hear.
