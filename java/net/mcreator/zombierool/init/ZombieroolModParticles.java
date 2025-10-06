
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.mcreator.zombierool.client.particle.ZombieBloodParticle;
import net.mcreator.zombierool.client.particle.WishParticle;
import net.mcreator.zombierool.client.particle.OnTheHouseParticle;
import net.mcreator.zombierool.client.particle.NukeParticle;
import net.mcreator.zombierool.client.particle.MaxammoParticle;
import net.mcreator.zombierool.client.particle.InstakillParticle;
import net.mcreator.zombierool.client.particle.GoldRushParticle;
import net.mcreator.zombierool.client.particle.DoublePointsParticle;
import net.mcreator.zombierool.client.particle.CarpenterParticle;
import net.mcreator.zombierool.client.particle.BulletImpactParticle;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZombieroolModParticles {
	@SubscribeEvent
	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(ZombieroolModParticleTypes.INSTAKILL.get(), InstakillParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.MAXAMMO.get(), MaxammoParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.NUKE.get(), NukeParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.DOUBLE_POINTS.get(), DoublePointsParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.CARPENTER.get(), CarpenterParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.BULLET_IMPACT.get(), BulletImpactParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.GOLD_RUSH.get(), GoldRushParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.ZOMBIE_BLOOD.get(), ZombieBloodParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.WISH.get(), WishParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.ON_THE_HOUSE.get(), OnTheHouseParticle::provider);
	}
}
