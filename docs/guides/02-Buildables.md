# Buildables and workbenches

Open **Map Config → Crafting & delivery** to manage map recipes and sky deliveries. You can also open a workbench directly.

## Add a preset

1. Place a **Workbench** and right-click it in Creative.
2. Open **Preset Library** and choose **Zombie Shield**, **Turbine**, **Sentry Turret**, or **Pack-a-Punch**.
3. The template becomes an editable map recipe. Obtain configured component supports from its editor and place them around the map.
4. Add alternative positions if wanted, save, then test a match.

Presets are templates, not recipes automatically enabled on every map. Component items are visible in the creative **Components** category.

## Make your own recipe

1. Create a new buildable in the workbench editor and name it.
2. Choose its result from your inventory or the item list. Inventory selection preserves custom item/weapon data.
3. Choose **Add a part**, select its visual item, and place the configured support provided by the editor.
4. Repeat for each part. Use a part's entry to obtain additional supports for alternative locations.
5. Set the total copies directly if rewards should be limited, then save.

Workbenches are unrestricted. The first installed component determines the recipe being assembled there; finish it at that bench. Deleting a recipe also removes its components and placement markers.

## During a match

Look directly at a component and press **F** at close range. You carry one component at a time; taking another swaps them at that location. Install parts at a workbench, then claim the completed result. Tab shows progress.

## Install a fixed machine

Choose a block result and use **Get placement marker** in its recipe. Place that marker at the intended destination. Build the result, collect it, then hold F at a compatible marker. Releasing F or moving away cancels installation without consuming it. Match reset restores the marker.

This differs from a portable turbine/turret, which uses a free placement preview.

**Decoy Component** is decorative. Right-click with an item in Creative to set its appearance; Shift + right-click clears it. It never contributes to a recipe.

[Portable equipment](03-Portable-Equipment-and-Locks.md) · [Addons](12-Lua-and-Addons.md) · [All guides](INDEX.md)

## Pack-a-Punch parts

The Pack-a-Punch preset uses three components: the machine body with its frame and control panel assembled together, the gears, and a separate sign. Place their component supports around the map, then configure a placement marker for the completed machine. Add the preset to the map explicitly; it is not enabled automatically. Test power, installation and match reset together.
