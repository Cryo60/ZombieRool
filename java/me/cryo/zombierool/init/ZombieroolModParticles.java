
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.api.distmarker.Dist;

import me.cryo.zombierool.client.particle.ZombieBloodParticle;
import me.cryo.zombierool.client.particle.WishParticle;
import me.cryo.zombierool.client.particle.ToxicSmokeParticle;
import me.cryo.zombierool.client.particle.OnTheHouseParticle;
import me.cryo.zombierool.client.particle.NukeParticle;
import me.cryo.zombierool.client.particle.MaxammoParticle;
import me.cryo.zombierool.client.particle.InstakillParticle;
import me.cryo.zombierool.client.particle.GoldRushParticle;
import me.cryo.zombierool.client.particle.DoublePointsParticle;
import me.cryo.zombierool.client.particle.CarpenterParticle;
import me.cryo.zombierool.client.particle.BlackCrowParticle;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ZombieroolModParticles {
	@SubscribeEvent
	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(ZombieroolModParticleTypes.INSTAKILL.get(), InstakillParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.MAXAMMO.get(), MaxammoParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.NUKE.get(), NukeParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.DOUBLE_POINTS.get(), DoublePointsParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.CARPENTER.get(), CarpenterParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.GOLD_RUSH.get(), GoldRushParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.ZOMBIE_BLOOD.get(), ZombieBloodParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.WISH.get(), WishParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.ON_THE_HOUSE.get(), OnTheHouseParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.BLACK_CROW.get(), BlackCrowParticle::provider);
		event.registerSpriteSet(ZombieroolModParticleTypes.TOXIC_SMOKE.get(), ToxicSmokeParticle::provider);
	}
}
