package net.mcreator.zombierool.api;

import net.minecraft.world.item.ItemStack;

public interface ICustomWeapon {
/** Renvoie les dégâts de cette arme pour le ItemStack donné. */
	float getWeaponDamage(ItemStack stack);
} 