package net.mcreator.zombierool.item;

import net.minecraft.world.item.Item;

/**
 * Interface marqueur pour les objets qui sont considérés comme des armes de poing (handguns)
 * dans le mod Zombierool. Tout objet d'arme qui doit se comporter comme un "handgun"
 * pour le système de mise à terre/réanimation doit implémenter cette interface.
 *
 * Exemple :
 * public class MyCustomHandgunItem extends Item implements IHandgunWeapon {
 * // ... votre implémentation d'item ...
 * }
 */
public interface IHandgunWeapon {
    // Aucune méthode n'est nécessaire si c'est une interface purement marqueur.
    // Si une logique spécifique aux armes de poing est requise, des méthodes pourraient être ajoutées ici.
    // Par exemple: boolean peutTirerEnEtatDown();
}
