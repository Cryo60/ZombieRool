# Traps, Soul Chests and Cerberus

## Linked trap

1. Place a **Trap Controller** at the purchase point.
2. Place Electric Trap or Fire Pit emitters with the same channel.
3. Set controller price, duration, cooldown and power requirement.
4. Orient emitters into the intended danger area and leave a safe route.
5. Start a match, supply power and buy activation with F.

A stationary Turret can run independently or through a controller. It differs from the portable workbench Sentry Turret.

Local power uses redstone/compatible devices; Global requires the map switch; Both requires both. A turbine cannot override Global. Test trap damage and point ownership. Decorative DCA/particle emitters are separate from damaging traps.

## Soul Chest

Place it from **Objectives & Rewards** and configure in Creative. Assign a unique objective ID, required souls, collection range and the available completion settings. Start a match and kill within range. Progress counts when the soul arrives, not on any kill anywhere on the map.

## Cerberus

Assign a unique ID and set required meals/feeding settings. Leave clear space around the mouth and kill eligible enemies nearby. Cerberus attracts and eats accepted corpses; it is not simply a Soul Chest with a different appearance.

A bursting gas crawler differs from a creature leaving a usable corpse. Test the chosen crawler behavior and enemy appearances with the feeding setup.

## Combine objectives

Use the Soul Completion Checker for the required objective IDs. Keep IDs unique. For a scripted exit, create a disabled teleporter with ID `exit`, then:

```lua
function OnSoulObjectiveCompleted(id)
    if ZombieroolAPI:areSoulObjectivesComplete("S1,C1,C2") then
        ZombieroolAPI:configureTeleporter("exit", {enabled=true})
    end
end
```

Create actual objectives S1, C1 and C2 first. End and restart a match to verify progression, rewards and temporary state reset.

[Lua](12-Lua-and-Addons.md) · [All guides](INDEX.md)
