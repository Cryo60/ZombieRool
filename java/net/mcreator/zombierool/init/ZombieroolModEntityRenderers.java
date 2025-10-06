
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.mcreator.zombierool.client.renderer.ZombieRenderer;
import net.mcreator.zombierool.client.renderer.WhiteKnightRenderer;
import net.mcreator.zombierool.client.renderer.MannequinRenderer;
import net.mcreator.zombierool.client.renderer.HellhoundRenderer;
import net.mcreator.zombierool.client.renderer.CrawlerRenderer;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZombieroolModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ZombieroolModEntities.ZOMBIE.get(), ZombieRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.HELLHOUND.get(), HellhoundRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.CRAWLER.get(), CrawlerRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.WHITE_KNIGHT.get(), WhiteKnightRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.MANNEQUIN.get(), MannequinRenderer::new);
	}
}
