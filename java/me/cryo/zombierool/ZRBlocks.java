package me.cryo.zombierool.core.registry;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.MeteoriteEasterEgg;
import me.cryo.zombierool.core.block.ZRDecorativeBlock;
import me.cryo.zombierool.core.block.ZRDirectionalBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZRBlocks {
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ZombieroolMod.MODID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ZombieroolMod.MODID);
	public static final List<ResourceLocation> ITEM_IDS = new ArrayList<>();
	public static final List<RegistryObject<Block>> CUTOUT_BLOCKS = new ArrayList<>();

	public static final RegistryObject<Block> DEFENSE_DOOR = registerBlock("defense_door", DefenseDoorSystem.DefenseDoorBlock::new);
	public static final RegistryObject<Block> STORAGE_BOX = registerBlock("storage_box", () -> new ZRDecorativeBlock(SoundType.WOOD, ZRDecorativeBlock.ShapeType.FULL, true));
	public static final RegistryObject<Block> DEFENSE_DOOR_OPENED = registerBlockNoItem("defense_door_opened", DefenseDoorSystem.DefenseDoorOpenedBlock::new);
	
    // AJOUT DU BLOC METEORITE ICI
    public static final RegistryObject<Block> METEORITE = registerBlock("meteorite", MeteoriteEasterEgg.MeteoriteBlock::new);

	static {
	    IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
	    BLOCKS.register(bus);
	    ITEMS.register(bus);
	    registerAllContent();
	}

	private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> blockFactory) {
	    RegistryObject<T> block = BLOCKS.register(name, blockFactory);
	    ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
	    ITEM_IDS.add(new ResourceLocation(ZombieroolMod.MODID, name));
	    return block;
	}

	private static <T extends Block> RegistryObject<T> registerBlockNoItem(String name, Supplier<T> blockFactory) {
	    return BLOCKS.register(name, blockFactory);
	}

	private static void registerFamily(String name, SoundType sound, boolean transparent) {
	    RegistryObject<Block> baseBlock = registerBlock(name, () -> new ZRDecorativeBlock(sound, ZRDecorativeBlock.ShapeType.FULL, transparent));
	    if (transparent) CUTOUT_BLOCKS.add(baseBlock);
	    RegistryObject<Block> stairs = registerBlock(name + "_stairs", () -> new StairBlock(() -> baseBlock.get().defaultBlockState(), BlockBehaviour.Properties.copy(baseBlock.get())));
	    if (transparent) CUTOUT_BLOCKS.add(stairs);
	    RegistryObject<Block> slab = registerBlock(name + "_slab", () -> new SlabBlock(BlockBehaviour.Properties.copy(baseBlock.get())));
	    if (transparent) CUTOUT_BLOCKS.add(slab);
	    if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD) {
	        RegistryObject<Block> fence = registerBlock(name + "_fence", () -> new FenceBlock(BlockBehaviour.Properties.copy(baseBlock.get())));
	        if (transparent) CUTOUT_BLOCKS.add(fence);
	    } else {
	        RegistryObject<Block> wall = registerBlock(name + "_wall", () -> new WallBlock(BlockBehaviour.Properties.copy(baseBlock.get())));
	        if (transparent) CUTOUT_BLOCKS.add(wall);
	    }
	}

	private static void registerGlassFamily(String name, SoundType sound) {
	    RegistryObject<Block> baseBlock = registerBlock(name, () -> new ZRDecorativeBlock(sound, ZRDecorativeBlock.ShapeType.FULL, true));
	    CUTOUT_BLOCKS.add(baseBlock);
	    RegistryObject<Block> slab = registerBlock(name + "_slab", () -> new SlabBlock(BlockBehaviour.Properties.copy(baseBlock.get())));
	    CUTOUT_BLOCKS.add(slab);
	    RegistryObject<Block> pane = registerBlock(name + "_pane", () -> new IronBarsBlock(BlockBehaviour.Properties.copy(baseBlock.get()).noOcclusion()));
	    CUTOUT_BLOCKS.add(pane);
	}

	private static void registerCross(String name, SoundType sound, boolean passable, int lightLevel) {
	    RegistryObject<Block> block = registerBlock(name, () -> new ZRDecorativeBlock(sound, ZRDecorativeBlock.ShapeType.CROSS, true, passable, lightLevel));
	    CUTOUT_BLOCKS.add(block);
	}

	private static void registerSimple(String name, SoundType sound, boolean transparent) {
	    RegistryObject<Block> block = registerBlock(name, () -> new ZRDecorativeBlock(sound, ZRDecorativeBlock.ShapeType.FULL, transparent));
	    if (transparent) CUTOUT_BLOCKS.add(block);
	}

	private static void registerCarpet(String name, SoundType sound) {
	    RegistryObject<Block> block = registerBlock(name, () -> new ZRDecorativeBlock(sound, ZRDecorativeBlock.ShapeType.CARPET, true, true, 0));
	    CUTOUT_BLOCKS.add(block);
	}

	private static void registerDirectional(String name, SoundType sound) {
	    registerBlock(name, () -> new ZRDirectionalBlock(sound));
	}

	private static void registerAllContent() {
	    registerFamily("planks_black", SoundType.WOOD, false);
	    registerFamily("planks_blue", SoundType.WOOD, false);
	    registerFamily("planks_brown", SoundType.WOOD, false);
	    registerFamily("planks_cyan", SoundType.WOOD, false);
	    registerFamily("planks_gray", SoundType.WOOD, false);
	    registerFamily("planks_orange", SoundType.WOOD, false);
	    registerFamily("planks_pink", SoundType.WOOD, false);
	    registerFamily("planks_red", SoundType.WOOD, false);
	    registerFamily("planks_silver", SoundType.WOOD, false);
	    registerFamily("planks_white", SoundType.WOOD, false);
	    registerFamily("planks_yellow", SoundType.WOOD, false);
	    registerFamily("old_planks", SoundType.WOOD, false);
	    registerFamily("deco_planks", SoundType.WOOD, false);
	    registerFamily("balsatic_stone", SoundType.STONE, false);
	    registerFamily("brick_granite", SoundType.STONE, false);
	    registerFamily("brick_iron", SoundType.STONE, false);
	    registerFamily("brick_slate", SoundType.STONE, false);
	    registerFamily("brick_steel", SoundType.METAL, false);
	    registerFamily("brick_stone", SoundType.STONE, false);
	    registerFamily("carved_slate", SoundType.STONE, false);
	    registerFamily("carved_stone", SoundType.STONE, false);
	    registerFamily("cobbled_and_stoned", SoundType.DEEPSLATE, false);
	    registerFamily("cracked_shadowed_brick", SoundType.DEEPSLATE_BRICKS, false);
	    registerFamily("cracked_shadowed_tiles", SoundType.DEEPSLATE_TILES, false);
	    registerFamily("darkbrick", SoundType.STONE, false);
	    registerFamily("deco_stone", SoundType.STONE, false);
	    registerFamily("deco_stone_2", SoundType.STONE, false);
	    registerFamily("fel_bricks", SoundType.POLISHED_DEEPSLATE, false);
	    registerFamily("old_cobblestone", SoundType.STONE, false);
	    registerFamily("old_mossy_cobblestone", SoundType.STONE, false);
	    registerFamily("perfect_andesit", SoundType.STONE, false);
	    registerFamily("perfect_diorite", SoundType.STONE, false);
	    registerFamily("perfect_granite", SoundType.STONE, false);
	    registerFamily("perfected_andesit", SoundType.STONE, false);
	    registerFamily("perfected_diorite", SoundType.STONE, false);
	    registerFamily("perfected_granite", SoundType.STONE, false);
	    registerFamily("retro_brick", SoundType.STONE, false);
	    registerFamily("retro_cobblestone", SoundType.STONE, false);
	    registerFamily("sewerbrick", SoundType.STONE, false);
	    registerFamily("shadow_bricks", SoundType.STONE, false);
	    registerFamily("shadow_tiles", SoundType.STONE, false);
	    registerFamily("squarefloor", SoundType.STONE, false);
	    registerFamily("steel_slate", SoundType.METAL, false);
	    registerFamily("tile_block", SoundType.STONE, false);
	    registerFamily("whitebrick", SoundType.STONE, false);
	    registerGlassFamily("glass_steel", SoundType.GLASS);
	    registerGlassFamily("glass_tinted", SoundType.GLASS);
	    registerGlassFamily("retro_glass", SoundType.GLASS);
	    registerSimple("haunted_glass", SoundType.GLASS, true);
	    registerSimple("not_haunted_glass", SoundType.GLASS, true);
	    registerSimple("haunted_window", SoundType.GLASS, true);
	    registerFamily("mesh", SoundType.METAL, true);
	    registerFamily("black_coarsed_dirt", SoundType.GRAVEL, false);
	    registerFamily("black_dirt", SoundType.GRAVEL, false);
	    registerFamily("deco_dirt", SoundType.WET_GRASS, false);
	    registerFamily("deco_farmland", SoundType.WET_GRASS, false);
	    registerFamily("deco_grass", SoundType.WET_GRASS, false);
	    registerFamily("mud", SoundType.MUD, false);
	    registerFamily("mud_2", SoundType.MUD, false);
	    registerFamily("permafrost_grass", SoundType.GRAVEL, false);
	    registerGlassFamily("permafrost_ice", SoundType.GLASS);
	    registerFamily("retro_grass", SoundType.GRASS, false);
	    registerFamily("retro_gravel", SoundType.GRAVEL, false);
	    registerFamily("scorched_dirt", SoundType.SAND, false);
	    registerFamily("sorched_grass", SoundType.GRASS, false);
	    registerFamily("weird_grass", SoundType.GRASS, false);
	    registerFamily("wet_dirt", SoundType.WET_GRASS, false);
	    registerSimple("purplewallpaper", SoundType.WOOL, false);
	    registerCarpet("soul_lichen", SoundType.VINE);
	    registerDirectional("machine_block", SoundType.METAL);
	    registerDirectional("modern_furnace", SoundType.METAL);
	    registerDirectional("sandbags", SoundType.GRAVEL);
	    registerSimple("darkbrickvariation", SoundType.STONE, false);
	    registerSimple("opaque_leaves", SoundType.GRASS, false);
	    registerSimple("eye_oak", SoundType.WOOD, false);
	    registerSimple("haunted_planks", SoundType.WOOD, false);
	    registerSimple("suspicious_haunted_planks", SoundType.WOOD, false);
	    registerSimple("raw_obscurium", SoundType.STONE, false);
	    registerSimple("obscurium", SoundType.AMETHYST, false);
	    registerBlock("iron_stairs", () -> new StairBlock(() -> Blocks.IRON_BLOCK.defaultBlockState(), BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
	    registerBlock("iron_slabs", () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
	    registerCross("spider_web", SoundType.STONE, true, 0);
	    registerCross("tiny_bones", SoundType.GRAVEL, true, 0);
	    registerCross("tiny_candles", SoundType.GRAVEL, true, 12);
	    registerCross("tiny_flame", SoundType.GRAVEL, true, 15);
	    registerCross("tiny_skull", SoundType.GRAVEL, true, 0);
	}
}