# Outnumbered: survival before normal rounds

Outnumbered is a continuous-pressure phase. It can open the match or suspend an existing round during a visit to another area.

## Start in Outnumbered

1. Open **Map Config → Rounds → Outnumbered**.
2. Enable **Start match in Outnumbered**.
3. Set spawner channels, for example **0**.
4. Configure intensity timing/sprint threshold and apply.
5. Ensure participating spawners are enabled, powered if required, and in valid zones.

Once sprint intensity is reached, health scales through virtual rounds. These do not advance the visible normal-round counter. Hellhounds require active Hellhound spawners in those channels; zombie spawners do not replace them.

## Leave through a teleporter

On Earth → Moon:

- Configure destination/network normally.
- Set **Unlock after match start (seconds)**: **60** for a minute, **0** for immediate access.
- Enable **Finish Outnumbered**.
- Enable Moon channel **1**, disable Earth channel **0**.
- Optionally select Moon's arrival atmosphere.

After travel finishes, Outnumbered enemies are cleared and normal rounds start, or the previously suspended round resumes.

For Moon → Earth, use **Enter Outnumbered on arrival** and reverse channels. The phase transition affects the entire match, so plan group travel in multiplayer.

Enabling both enter/finish options toggles according to the current mode. Use only the intended action for predictable one-way behavior.

**Test the loop:** start fresh, leave the opening, complete a normal round, return to Outnumbered, then leave again. Check suspended-round resumption, channels and Game Over reset.

[Teleporter setup](09-Teleporters-and-Traversal.md) · [All guides](INDEX.md)
