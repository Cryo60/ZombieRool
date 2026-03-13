
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.client.gui.screens.MenuScreens;

import me.cryo.zombierool.client.gui.WallWeaponManagerScreen;
import me.cryo.zombierool.client.gui.PerksInterfaceScreen;
import me.cryo.zombierool.client.gui.ObstacleDoorManagerScreen;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZombieroolModScreens {
	@SubscribeEvent
	public static void clientLoad(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			MenuScreens.register(ZombieroolModMenus.OBSTACLE_DOOR_MANAGER.get(), ObstacleDoorManagerScreen::new);
			MenuScreens.register(ZombieroolModMenus.WALL_WEAPON_MANAGER.get(), WallWeaponManagerScreen::new);
			MenuScreens.register(ZombieroolModMenus.PERKS_INTERFACE.get(), PerksInterfaceScreen::new);
		});
	}
}
