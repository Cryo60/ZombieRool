
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;

import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.WhiteKnightEntity;
import net.mcreator.zombierool.entity.MannequinEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.ZombieroolMod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZombieroolModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<EntityType<ZombieEntity>> ZOMBIE = register("zombie",
			EntityType.Builder.<ZombieEntity>of(ZombieEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(124).setUpdateInterval(3).setCustomClientFactory(ZombieEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<HellhoundEntity>> HELLHOUND = register("hellhound",
			EntityType.Builder.<HellhoundEntity>of(HellhoundEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(HellhoundEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<CrawlerEntity>> CRAWLER = register("crawler",
			EntityType.Builder.<CrawlerEntity>of(CrawlerEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(CrawlerEntity::new)

					.sized(1f, 0.9f));
	public static final RegistryObject<EntityType<WhiteKnightEntity>> WHITE_KNIGHT = register("white_knight",
			EntityType.Builder.<WhiteKnightEntity>of(WhiteKnightEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(WhiteKnightEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<MannequinEntity>> MANNEQUIN = register("mannequin",
			EntityType.Builder.<MannequinEntity>of(MannequinEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(MannequinEntity::new)

					.sized(0.6f, 1.8f));

	private static <T extends Entity> RegistryObject<EntityType<T>> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return REGISTRY.register(registryname, () -> (EntityType<T>) entityTypeBuilder.build(registryname));
	}

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			ZombieEntity.init();
			HellhoundEntity.init();
			CrawlerEntity.init();
			WhiteKnightEntity.init();
			MannequinEntity.init();
		});
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(ZOMBIE.get(), ZombieEntity.createAttributes().build());
		event.put(HELLHOUND.get(), HellhoundEntity.createAttributes().build());
		event.put(CRAWLER.get(), CrawlerEntity.createAttributes().build());
		event.put(WHITE_KNIGHT.get(), WhiteKnightEntity.createAttributes().build());
		event.put(MANNEQUIN.get(), MannequinEntity.createAttributes().build());
	}
}
