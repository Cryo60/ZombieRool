
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
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ZombieroolMod.MODID);
	public static final RegistryObject<BlockEntityType<?>> TRAITOR = register("traitor", ZombieroolModBlocks.TRAITOR, TraitorBlockEntity::new);
	public static final RegistryObject<BlockEntityType<?>> DER_WUNDERFIZZ = register("der_wunderfizz", ZombieroolModBlocks.DER_WUNDERFIZZ, DerWunderfizzBlockEntity::new);

	private static RegistryObject<BlockEntityType<?>> register(String registryname, RegistryObject<Block> block, BlockEntityType.BlockEntitySupplier<?> supplier) {
		return REGISTRY.register(registryname, () -> BlockEntityType.Builder.of(supplier, block.get()).build(null));
	}
}
