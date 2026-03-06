package me.cryo.zombierool.block.entity;

import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import me.cryo.zombierool.world.inventory.ObstacleDoorManagerMenu;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;
import net.minecraft.world.level.Level;
import java.util.Set;
import javax.annotation.Nullable;
import java.util.stream.IntStream;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.block.ObstacleDoorBlock;
import me.cryo.zombierool.block.system.MimicSystem;

public class ObstacleDoorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, MimicSystem.IMimicContainer {
	private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
	private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());
	private BlockState mimicBlockState = null;
	private int prix = 0;
	private String canal = "";
	public ObstacleDoorBlockEntity(BlockPos position, BlockState state) {
		super(ZombieroolModBlockEntities.OBSTACLE_DOOR.get(), position, state);
	}
	
	public int getPrix() { return this.prix; }
	public void setPrix(int prix) { this.prix = prix; }
	public String getCanal() { return this.canal; }
	public void setCanal(String canal) { this.canal = canal; }
	
	@Override
	public BlockState getMimic() {
	    return mimicBlockState;
	}
	
	@Override
	public void setMimic(@Nullable BlockState state) {
	    this.mimicBlockState = state;
	    if (this.level != null && this.worldPosition != null) {
	        BlockState currentState = this.level.getBlockState(this.worldPosition);
	        if (currentState.hasProperty(ObstacleDoorBlock.HAS_COPIED_BLOCK)) {
	            boolean hasMimic = (state != null);
	            if (currentState.getValue(ObstacleDoorBlock.HAS_COPIED_BLOCK) != hasMimic) {
	                this.level.setBlock(this.worldPosition, currentState.setValue(ObstacleDoorBlock.HAS_COPIED_BLOCK, hasMimic), 3);
	            }
	        }
	    }
	    setChanged();
	}
	
	@Nullable
	public Block getCopiedBlock() {
	    if (mimicBlockState == null) return null;
	    return mimicBlockState.getBlock();
	}
	
	public void updateGroupParameters(int newPrix, String newCanal) {
	    Set<BlockPos> group = new HashSet<>();
	    findAllConnectedBlocks(this.level, this.worldPosition, group);
	    for (BlockPos pos : group) {
	        if (level.getBlockEntity(pos) instanceof ObstacleDoorBlockEntity be) {
	            be.setPrix(newPrix);
	            be.setCanal(newCanal);
	            be.setChanged();
	        }
	    }
	}
	
	public int getCanalAsInt() {
	    try {
	        return Integer.parseInt(this.canal);
	    } catch (NumberFormatException e) {
	        return 0;
	    }
	}
	
	private static void findAllConnectedBlocks(Level level, BlockPos pos, Set<BlockPos> result) {
	    if (result.contains(pos) || !isValidBlock(level, pos)) return;
	    result.add(pos);
	    for (Direction dir : Direction.values()) {
	        findAllConnectedBlocks(level, pos.relative(dir), result);
	    }
	}
	
	private static boolean isValidBlock(Level level, BlockPos pos) {
	    return level.getBlockState(pos).getBlock() instanceof me.cryo.zombierool.block.ObstacleDoorBlock;
	}
	
	@Override
	public void load(CompoundTag compound) {
	    super.load(compound);
	    this.prix = compound.getInt("Prix");
	    this.canal = compound.getString("Canal");
	    this.mimicBlockState = MimicSystem.loadMimic(compound, this.level, "CopiedBlockId", true);
	}
	
	@Override
	protected void saveAdditional(CompoundTag compound) {
	    super.saveAdditional(compound);
	    compound.putInt("Prix", this.prix);
	    compound.putString("Canal", this.canal);
	    MimicSystem.saveMimic(compound, this.mimicBlockState);
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
		return Component.literal("obstacle_door");
	}
	
	@Override
	public int getMaxStackSize() {
		return 64;
	}
	
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new ObstacleDoorManagerMenu(id, inventory, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition));
	}
	
	@Override
	public Component getDisplayName() {
		return Component.literal("Obstacle Door");
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
		return true;
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
	
	@Override
	public void setChanged() {
	    super.setChanged();
	    if (this.level != null) {
	        this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
	    }
	}
	
	@Override
	public void handleUpdateTag(CompoundTag tag) {
	    super.handleUpdateTag(tag);
	    this.prix = tag.getInt("Prix");
	    this.canal = tag.getString("Canal");
	    this.mimicBlockState = MimicSystem.loadMimic(tag, this.level, "CopiedBlockId", true);
	}
}