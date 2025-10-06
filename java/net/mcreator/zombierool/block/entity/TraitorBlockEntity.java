package net.mcreator.zombierool.block.entity;

import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.Level;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.minecraft.world.phys.AABB;

import net.mcreator.zombierool.init.ZombieroolModBlockEntities;

import javax.annotation.Nullable;

import java.util.stream.IntStream;

public class TraitorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
	private ResourceLocation copiedBlock = null;
	private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
	private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

	public TraitorBlockEntity(BlockPos position, BlockState state) {
		super(ZombieroolModBlockEntities.TRAITOR.get(), position, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TraitorBlockEntity blockEntity) {
        if (!level.isClientSide) {
            for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
                if (level.getBlockEntity(nearby) != null) continue; // ignore other BlockEntities

                if (!level.getEntitiesOfClass(ZombieEntity.class, new AABB(nearby)).isEmpty()) {
                    level.destroyBlock(pos, false); // détruit ce bloc
                    break;
                }
            }
        }
    }

	public void setCopiedBlock(Block block) {
	    this.copiedBlock = ForgeRegistries.BLOCKS.getKey(block);
	}
	
	public @Nullable Block getCopiedBlock() {
	    if (copiedBlock == null)
	        return null;
	    return ForgeRegistries.BLOCKS.getValue(copiedBlock);
	}

	@Override
	public void load(CompoundTag tag) {
	    super.load(tag);
	    if (tag.contains("CopiedBlock")) {
	        copiedBlock = new ResourceLocation(tag.getString("CopiedBlock"));
	    }
	}
		
	@Override
	public void saveAdditional(CompoundTag tag) {
	    super.saveAdditional(tag);
	    if (copiedBlock != null) {
	        tag.putString("CopiedBlock", copiedBlock.toString());
	    }
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
		return Component.literal("traitor");
	}

	@Override
	public int getMaxStackSize() {
		return 64;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return ChestMenu.threeRows(id, inventory);
	}

	@Override
	public Component getDisplayName() {
		return Component.literal("Traitor");
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
}
