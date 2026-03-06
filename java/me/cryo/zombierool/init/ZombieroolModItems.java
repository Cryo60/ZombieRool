
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package me.cryo.zombierool.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.ForgeSpawnEggItem;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;

import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.item.BulletVestTier1Item;
import me.cryo.zombierool.item.BloodBrushItem;
import me.cryo.zombierool.ZombieroolMod;

public class ZombieroolModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
	public static final RegistryObject<Item> PATH = block(ZombieroolModBlocks.PATH);
	public static final RegistryObject<Item> ZOMBIE_SPAWN_EGG = REGISTRY.register("zombie_spawn_egg", () -> new ForgeSpawnEggItem(ZombieroolModEntities.ZOMBIE, -10092442, -10092544, new Item.Properties()));
	public static final RegistryObject<Item> LIMIT = block(ZombieroolModBlocks.LIMIT);
	public static final RegistryObject<Item> RESTRICT = block(ZombieroolModBlocks.RESTRICT);
	public static final RegistryObject<Item> INGOT_SALE = REGISTRY.register("ingot_sale", () -> new IngotSaleItem());
	public static final RegistryObject<Item> OBSTACLE_DOOR = block(ZombieroolModBlocks.OBSTACLE_DOOR);
	public static final RegistryObject<Item> POWER_SWITCH = block(ZombieroolModBlocks.POWER_SWITCH);
	public static final RegistryObject<Item> ACTIVATOR = block(ZombieroolModBlocks.ACTIVATOR);
	public static final RegistryObject<Item> ALPHA_ACTIVATOR = block(ZombieroolModBlocks.ALPHA_ACTIVATOR);
	public static final RegistryObject<Item> ALPHA_RECEPTOR = block(ZombieroolModBlocks.ALPHA_RECEPTOR);
	public static final RegistryObject<Item> BETA_ACTIVATOR = block(ZombieroolModBlocks.BETA_ACTIVATOR);
	public static final RegistryObject<Item> BETA_RECEPTOR = block(ZombieroolModBlocks.BETA_RECEPTOR);
	public static final RegistryObject<Item> OMEGA_ACTIVATOR = block(ZombieroolModBlocks.OMEGA_ACTIVATOR);
	public static final RegistryObject<Item> OMEGA_RECEPTOR = block(ZombieroolModBlocks.OMEGA_RECEPTOR);
	public static final RegistryObject<Item> ULTIMA_ACTIVATOR = block(ZombieroolModBlocks.ULTIMA_ACTIVATOR);
	public static final RegistryObject<Item> ULTIMA_RECEPTOR = block(ZombieroolModBlocks.ULTIMA_RECEPTOR);
	public static final RegistryObject<Item> HELLHOUND_SPAWN_EGG = REGISTRY.register("hellhound_spawn_egg", () -> new ForgeSpawnEggItem(ZombieroolModEntities.HELLHOUND, -10066432, -16751002, new Item.Properties()));
	public static final RegistryObject<Item> CRAWLER_SPAWN_EGG = REGISTRY.register("crawler_spawn_egg", () -> new ForgeSpawnEggItem(ZombieroolModEntities.CRAWLER, -13369549, -16764109, new Item.Properties()));
	public static final RegistryObject<Item> SPAWNER_ZOMBIE = block(ZombieroolModBlocks.SPAWNER_ZOMBIE);
	public static final RegistryObject<Item> SPAWNER_CRAWLER = block(ZombieroolModBlocks.SPAWNER_CRAWLER);
	public static final RegistryObject<Item> SPAWNER_DOG = block(ZombieroolModBlocks.SPAWNER_DOG);
	public static final RegistryObject<Item> TRAITOR = block(ZombieroolModBlocks.TRAITOR);
	public static final RegistryObject<Item> BUY_WALL_WEAPON = block(ZombieroolModBlocks.BUY_WALL_WEAPON);
	public static final RegistryObject<Item> PUNCH_PACK_CORPSE = block(ZombieroolModBlocks.PUNCH_PACK_CORPSE);
	public static final RegistryObject<Item> PUNCH_PACK = block(ZombieroolModBlocks.PUNCH_PACK);
	public static final RegistryObject<Item> PLAYER_SPAWNER = block(ZombieroolModBlocks.PLAYER_SPAWNER);
	public static final RegistryObject<Item> WHITE_KNIGHT_SPAWN_EGG = REGISTRY.register("white_knight_spawn_egg", () -> new ForgeSpawnEggItem(ZombieroolModEntities.WHITE_KNIGHT, -1, -1, new Item.Properties()));
	public static final RegistryObject<Item> EMPTYMYSTERYBOX = block(ZombieroolModBlocks.EMPTYMYSTERYBOX);
	public static final RegistryObject<Item> ZOMBIE_PASS = block(ZombieroolModBlocks.ZOMBIE_PASS);
	public static final RegistryObject<Item> BULLET_VEST_TIER_1_CHESTPLATE = REGISTRY.register("bullet_vest_tier_1_chestplate", () -> new BulletVestTier1Item.Chestplate());
	public static final RegistryObject<Item> AMMO_CRATE = block(ZombieroolModBlocks.AMMO_CRATE);
	public static final RegistryObject<Item> BLOOD_BRUSH = REGISTRY.register("blood_brush", () -> new BloodBrushItem());
	public static final RegistryObject<Item> BLACK_PUMPKIN = block(ZombieroolModBlocks.BLACK_PUMPKIN);
	public static final RegistryObject<Item> PERKS_LOWER = block(ZombieroolModBlocks.PERKS_LOWER);
	public static final RegistryObject<Item> DER_WUNDERFIZZ = block(ZombieroolModBlocks.DER_WUNDERFIZZ);
	public static final RegistryObject<Item> DUMMY_SPAWN_EGG = REGISTRY.register("dummy_spawn_egg", () -> new ForgeSpawnEggItem(ZombieroolModEntities.DUMMY, -1, -1, new Item.Properties()));
	public static final RegistryObject<Item> MYSTERY_BOX = block(ZombieroolModBlocks.MYSTERY_BOX);

	private static RegistryObject<Item> block(RegistryObject<Block> block) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
	}
}
