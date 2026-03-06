package me.cryo.zombierool.block.entity;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.world.inventory.WallWeaponManagerMenu;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.block.BuyWallWeaponBlock;
import me.cryo.zombierool.block.system.MimicSystem;
import javax.annotation.Nullable;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import io.netty.buffer.Unpooled;
public class BuyWallWeaponBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, MimicSystem.IMimicContainer {
	private NonNullList<ItemStack> stacks = NonNullList.withSize(1, ItemStack.EMPTY);
	private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());
	private int price = 0;
	private ResourceLocation itemToSell = null;
	private BlockState mimicBlockState = null;
	private boolean orientationFixed = false;
	private static final Map<String, String> ID_MIGRATION_MAP = new HashMap<>();
	static {
	    ID_MIGRATION_MAP.put("M1GarandItem", "m1garand");
	    ID_MIGRATION_MAP.put("m_1_garand_weapon", "m1garand");
	    ID_MIGRATION_MAP.put("m1_garand_weapon", "m1garand");
	    ID_MIGRATION_MAP.put("m1garand_weapon", "m1garand");
	    ID_MIGRATION_MAP.put("Gewehr43WeaponItem", "gewehr43");
	    ID_MIGRATION_MAP.put("gewehr_43_weapon", "gewehr43");
	    ID_MIGRATION_MAP.put("gewehr43_weapon", "gewehr43");
	    ID_MIGRATION_MAP.put("Kar98kWeaponItem", "kar98k");
	    ID_MIGRATION_MAP.put("kar_98k_weapon", "kar98k");
	    ID_MIGRATION_MAP.put("kar98k_weapon", "kar98k");
	    ID_MIGRATION_MAP.put("DoubleBarrelWeaponItem", "doublebarrel");
	    ID_MIGRATION_MAP.put("double_barrel_weapon", "doublebarrel");
	    ID_MIGRATION_MAP.put("TrenchGunWeaponItem", "trenchgun");
	    ID_MIGRATION_MAP.put("trench_gun_weapon", "trenchgun");
	    ID_MIGRATION_MAP.put("ThompsonWeaponItem", "thompson");
	    ID_MIGRATION_MAP.put("thompson_weapon", "thompson");
	    ID_MIGRATION_MAP.put("M1911WeaponItem", "m1911");
	    ID_MIGRATION_MAP.put("m_1911_weapon", "m1911");
	    ID_MIGRATION_MAP.put("Starr1858WeaponItem", "starr1858");
	    ID_MIGRATION_MAP.put("starr_1858_weapon", "starr1858");
	    ID_MIGRATION_MAP.put("MosinNagantWeaponItem", "mosinnagant");
	    ID_MIGRATION_MAP.put("mosin_nagant_weapon", "mosinnagant");
	    ID_MIGRATION_MAP.put("SpringfieldWeaponItem", "springfield");
	    ID_MIGRATION_MAP.put("springfield_weapon", "springfield");
	    ID_MIGRATION_MAP.put("ArisakaWeaponItem", "arisaka");
	    ID_MIGRATION_MAP.put("arisaka_weapon", "arisaka");
	    ID_MIGRATION_MAP.put("MauserC96WeaponItem", "mauserc96");
	    ID_MIGRATION_MAP.put("mauser_c_96_weapon", "mauserc96");
	    ID_MIGRATION_MAP.put("MP40WeaponItem", "mp40");
	    ID_MIGRATION_MAP.put("mp_40_weapon", "mp40");
	    ID_MIGRATION_MAP.put("STG44WeaponItem", "stg44");
	    ID_MIGRATION_MAP.put("stg_44_weapon", "stg44");
	    ID_MIGRATION_MAP.put("BARWeaponItem", "bar");
	    ID_MIGRATION_MAP.put("bar_weapon", "bar");
	    ID_MIGRATION_MAP.put("Ak47WeaponItem", "ak47");
	    ID_MIGRATION_MAP.put("ak_47_weapon", "ak47");
	    ID_MIGRATION_MAP.put("AK74uWeaponItem", "ak74u");
	    ID_MIGRATION_MAP.put("ak_74u_weapon", "ak74u");
	    ID_MIGRATION_MAP.put("FAMASWeaponItem", "famas");
	    ID_MIGRATION_MAP.put("famas_weapon", "famas");
	    ID_MIGRATION_MAP.put("ScarHWeaponItem", "scarh");
	    ID_MIGRATION_MAP.put("scar_h_weapon", "scarh");
	    ID_MIGRATION_MAP.put("AUGWeaponItem", "aug");
	    ID_MIGRATION_MAP.put("aug_weapon", "aug");
	    ID_MIGRATION_MAP.put("TAR21WeaponItem", "tar21");
	    ID_MIGRATION_MAP.put("tar_21_weapon", "tar21");
	    ID_MIGRATION_MAP.put("FNFALWeaponItem", "fnfal");
	    ID_MIGRATION_MAP.put("fnfal_weapon", "fnfal");
	    ID_MIGRATION_MAP.put("ACRWeaponItem", "acr");
	    ID_MIGRATION_MAP.put("acr_weapon", "acr");
	    ID_MIGRATION_MAP.put("G36cWeaponItem", "g36c");
	    ID_MIGRATION_MAP.put("g_36c_weapon", "g36c");
	    ID_MIGRATION_MAP.put("M14WeaponItem", "m14");
	    ID_MIGRATION_MAP.put("m_1_4_weapon", "m14");
	    ID_MIGRATION_MAP.put("m_14_weapon", "m14");
	    ID_MIGRATION_MAP.put("M16A4WeaponItem", "m16a4");
	    ID_MIGRATION_MAP.put("m_16_a_4_weapon", "m16a4");
	    ID_MIGRATION_MAP.put("MP5WeaponItem", "mp5");
	    ID_MIGRATION_MAP.put("mp_5_weapon", "mp5");
	    ID_MIGRATION_MAP.put("UMP45WeaponItem", "ump45");
	    ID_MIGRATION_MAP.put("ump_45_weapon", "ump45");
	    ID_MIGRATION_MAP.put("BizonWeaponItem", "bizon");
	    ID_MIGRATION_MAP.put("bizon_weapon", "bizon");
	    ID_MIGRATION_MAP.put("P90WeaponItem", "p90");
	    ID_MIGRATION_MAP.put("p_90_weapon", "p90");
	    ID_MIGRATION_MAP.put("VectorWeaponItem", "vector");
	    ID_MIGRATION_MAP.put("vector_weapon", "vector");
	    ID_MIGRATION_MAP.put("UziWeaponItem", "uzi");
	    ID_MIGRATION_MAP.put("uzi_weapon", "uzi");
	    ID_MIGRATION_MAP.put("MP7WeaponItem", "mp7");
	    ID_MIGRATION_MAP.put("mp_7_weapon", "mp7");
	    ID_MIGRATION_MAP.put("RPDWeaponItem", "rpd");
	    ID_MIGRATION_MAP.put("rpd_weapon", "rpd");
	    ID_MIGRATION_MAP.put("RPKWeaponItem", "rpk");
	    ID_MIGRATION_MAP.put("rpk_weapon", "rpk");
	    ID_MIGRATION_MAP.put("GalilWeaponItem", "galil");
	    ID_MIGRATION_MAP.put("galil_weapon", "galil");
	    ID_MIGRATION_MAP.put("InterventionWeaponItem", "intervention");
	    ID_MIGRATION_MAP.put("intervention_weapon", "intervention");
	    ID_MIGRATION_MAP.put("BarretWeaponItem", "barret");
	    ID_MIGRATION_MAP.put("barret_weapon", "barret");
	    ID_MIGRATION_MAP.put("DragunovWeaponItem", "dragunov");
	    ID_MIGRATION_MAP.put("dragunov_weapon", "dragunov");
	    ID_MIGRATION_MAP.put("M40A3WeaponItem", "m40a3");
	    ID_MIGRATION_MAP.put("m_40_a_3_weapon", "m40a3");
	    ID_MIGRATION_MAP.put("SPAS12WeaponItem", "spas12");
	    ID_MIGRATION_MAP.put("spas_12_weapon", "spas12");
	    ID_MIGRATION_MAP.put("SuperBowItem", "superbow");
	    ID_MIGRATION_MAP.put("super_bow", "superbow");
	    ID_MIGRATION_MAP.put("OldCrossbowWeaponItem", "oldcrossbow");
	    ID_MIGRATION_MAP.put("old_crossbow_weapon", "oldcrossbow");
	    ID_MIGRATION_MAP.put("ChinaLakeWeaponItem", "chinalake");
	    ID_MIGRATION_MAP.put("china_lake_weapon", "chinalake");
	    ID_MIGRATION_MAP.put("RPGWeaponItem", "rpg");
	    ID_MIGRATION_MAP.put("rpg_weapon", "rpg");
	    ID_MIGRATION_MAP.put("MPXWeaponItem", "mpx");
	    ID_MIGRATION_MAP.put("mpx_weapon", "mpx");
	    ID_MIGRATION_MAP.put("CovenantCarbineWeaponItem", "covenantcarbine");
	    ID_MIGRATION_MAP.put("covenant_carbine_weapon", "covenantcarbine");
	    ID_MIGRATION_MAP.put("M7SMGWeaponItem", "m7smg");
	    ID_MIGRATION_MAP.put("m_7_smg_weapon", "m7smg");
	    ID_MIGRATION_MAP.put("MA5DWeaponItem", "ma5d");
	    ID_MIGRATION_MAP.put("ma_5_d_weapon", "ma5d");
	    ID_MIGRATION_MAP.put("BattleRifleWeaponItem", "battlerifle");
	    ID_MIGRATION_MAP.put("battle_rifle_weapon", "battlerifle");
	    ID_MIGRATION_MAP.put("NeedlerWeaponItem", "needler");
	    ID_MIGRATION_MAP.put("needler_weapon", "needler");
	    ID_MIGRATION_MAP.put("PlasmaPistolWeaponItem", "plasmapistol");
	    ID_MIGRATION_MAP.put("plasma_pistol_weapon", "plasmapistol");
	    ID_MIGRATION_MAP.put("OldSwordWeaponItem", "oldsword");
	    ID_MIGRATION_MAP.put("old_sword_weapon", "oldsword");
	    ID_MIGRATION_MAP.put("ThundergunWeaponItem", "thundergun");
	    ID_MIGRATION_MAP.put("thundergun_weapon", "thundergun");
	    ID_MIGRATION_MAP.put("WunderwaffeDG2WeaponItem", "wunderwaffedg2");
	    ID_MIGRATION_MAP.put("wunderwaffe_dg_2_weapon", "wunderwaffedg2");
	    ID_MIGRATION_MAP.put("RaygunWeaponItem", "raygun");
	    ID_MIGRATION_MAP.put("raygun_weapon", "raygun");
	    ID_MIGRATION_MAP.put("RaygunMarkiiItem", "raygunmarkii");
	    ID_MIGRATION_MAP.put("raygun_markii", "raygunmarkii");
	    ID_MIGRATION_MAP.put("FlamethrowerWeaponItem", "flamethrower");
	    ID_MIGRATION_MAP.put("flamethrower_weapon", "flamethrower");
	    ID_MIGRATION_MAP.put("BrowningM1911WeaponItem", "browningm1911");
	    ID_MIGRATION_MAP.put("browning_m_1911_weapon", "browningm1911");
	    ID_MIGRATION_MAP.put("Beretta93rWeaponItem", "beretta93r");
	    ID_MIGRATION_MAP.put("beretta_93r_weapon", "beretta93r");
	    ID_MIGRATION_MAP.put("FiveSevenWeaponItem", "fiveseven");
	    ID_MIGRATION_MAP.put("five_seven_weapon", "fiveseven");
	    ID_MIGRATION_MAP.put("CZScorpionEvo3WeaponItem", "czscorpionevo3");
	    ID_MIGRATION_MAP.put("cz_scorpion_evo_3_weapon", "czscorpionevo3");
	    ID_MIGRATION_MAP.put("L85A2WeaponItem", "l85a2");
	    ID_MIGRATION_MAP.put("l_85_a_2_weapon", "l85a2");
	    ID_MIGRATION_MAP.put("Maschinenpistole28WeaponItem", "maschinenpistole28");
	    ID_MIGRATION_MAP.put("maschinenpistole_28_weapon", "maschinenpistole28");
	    ID_MIGRATION_MAP.put("PercepteurWeaponItem", "percepteur");
	    ID_MIGRATION_MAP.put("percepteur_weapon", "percepteur");
	    ID_MIGRATION_MAP.put("R4CWeaponItem", "r4c");
	    ID_MIGRATION_MAP.put("r_4_c_weapon", "r4c");
	    ID_MIGRATION_MAP.put("VandalWeaponItem", "vandal");
	    ID_MIGRATION_MAP.put("vandal_weapon", "vandal");
	    ID_MIGRATION_MAP.put("WhisperWeaponItem", "whisper");
	    ID_MIGRATION_MAP.put("whisper_weapon", "whisper");
	    ID_MIGRATION_MAP.put("SawWeaponItem", "saw");
	    ID_MIGRATION_MAP.put("saw_weapon", "saw");
	    ID_MIGRATION_MAP.put("StormWeaponItem", "storm");
	    ID_MIGRATION_MAP.put("storm_weapon", "storm");
	    ID_MIGRATION_MAP.put("MG42WeaponItem", "mg42");
	    ID_MIGRATION_MAP.put("mg_42_weapon", "mg42");
	    ID_MIGRATION_MAP.put("Gewehr43WeaponItem", "gewehr43");
	    ID_MIGRATION_MAP.put("gewehr_43_weapon", "gewehr43");
	    ID_MIGRATION_MAP.put("MagnumWeaponItem", "magnum");
	    ID_MIGRATION_MAP.put("magnum_weapon", "magnum");
	    ID_MIGRATION_MAP.put("USP45WeaponItem", "usp45");
	    ID_MIGRATION_MAP.put("usp_45_weapon", "usp45");
	    ID_MIGRATION_MAP.put("Arc12WeaponItem", "arc12");
	    ID_MIGRATION_MAP.put("arc_12_weapon", "arc12");
	    ID_MIGRATION_MAP.put("HydraWeaponItem", "hydra");
	    ID_MIGRATION_MAP.put("hydra_weapon", "hydra");
	    ID_MIGRATION_MAP.put("DMRWeaponItem", "dmr");
	    ID_MIGRATION_MAP.put("dmr_weapon", "dmr");
	}
	public BuyWallWeaponBlockEntity(BlockPos position, BlockState state) {
	    super(ZombieroolModBlockEntities.BUY_WALL_WEAPON.get(), position, state);
	}
	public void tick(Level level, BlockPos pos, BlockState state) {
	    if (level.isClientSide) return;
	    if (!orientationFixed) {
	        fixOrientation(level, pos, state);
	        orientationFixed = true;
	    }
	}
	private void fixOrientation(Level level, BlockPos pos, BlockState state) {
	    if (!(state.getBlock() instanceof BuyWallWeaponBlock)) return;
	    Direction currentFacing = state.getValue(BuyWallWeaponBlock.FACING);
	    if (isValidOrientation(level, pos, currentFacing)) {
	        return;
	    }
	    for (Direction dir : Direction.Plane.HORIZONTAL) {
	        if (dir == currentFacing) continue;
	        if (isValidOrientation(level, pos, dir)) {
	            level.setBlock(pos, state.setValue(BuyWallWeaponBlock.FACING, dir), 3);
	            return;
	        }
	    }
	}
	private boolean isValidOrientation(Level level, BlockPos pos, Direction facing) {
	    BlockPos front = pos.relative(facing);
	    BlockPos belowFront = front.below();
	    BlockPos aboveFront = front.above();
	    boolean isAirFront = level.getBlockState(front).isAir();
	    boolean isPathBelow = level.getBlockState(belowFront).is(ZombieroolModBlocks.PATH.get());
	    boolean isPathAbove = level.getBlockState(aboveFront).is(ZombieroolModBlocks.PATH.get());
	    return isAirFront && (isPathBelow || isPathAbove);
	}
	@Override
	public void onLoad() {
	    super.onLoad();
	    if (this.level != null && !this.level.isClientSide) {
	         this.orientationFixed = false; 
	    }
	}
	@Override
	public void load(CompoundTag nbt) {
	    super.load(nbt);
	    this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
	    ContainerHelper.loadAllItems(nbt, this.stacks);
	    this.price = nbt.getInt("Price");
	    if (nbt.contains("OrientationFixed")) {
	        this.orientationFixed = nbt.getBoolean("OrientationFixed");
	    }
	    if (nbt.contains("ItemToSell", Tag.TAG_STRING)) {
	        String fullId = nbt.getString("ItemToSell");
	        this.itemToSell = ResourceLocation.tryParse(fullId); 
	        if (this.itemToSell != null && ForgeRegistries.ITEMS.containsKey(this.itemToSell)) {
	             this.stacks.set(0, new ItemStack(ForgeRegistries.ITEMS.getValue(this.itemToSell)));
	        }
	    }
	    this.mimicBlockState = MimicSystem.loadMimic(nbt, this.level, "CapturedBlock", true);
	    if (this.itemToSell != null) {
	        ItemStack currentStack = this.stacks.get(0);
	        Item regItem = ForgeRegistries.ITEMS.getValue(this.itemToSell);
	        if (regItem != null && regItem != Items.AIR) {
	            if (currentStack.isEmpty() || currentStack.getItem() != regItem) {
	                 this.stacks.set(0, new ItemStack(regItem));
	                 this.setChanged();
	            }
	        }
	    }
	}
	@Override
	public void saveAdditional(CompoundTag nbt) {
	    super.saveAdditional(nbt);
	    ContainerHelper.saveAllItems(nbt, this.stacks);
	    nbt.putInt("Price", this.price);
	    nbt.putBoolean("OrientationFixed", this.orientationFixed);
	    if (this.itemToSell != null) {
	        nbt.putString("ItemToSell", this.itemToSell.toString());
	    }
	    MimicSystem.saveMimic(nbt, this.mimicBlockState);
	}
	public int getPrice() { return price; }
	public void setPrice(int price) { this.price = price; setChanged(); }
	public ResourceLocation getItemToSell() { return itemToSell; }
	public void setItemToSell(ResourceLocation itemToSell) { 
	    this.itemToSell = itemToSell; 
	    if (itemToSell != null) {
	        Item item = ForgeRegistries.ITEMS.getValue(itemToSell);
	        if (item != null && item != Items.AIR) {
	            this.stacks.set(0, new ItemStack(item));
	        }
	    }
	    setChanged(); 
	}
	@Override
	public BlockState getMimic() {
	    return mimicBlockState;
	}
	@Override
	public void setMimic(BlockState state) {
	    this.mimicBlockState = state;
	    setChanged();
	}
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
	    return ClientboundBlockEntityDataPacket.create(this);
	}
	@Override
	public CompoundTag getUpdateTag() {
	    return this.saveWithFullMetadata();
	}
	@Override
	public int getContainerSize() {
	    return stacks.size();
	}
	@Override
	public boolean isEmpty() {
	    for (ItemStack itemstack : this.stacks)
	        if (!itemstack.isEmpty())
	            return false;
	    return true;
	}
	@Override
	public Component getDefaultName() {
	    return Component.literal("buy_wall_weapon");
	}
	@Override
	public int getMaxStackSize() {
	    return 64;
	}
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory) {
	    return new WallWeaponManagerMenu(id, inventory, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition));
	}
	@Override
	public Component getDisplayName() {
	    return Component.literal("Wall Weapon");
	}
	@Override
	protected NonNullList<ItemStack> getItems() {
	    return this.stacks;
	}
	@Override
	protected void setItems(NonNullList<ItemStack> stacks) {
	    this.stacks = stacks;
	}
	@Override
	public boolean canPlaceItem(int index, ItemStack stack) {
	    return true;
	}
	@Override
	public int[] getSlotsForFace(Direction side) {
	    return IntStream.range(0, this.getContainerSize()).toArray();
	}
	@Override
	public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
	    return this.canPlaceItem(index, stack);
	}
	@Override
	public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
	    return index != 0;
	}
	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
	    if (!this.remove && facing != null && capability == ForgeCapabilities.ITEM_HANDLER)
	        return handlers[facing.ordinal()].cast();
	    return super.getCapability(capability, facing);
	}
	@Override
	public void setRemoved() {
	    super.setRemoved();
	    for (LazyOptional<? extends IItemHandler> handler : handlers)
	        handler.invalidate();
	}
}