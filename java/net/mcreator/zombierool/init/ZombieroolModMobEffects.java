
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.effect.MobEffect;

import net.mcreator.zombierool.potion.PerksEffectVultureMobEffect;
import net.mcreator.zombierool.potion.PerksEffectSpeedColaMobEffect;
import net.mcreator.zombierool.potion.PerksEffectRoyalBeerMobEffect;
import net.mcreator.zombierool.potion.PerksEffectQuickReviveMobEffect;
import net.mcreator.zombierool.potion.PerksEffectPHDFlopperMobEffect;
import net.mcreator.zombierool.potion.PerksEffectMastodonteMobEffect;
import net.mcreator.zombierool.potion.PerksEffectDoubleTapeMobEffect;
import net.mcreator.zombierool.potion.PerksEffectCherryMobEffect;
import net.mcreator.zombierool.potion.PerksEffectBloodRageMobEffect;
import net.mcreator.zombierool.ZombieroolMod;

public class ZombieroolModMobEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ZombieroolMod.MODID);
	public static final RegistryObject<MobEffect> PERKS_EFFECT_MASTODONTE = REGISTRY.register("perks_effect_mastodonte", () -> new PerksEffectMastodonteMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_SPEED_COLA = REGISTRY.register("perks_effect_speed_cola", () -> new PerksEffectSpeedColaMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_DOUBLE_TAPE = REGISTRY.register("perks_effect_double_tape", () -> new PerksEffectDoubleTapeMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_BLOOD_RAGE = REGISTRY.register("perks_effect_blood_rage", () -> new PerksEffectBloodRageMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_QUICK_REVIVE = REGISTRY.register("perks_effect_quick_revive", () -> new PerksEffectQuickReviveMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_PHD_FLOPPER = REGISTRY.register("perks_effect_phd_flopper", () -> new PerksEffectPHDFlopperMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_CHERRY = REGISTRY.register("perks_effect_cherry", () -> new PerksEffectCherryMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_ROYAL_BEER = REGISTRY.register("perks_effect_royal_beer", () -> new PerksEffectRoyalBeerMobEffect());
	public static final RegistryObject<MobEffect> PERKS_EFFECT_VULTURE = REGISTRY.register("perks_effect_vulture", () -> new PerksEffectVultureMobEffect());
}
