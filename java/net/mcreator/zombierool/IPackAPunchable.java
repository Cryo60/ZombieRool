package net.mcreator.zombierool.api;

import net.minecraft.world.item.ItemStack;

public interface IPackAPunchable {
    /**
     * Applique l'amélioration Pack-a-Punch à l'item donné.
     * @param stack L'item à améliorer.
     */
    void applyPackAPunch(ItemStack stack);

    /**
     * Vérifie si l'item est déjà amélioré.
     * @param stack L'item à vérifier.
     * @return true si amélioré, false sinon.
     */
    boolean isPackAPunched(ItemStack stack);

    default boolean canBePackAPunched(ItemStack stack) {
	    return !isPackAPunched(stack);
	}
}
