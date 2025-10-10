
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;

import net.mcreator.zombierool.block.entity.TraitorBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import net.mcreator.zombierool.block.entity.PunchPackBlockEntity;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;
import net.mcreator.zombierool.block.entity.MysteryBoxBlockEntity;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.mcreator.zombierool.ZombieroolMod;

public class ZombieroolModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<BlockEntityType<?>> OBSTACLE_DOOR = register("obstacle_door", ZombieroolModBlocks.OBSTACLE_DOOR, ObstacleDoorBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> SPAWNER_ZOMBIE = register("spawner_zombie", ZombieroolModBlocks.SPAWNER_ZOMBIE, SpawnerZombieBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> SPAWNER_CRAWLER = register("spawner_crawler", ZombieroolModBlocks.SPAWNER_CRAWLER, SpawnerCrawlerBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> SPAWNER_DOG = register("spawner_dog", ZombieroolModBlocks.SPAWNER_DOG, SpawnerDogBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> TRAITOR = register("traitor", ZombieroolModBlocks.TRAITOR, TraitorBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> BUY_WALL_WEAPON = register("buy_wall_weapon", ZombieroolModBlocks.BUY_WALL_WEAPON, BuyWallWeaponBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> PUNCH_PACK = register("punch_pack", ZombieroolModBlocks.PUNCH_PACK, PunchPackBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> MYSTERY_BOX = register("mystery_box", ZombieroolModBlocks.MYSTERY_BOX, MysteryBoxBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> PERKS_LOWER = register("perks_lower", ZombieroolModBlocks.PERKS_LOWER, PerksLowerBlockEntity::new);

	private static RegistryObject<BlockEntityType<?>> register(String registryname, RegistryObject<Block> block, BlockEntityType.BlockEntitySupplier<?> supplier) {
		return REGISTRY.register(registryname, () -> BlockEntityType.Builder.of(supplier, block.get()).build(null));
	}
}
