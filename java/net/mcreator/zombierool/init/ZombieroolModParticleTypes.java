
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleType;

import net.mcreator.zombierool.ZombieroolMod;

public class ZombieroolModParticleTypes {
	public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<SimpleParticleType> INSTAKILL = REGISTRY.register("instakill", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> MAXAMMO = REGISTRY.register("maxammo", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> NUKE = REGISTRY.register("nuke", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> DOUBLE_POINTS = REGISTRY.register("double_points", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> CARPENTER = REGISTRY.register("carpenter", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> BULLET_IMPACT = REGISTRY.register("bullet_impact", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> GOLD_RUSH = REGISTRY.register("gold_rush", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> ZOMBIE_BLOOD = REGISTRY.register("zombie_blood", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> WISH = REGISTRY.register("wish", () -> new SimpleParticleType(false));
	public static final RegistryObject<SimpleParticleType> ON_THE_HOUSE = REGISTRY.register("on_the_house", () -> new SimpleParticleType(false));
}
