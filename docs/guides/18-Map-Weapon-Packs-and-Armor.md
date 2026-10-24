# Map weapon packs and armor

A themed arsenal belongs to the map that enables it. Stratholme supplies the example medieval arsenal; these weapons are not automatically offered in every world.

## Package an arsenal

Use `<world>/zombierool/gameplay/weapon_packs/<pack-id>/`. `pack.json` enables the pack and configures its armor protection and purchase/repair prices. `weapons/` stores weapon balance and animation channels. `assets/zombierool/` follows the Minecraft resource layout for models, textures, sounds and translations.

The server synchronizes map assets. Clients decode them directly, without a full resource-pack reload, and discard them when leaving. Keep the complete pack inside the shared save. Existing item identities remain registered so saved weapon stacks and wall purchases can resolve.

For balance changes to an existing weapon, use individual JSON overrides in `zombierool/gameplay/weapons/` instead. Unknown weapon IDs in that override folder do not create a new weapon.

## Play with Stratholme's arsenal

The pack provides 24 ordinary weapons: bows, crossbows, powder firearms, daggers, swords, an axe, a halberd, sword-and-shield, and Fire/Shadow Wands. They use normal weapon slots, Pack-a-Punch and applicable perks.

- Hold use to draw a bow and release to fire. Very short taps do not consume arrows.
- Crossbows and powder firearms use the ordinary fire/reload controls. The blunderbuss fires pellets; powder guns produce smoke and deliberate reloads.
- Melee uses repairable durability. Light attacks alternate cuts and end in a third-hit finisher. With an empty offhand, hold right-click for at least 16 ticks and release for a heavy strike: 1.8× damage at a cost of 3 durability.
- Daggers reward attacks from behind; greatswords have extra reach and a sweeping hit. The Scarlet axe temporarily reduces armor; the halberd has extra reach.
- Scarlet Sword and Shield uses one weapon slot, one durability pool and one PAP upgrade. Selecting it equips its guard in the offhand; switching restores the earlier offhand item. Left-click attacks and right-click blocks. It has no shield dash.

Heat and durability weapons show the corresponding HUD status instead of conventional magazine/reserve counters. Wall weapon drawings use the actual 3D model for these weapons.

## Armor stations

With the pack enabled, place a Gambeson, Lordaeron Chainmail or Lordaeron Plate station from Machines. Armor pieces are in Map Weapons. Press the purchase key (F by default), or right-click during a match, to buy/equip a set; use the same station with a matching damaged set to repair it. Intact armor is not charged again.

The Stratholme defaults are:

- **Gambeson:** 20% protection; 500 points to buy, 150 to repair.
- **Lordaeron Chainmail:** 35%; 1,500 to buy, 300 to repair.
- **Lordaeron Plate:** 50%; 3,000 to buy, 500 to repair.

Protection applies after ZombieRool normalizes enemy damage. Each intact piece contributes one quarter of the complete set's protection. Forced kills and environmental hazards remain outside this protection. Map pack configuration can change prices and protection.

`buyMapArmor(playerUUID, set)` returns `equipped`, `repaired`, `full`, `funds` or `unavailable`. `equipMapArmor(playerUUID, set)` equips a scripted reward without charging. Set IDs are `gambeson`, `chainmail` and `plate`.

Test the pack on both clients, including PAP, perk interactions, ammunition/durability, armor repair, swapping from the shield kit, death and match reset. Verify that another world does not retain this world's assets.

[Map files](19-Map-Files-and-Migration.md) · [Combat](15-Wonder-Weapons-and-Combat.md) · [All guides](INDEX.md)
