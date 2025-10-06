
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.mcreator.zombierool.client.model.Modelwolf;
import net.mcreator.zombierool.client.model.Modelmannequin;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public class ZombieroolModModels {
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(Modelwolf.LAYER_LOCATION, Modelwolf::createBodyLayer);
		event.registerLayerDefinition(Modelmannequin.LAYER_LOCATION, Modelmannequin::createBodyLayer);
	}
}
