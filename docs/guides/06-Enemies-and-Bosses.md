# Enemies, bosses and special rounds

Open **Map Config → Enemies/Bosses**. Place the appropriate spawners and check zone, channels and power. Map-wide encounter settings control boss selection and schedule; extra spawner blocks do not automatically mean extra encounters.

## Special rounds

Choose **Hellhound** or **Pentagon Thief**. They cannot coexist on the same map. Thief rounds use Hellhound spawners, falling back to normal zombie spawners.

The Thief steals the held weapon and then escapes. It does not steal twice from the same victim. Killing it returns stolen weapons with their item/upgrade data. Killing it before any theft gives every player an **Ingot Sale**. Its round uses a red alarm presentation rather than Hellhound fog.

## Bosses

- **Panzer:** armor, grabbing claw and flames. Aim for weak points and interrupt captures.
- **Brutus:** heavy attacks and machine-locking pressure. Warden Key drop enabled by default.
- **George Romero:** slow when calm, enraged when provoked. Water calms him. Slams electrify nearby zombies; roars can accelerate them. Returns after the configured delay following defeat.
- **Astronaut:** slow pursuit, grab and headbutt. Teleports use safely reachable space; no safe destination means no teleport. Randomized console-era-style names. Does not count toward the normal wave quota.
- **Abomination:** chain hook and plague. Solid geometry can stop the hook; exposed players face damage over time.

Configure timing, balance and loot in the boss settings. First-kill and recurring rewards are separate. Choosing an inventory item preserves its data. Behaviors are adapted to Minecraft collision/navigation.

## Scourge hordes

Choose **Scourge** in mob appearance. Ghouls, Zombies and Skeletons mix in one horde, with multiple skins. Their speed follows round scaling. They are not three mutually exclusive map presets.

## Crawlers and Geist

Appearance and behavior are independent. Choose Nova or Geist, then:

- **Ground + gas:** ground movement, faint trail and configured death-gas behavior.
- **Leaper:** prepared directional jump that can be dodged; no death gas.

Add Crawler Wall Links for wall descents. Test special-round completion, boss loot, narrow routes, stolen-weapon recovery and Cerberus feeding with the chosen appearances.

## Map-owned enemies and factions

Use the [Map Entity Workshop](16-Map-Entity-Workshop.md) for custom profiles, Enemy Spawners, patrols, independent pursuit leashes and faction combat. These placed residents are separate from ordinary wave enemies unless wave quota is enabled. Stratholme's Risen, Rivendare, Balnazzar and Crypt Fiend are map content, not characters automatically present in every world.

The Nuke kills tracked wave enemies. Map-owned residents, hidden encounter enemies and bosses remain alive; the team still receives 400 points per player. Test your objectives with this distinction.

[Warden Key](03-Portable-Equipment-and-Locks.md) · [All guides](INDEX.md)


## Crawlers and crowded rounds

Ordinary zombies that have lost their legs can attack Defense Walls and climb stairs. Test the actual stair route and wall placement during a match, including a crawler approaching from below. Navigation work is shared across the horde to reduce movement stalls in crowded rounds; unusually complex paths can still affect performance.

Gore pieces settle after landing and recover or disappear if trapped inside blocks. Reduce gore in the client settings if combat effects become too heavy for your machine.
