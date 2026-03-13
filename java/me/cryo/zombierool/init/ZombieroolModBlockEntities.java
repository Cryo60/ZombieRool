
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;

import me.cryo.zombierool.block.entity.TraitorBlockEntity;
import me.cryo.zombierool.block.entity.PunchPackBlockEntity;
import me.cryo.zombierool.block.entity.PerksLowerBlockEntity;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.entity.MysteryBoxBlockEntity;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<BlockEntityType<?>> OBSTACLE_DOOR = register("obstacle_door", ZombieroolModBlocks.OBSTACLE_DOOR, ObstacleDoorBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> TRAITOR = register("traitor", ZombieroolModBlocks.TRAITOR, TraitorBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> BUY_WALL_WEAPON = register("buy_wall_weapon", ZombieroolModBlocks.BUY_WALL_WEAPON, BuyWallWeaponBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> PUNCH_PACK = register("punch_pack", ZombieroolModBlocks.PUNCH_PACK, PunchPackBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> PERKS_LOWER = register("perks_lower", ZombieroolModBlocks.PERKS_LOWER, PerksLowerBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> DER_WUNDERFIZZ = register("der_wunderfizz", ZombieroolModBlocks.DER_WUNDERFIZZ, DerWunderfizzBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> MYSTERY_BOX = register("mystery_box", ZombieroolModBlocks.MYSTERY_BOX, MysteryBoxBlockEntity::new);

	private static RegistryObject<BlockEntityType<?>> register(String registryname, RegistryObject<Block> block, BlockEntityType.BlockEntitySupplier<?> supplier) {
		return REGISTRY.register(registryname, () -> BlockEntityType.Builder.of(supplier, block.get()).build(null));
	}
}
