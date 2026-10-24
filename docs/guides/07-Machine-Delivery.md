# Perk roulettes and sky deliveries

Both use **Landing Site** in **Machines & Traps**. Place it on the floor like a carpet at the machine's future base. The supporting block remains available for redstone/power. Leave space for the full machine.

## Door-triggered roulette

1. Place a Buyable Door; assign a door channel, for example **7**.
2. Put a Landing Site behind it and open its Creative menu.
3. Select **Floating Roulette**, then **Door channel**, and enter **7**.
4. Select possible perks/prices and adjust float height/duration.
5. Save, start a match and buy the door.

Models cycle above the floor before the final perk settles. The result remains for the match. Multiple sites can use the same door channel; coordinate entry is unnecessary.

## Falling machines every X rounds

1. Place several Landing Sites; set their receiving mode to **Sky Delivery**.
2. Open **Map Config → Crafting & delivery → Sky Delivery** and enable it.
3. Set first round and interval: **3**, interval **4**, gives **3, 7, 11, 15…**. Special rounds count.
4. Choose perks and optionally **Pack-a-Punch**, plus prices.
5. Configure height and fall duration; the default descent is **10 seconds**. Save and test.

The machine and a free site are chosen before the descent, so no roulette is visible. No group or controller block is needed. Door-roulette sites are excluded. No-repeats avoids machines already delivered/reserved by the same system.

The descent has an alarm, positional falling sounds, flames and impact. It does not ignite the map. Landing space is reserved to prevent players staying inside the finished machine.

## Power and reset

Delivered machines follow normal power/purchase rules. Delivery does not enable global power. Match reset removes machines and restores configured Landing Sites, preserving supporting power blocks.

Use **Test delivery** while editing, then its reset controls to clear the preview. If landing fails, check headroom, solid obstructions, free sites and eligible machines.

[All guides](INDEX.md)
