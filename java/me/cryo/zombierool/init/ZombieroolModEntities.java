
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

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

import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.entity.WhiteKnightEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.GorePieceEntity;
import me.cryo.zombierool.entity.BloodDecalEntity;
import me.cryo.zombierool.ZombieroolMod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZombieroolModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<EntityType<me.cryo.zombierool.entity.MapActorEntity>> MAP_ACTOR = register("map_actor",EntityType.Builder.<me.cryo.zombierool.entity.MapActorEntity>of(me.cryo.zombierool.entity.MapActorEntity::new,MobCategory.MONSTER).sized(.6f,1.8f).clientTrackingRange(96).updateInterval(2));
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> PENTAGON_THIEF=special("pentagon_thief",.6f,1.95f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> GEIST=special("geist",.65f,1.9f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> CUSTOM_MINI_BOSS=special("custom_mini_boss",.95f,2.8f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> PANZER=special("panzer",.95f,2.8f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> BRUTUS=special("brutus",.95f,2.65f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> NAPALM_MINION=special("napalm_minion",.6f,1.8f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> SKELETON_MINION=special("skeleton_minion",.4f,1.3f);
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.NapalmFireEntity>> NAPALM_FIRE=register("napalm_fire",EntityType.Builder.<me.cryo.zombierool.entity.NapalmFireEntity>of(me.cryo.zombierool.entity.NapalmFireEntity::new,MobCategory.MISC).sized(8,.2f).clientTrackingRange(64).updateInterval(10));
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.PanzerClawEntity>> PANZER_CLAW=register("panzer_claw",EntityType.Builder.<me.cryo.zombierool.entity.PanzerClawEntity>of(me.cryo.zombierool.entity.PanzerClawEntity::new,MobCategory.MISC).sized(.3f,.3f).clientTrackingRange(64).updateInterval(1).noSave());
    private static RegistryObject<EntityType<me.cryo.zombierool.entity.SpecialEnemyEntity>> special(String id,float width,float height){return register(id,EntityType.Builder.<me.cryo.zombierool.entity.SpecialEnemyEntity>of(me.cryo.zombierool.entity.SpecialEnemyEntity::new,MobCategory.MONSTER).sized(width,height).setShouldReceiveVelocityUpdates(true).clientTrackingRange(64).updateInterval(2));}
    public static final RegistryObject<EntityType<me.cryo.zombierool.entity.VrilHumanEntity>> VRIL_HUMAN=register("vril_human",EntityType.Builder.<me.cryo.zombierool.entity.VrilHumanEntity>of(me.cryo.zombierool.entity.VrilHumanEntity::new,MobCategory.MISC).sized(.6f,1.8f).clientTrackingRange(64).updateInterval(2).noSave().noSummon());
	public static final RegistryObject<EntityType<me.cryo.zombierool.entity.SoulOrbEntity>> SOUL_ORB = register("soul_orb",EntityType.Builder.<me.cryo.zombierool.entity.SoulOrbEntity>of(me.cryo.zombierool.entity.SoulOrbEntity::new,MobCategory.MISC).sized(.5f,.5f).clientTrackingRange(128).updateInterval(1).noSave().noSummon().setCustomClientFactory(me.cryo.zombierool.entity.SoulOrbEntity::new));
	public static final RegistryObject<EntityType<ZombieEntity>> ZOMBIE = register("zombie",
			EntityType.Builder.<ZombieEntity>of(ZombieEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(124).setUpdateInterval(3).setCustomClientFactory(ZombieEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<HellhoundEntity>> HELLHOUND = register("hellhound",
			EntityType.Builder.<HellhoundEntity>of(HellhoundEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(HellhoundEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<CrawlerEntity>> CRAWLER = register("crawler",
			EntityType.Builder.<CrawlerEntity>of(CrawlerEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(CrawlerEntity::new)

					.sized(1.4f, 0.9f));
	public static final RegistryObject<EntityType<WhiteKnightEntity>> WHITE_KNIGHT = register("white_knight",
			EntityType.Builder.<WhiteKnightEntity>of(WhiteKnightEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(WhiteKnightEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<DummyEntity>> DUMMY = register("dummy",
			EntityType.Builder.<DummyEntity>of(DummyEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).setCustomClientFactory(DummyEntity::new)

					.sized(0.6f, 1.8f));
	public static final RegistryObject<EntityType<GorePieceEntity>> GORE_PIECE = register("gore_piece",
			EntityType.Builder.<GorePieceEntity>of(GorePieceEntity::new, MobCategory.MISC).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(2).setCustomClientFactory(GorePieceEntity::new)
					.sized(0.3f, 0.3f));
	public static final RegistryObject<EntityType<BloodDecalEntity>> BLOOD_DECAL = register("blood_decal",
			EntityType.Builder.<BloodDecalEntity>of(BloodDecalEntity::new, MobCategory.MISC).setShouldReceiveVelocityUpdates(false).setTrackingRange(48).setUpdateInterval(20).setCustomClientFactory(BloodDecalEntity::new)
					.sized(0.8f, 0.05f));

	public static final RegistryObject<EntityType<me.cryo.zombierool.entity.MrChiefEntity>> MR_CHIEF = register("mr_chief", EntityType.Builder.<me.cryo.zombierool.entity.MrChiefEntity>of(me.cryo.zombierool.entity.MrChiefEntity::new, MobCategory.CREATURE).sized(.6f,.7f).clientTrackingRange(48).updateInterval(2).noSummon().noSave());

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
			DummyEntity.init();
		});
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PENTAGON_THIEF.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(350,.34,0).build());
        event.put(GEIST.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(30,.28,1).build());
        event.put(MAP_ACTOR.get(),me.cryo.zombierool.entity.MapActorEntity.attributes().build());
        event.put(CUSTOM_MINI_BOSS.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(360,.36,4).build());
        event.put(PANZER.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(480,.30,4).build());
        event.put(BRUTUS.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(360,.36,3).build());
        event.put(NAPALM_MINION.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(10,.16,2).build());
        event.put(SKELETON_MINION.get(),me.cryo.zombierool.entity.SpecialEnemyEntity.attributes(4,.34,1).build());
        event.put(VRIL_HUMAN.get(),me.cryo.zombierool.entity.VrilHumanEntity.attributes().build());
		event.put(MR_CHIEF.get(), me.cryo.zombierool.entity.MrChiefEntity.attributes().build());
		event.put(ZOMBIE.get(), ZombieEntity.createAttributes().build());
		event.put(HELLHOUND.get(), HellhoundEntity.createAttributes().build());
		event.put(CRAWLER.get(), CrawlerEntity.createAttributes().build());
		event.put(WHITE_KNIGHT.get(), WhiteKnightEntity.createAttributes().build());
		event.put(DUMMY.get(), DummyEntity.createAttributes().build());
	}
}
