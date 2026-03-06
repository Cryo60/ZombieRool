
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.extensions.IForgeMenuType;

import net.minecraft.world.inventory.MenuType;

import me.cryo.zombierool.world.inventory.WallWeaponManagerMenu;
import me.cryo.zombierool.world.inventory.SpawnerManagerMenu;
import me.cryo.zombierool.world.inventory.PerksInterfaceMenu;
import me.cryo.zombierool.world.inventory.ObstacleDoorManagerMenu;
import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<MenuType<ObstacleDoorManagerMenu>> OBSTACLE_DOOR_MANAGER = REGISTRY.register("obstacle_door_manager", () -> IForgeMenuType.create(ObstacleDoorManagerMenu::new));
	public static final RegistryObject<MenuType<SpawnerManagerMenu>> SPAWNER_MANAGER = REGISTRY.register("spawner_manager", () -> IForgeMenuType.create(SpawnerManagerMenu::new));
	public static final RegistryObject<MenuType<WallWeaponManagerMenu>> WALL_WEAPON_MANAGER = REGISTRY.register("wall_weapon_manager", () -> IForgeMenuType.create(WallWeaponManagerMenu::new));
	public static final RegistryObject<MenuType<PerksInterfaceMenu>> PERKS_INTERFACE = REGISTRY.register("perks_interface", () -> IForgeMenuType.create(PerksInterfaceMenu::new));
}
