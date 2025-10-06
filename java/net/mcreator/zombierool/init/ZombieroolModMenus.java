
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.extensions.IForgeMenuType;

import net.minecraft.world.inventory.MenuType;

import net.mcreator.zombierool.world.inventory.WallWeaponManagerMenu;
import net.mcreator.zombierool.world.inventory.SpawnerManagerMenu;
import net.mcreator.zombierool.world.inventory.PerksInterfaceMenu;
import net.mcreator.zombierool.world.inventory.ObstacleDoorManagerMenu;
import net.mcreator.zombierool.ZombieroolMod;

public class ZombieroolModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<MenuType<ObstacleDoorManagerMenu>> OBSTACLE_DOOR_MANAGER = REGISTRY.register("obstacle_door_manager", () -> IForgeMenuType.create(ObstacleDoorManagerMenu::new));
	public static final RegistryObject<MenuType<SpawnerManagerMenu>> SPAWNER_MANAGER = REGISTRY.register("spawner_manager", () -> IForgeMenuType.create(SpawnerManagerMenu::new));
	public static final RegistryObject<MenuType<WallWeaponManagerMenu>> WALL_WEAPON_MANAGER = REGISTRY.register("wall_weapon_manager", () -> IForgeMenuType.create(WallWeaponManagerMenu::new));
	public static final RegistryObject<MenuType<PerksInterfaceMenu>> PERKS_INTERFACE = REGISTRY.register("perks_interface", () -> IForgeMenuType.create(PerksInterfaceMenu::new));
}
