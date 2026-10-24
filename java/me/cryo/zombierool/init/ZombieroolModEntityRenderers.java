
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;

import me.cryo.zombierool.client.renderer.ZombieRenderer;
import me.cryo.zombierool.client.renderer.WhiteKnightRenderer;
import me.cryo.zombierool.client.renderer.HellhoundRenderer;
import me.cryo.zombierool.client.renderer.DummyRenderer;
import me.cryo.zombierool.client.renderer.CrawlerRenderer;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZombieroolModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ZombieroolModEntities.MAP_ACTOR.get(),me.cryo.zombierool.client.renderer.MapActorRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.NAPALM_FIRE.get(),me.cryo.zombierool.client.renderer.NapalmFireRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.PANZER_CLAW.get(),me.cryo.zombierool.client.renderer.PanzerClawRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.PENTAGON_THIEF.get(),c->new me.cryo.zombierool.client.renderer.BlockyEnemyRenderer<>(c,"pentagon_thief"));
        event.registerEntityRenderer(ZombieroolModEntities.GEIST.get(),c->new me.cryo.zombierool.client.renderer.BlockyEnemyRenderer<>(c,"geist"));
        event.registerEntityRenderer(ZombieroolModEntities.CUSTOM_MINI_BOSS.get(),me.cryo.zombierool.client.renderer.CustomMiniBossRenderer::new);
        event.registerEntityRenderer(ZombieroolModEntities.PANZER.get(),ctx->new me.cryo.zombierool.client.renderer.MinecraftBossRenderer(ctx,"panzer"));
        event.registerEntityRenderer(ZombieroolModEntities.BRUTUS.get(),ctx->new me.cryo.zombierool.client.renderer.MinecraftBossRenderer(ctx,"brutus"));
        event.registerEntityRenderer(ZombieroolModEntities.NAPALM_MINION.get(),ctx->new me.cryo.zombierool.client.renderer.SpecialEnemyRenderer(ctx,"napalm_minion"));
        event.registerEntityRenderer(ZombieroolModEntities.SKELETON_MINION.get(),ctx->new me.cryo.zombierool.client.renderer.SpecialEnemyRenderer(ctx,"skeleton_minion"));
        event.registerEntityRenderer(ZombieroolModEntities.VRIL_HUMAN.get(),me.cryo.zombierool.client.renderer.VrilHumanRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.SOUL_ORB.get(),me.cryo.zombierool.client.renderer.TrackedSoulRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.MR_CHIEF.get(), me.cryo.zombierool.client.renderer.MrChiefRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.ZOMBIE.get(), ZombieRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.HELLHOUND.get(), HellhoundRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.CRAWLER.get(), CrawlerRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.WHITE_KNIGHT.get(), WhiteKnightRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.DUMMY.get(), DummyRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.GORE_PIECE.get(), me.cryo.zombierool.entity.GorePieceEntity.GorePieceRenderer::new);
		event.registerEntityRenderer(ZombieroolModEntities.BLOOD_DECAL.get(), me.cryo.zombierool.entity.BloodDecalEntity.BloodDecalRenderer::new);
	}
}
