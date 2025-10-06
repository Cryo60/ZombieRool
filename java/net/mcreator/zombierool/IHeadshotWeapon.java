// net.mcreator.zombierool.api/IHeadshotWeapon.java
package net.mcreator.zombierool.api;

import net.minecraft.world.item.ItemStack;

/**
 * Interface pour les armes personnalisées qui ont une logique de dégâts de headshot spécifique.
 */
public interface IHeadshotWeapon {

    /**
     * Renvoie les dégâts de base du headshot pour cette arme.
     * C'est le montant initial de dégâts infligés lors d'un headshot,
     * avant d'ajouter d'éventuels bonus (comme Pack-a-Punch).
     *
     * @param stack L'ItemStack de l'arme.
     * @return Le montant des dégâts de base du headshot.
     */
    float getHeadshotBaseDamage(ItemStack stack);

    /**
     * Renvoie le bonus de dégâts pour le headshot lorsque l'arme est améliorée (PaP).
     * Ce bonus est ajouté aux dégâts de base du headshot.
     *
     * @param stack L'ItemStack de l'arme.
     * @return Le bonus de dégâts de headshot pour l'arme PaP.
     */
    float getHeadshotPapBonusDamage(ItemStack stack);
    
    /**
     * Indique si cette arme supporte les headshots personnalisés.
     * Par défaut, on pourrait retourner vrai si elle implémente cette interface.
     *
     * @param stack L'ItemStack de l'arme.
     * @return true si l'arme a une logique de headshot personnalisée.
     */
    default boolean supportsCustomHeadshot(ItemStack stack) {
        return true;
    }
}