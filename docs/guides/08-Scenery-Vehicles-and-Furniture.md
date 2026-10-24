# Ambient scenery, vehicles and furniture

## Anti-aircraft guns

1. Place decorative DCA from **Ambience**; right-click it in Creative.
2. Choose the model and power requirement. Local/redstone and global power follow the device's settings.
3. Use its world aiming tool, aim at a block or the sky, and right-click.
4. Choose Regular, Varied or Aircraft tracking fire. Varied alternates bursts, sustained fire and pauses independently per gun.
5. Adjust turn speed, intervals, tracer height and sound. Defaults: **30–40 blocks above the gun**, volume **0.2**.

Tracking chooses randomly among nearby active decorative aircraft, retains a target for a while and turns gradually. Tracers continue in their firing direction rather than following the target. DCA here is decorative and does not damage players.

## Aircraft and airships

Place an **Aircraft Generator**. Set count, altitude, spread, speed and sound. Aircraft start about **50 blocks above** and can visually fight one another. Engines/shots are positional; initial volume is **0.1**.

Choose Airship for a straight pass. Set heading, length, speed, altitude and loop. It continues into the distance before fading. Flying scenery has no physical vehicle entities or collision.

## Tornadoes

Place a **Tornado Generator**, choose normal/fire, and obtain its bound route tool from the menu. Right-click ground to add points; Shift + right-click removes the last. The menu can also add the player's position. Set speed, size and loop. Without points it stays above the generator. It damages neither players nor terrain.

Generators need loaded chunks; they do not force loading.

## Static vehicles

Choose cars, buses, trucks, armored vehicles, planes or helicopters. In Creative configuration, select model/state, then yaw, pitch, roll and scale. Use the angle sliders or ±5° buttons for adjustments; Face my direction and Reset angles provide quick starting points. Normal, Broken, Burnt Out and Burning are available where supported. Burnt Out has no flames.

Static vehicles have selection and movement collision. They are scenery rather than drivable vehicles. Flying ambient aircraft, by contrast, are non-colliding decoration.

## Furniture and Buyable Obstacles

Large furniture places as one object, reserves its footprint, and removes as a whole. Sofas connect; pallets and trench duckboards stack. Wall/ceiling pieces follow the mounting face.

Right-click a Buyable Obstacle area's lower corner with a furniture/vehicle item to assign that model. Only the required footprint is used. Provide enough obstacle cells; unrelated neighbors remain separate. One purchase opens the whole face-connected obstacle barrier, including adjacent furniture groups; the collision disappears with the purchased blocks.

### Unlock several channels with one purchase

Open the Buyable Obstacle editor in Creative and enter a comma-separated list in **Unlock channels**, for example `1,2,3`. One purchase opens the connected obstacle and unlocks every listed channel, charging the configured price once. Duplicate channels are ignored. Existing single-channel obstacles remain supported. The list is saved with the map and appears in the channel-link visualization.

## Wall barricade planks

**Wall Barricade Planks** is one decoration block with ten randomly selected arrangements. Place it against a wall or door; each placement keeps its chosen arrangement. Right-click the placed block in Creative to cycle through all ten variants.

## Additional scenery

The DCA model selector includes **Dwarven Bombard**: a short bronze cannon with a wooden carriage, runes and blue hammer-emblem plates. Its existing rotation, elevation, recoil and decorative firing settings still apply.

The Aircraft Generator includes **Necropolis**. Configure altitude, size, heading and speed; zero speed hovers. It renders in the world with fog and remains decorative, without aircraft weapons or an engine loop.

[Flags and standards](13-Flags-and-Standards.md) · [All guides](INDEX.md)
