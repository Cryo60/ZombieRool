
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZombieroolMod.MODID);
	public static final RegistryObject<CreativeModeTab> ZB_RCT = REGISTRY.register("zb_rct",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.zombierool.zb_rct")).icon(() -> new ItemStack(ZombieroolModBlocks.DER_WUNDERFIZZ.get())).displayItems((parameters, tabData) -> {
				tabData.accept(ZombieroolModBlocks.PATH.get().asItem());
				tabData.accept(ZombieroolModItems.ZOMBIE_SPAWN_EGG.get());
				tabData.accept(ZombieroolModBlocks.LIMIT.get().asItem());
				tabData.accept(ZombieroolModBlocks.RESTRICT.get().asItem());
				tabData.accept(ZombieroolModItems.INGOT_SALE.get());
				tabData.accept(ZombieroolModBlocks.POWER_SWITCH.get().asItem());
				tabData.accept(ZombieroolModBlocks.ACTIVATOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.ALPHA_ACTIVATOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.ALPHA_RECEPTOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.BETA_ACTIVATOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.BETA_RECEPTOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.OMEGA_ACTIVATOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.OMEGA_RECEPTOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.ULTIMA_ACTIVATOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.ULTIMA_RECEPTOR.get().asItem());
				tabData.accept(ZombieroolModItems.HELLHOUND_SPAWN_EGG.get());
				tabData.accept(ZombieroolModItems.CRAWLER_SPAWN_EGG.get());
				tabData.accept(ZombieroolModBlocks.TRAITOR.get().asItem());
				tabData.accept(ZombieroolModBlocks.ZOMBIE_PASS.get().asItem());
				tabData.accept(ZombieroolModBlocks.AMMO_CRATE.get().asItem());
				tabData.accept(ZombieroolModItems.BLOOD_BRUSH.get());
				tabData.accept(ZombieroolModBlocks.DER_WUNDERFIZZ.get().asItem());
				tabData.accept(ZombieroolModItems.DUMMY_SPAWN_EGG.get());
				tabData.accept(ZombieroolModItems.CHALK.get());
			}).withSearchBar().build());
}
