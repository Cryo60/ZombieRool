package net.mcreator.zombierool.api;

import net.minecraft.world.item.ItemStack;

public interface IOverheatable {
    /**
     * Gets the current overheat level of the item stack.
     * @param stack The item stack.
     * @return The current overheat level.
     */
    int getOverheat(ItemStack stack);

    /**
     * Sets the current overheat level of the item stack.
     * @param stack The item stack.
     * @param overheat The new overheat level.
     */
    void setOverheat(ItemStack stack, int overheat);

    /**
     * Gets the maximum overheat level for this item.
     * @return The maximum overheat level.
     */
    int getMaxOverheat();
}