package net.mcreator.zombierool.block.entity;

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
import net.minecraft.world.ContainerHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.mcreator.zombierool.world.inventory.ObstacleDoorManagerMenu;
import net.mcreator.zombierool.init.ZombieroolModBlockEntities;

import net.minecraft.world.level.Level;
import java.util.Set;
import javax.annotation.Nullable;
import java.util.stream.IntStream;
import io.netty.buffer.Unpooled;

import java.util.HashSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.mcreator.zombierool.block.ObstacleDoorBlock; // Import ajouté pour la propriété du bloc

public class ObstacleDoorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
	private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
	private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    private ResourceLocation copiedBlockId = null;

	public ObstacleDoorBlockEntity(BlockPos position, BlockState state) {
		super(ZombieroolModBlockEntities.OBSTACLE_DOOR.get(), position, state);
	}

	private int prix = 0;
    private String canal = "";

    public int getPrix() { return this.prix; }
    public void setPrix(int prix) { this.prix = prix; }

    public String getCanal() { return this.canal; }
    public void setCanal(String canal) { this.canal = canal; }

    // MODIFIÉ: Met à jour la propriété du bloc lors de la copie
    public void setCopiedBlock(Block block) {
        this.copiedBlockId = ForgeRegistries.BLOCKS.getKey(block);
        //System.out.println("DEBUG: ObstacleDoorBlockEntity.setCopiedBlock() - copiedBlockId défini à : " + (this.copiedBlockId != null ? this.copiedBlockId.toString() : "null")); 
        
        if (this.level != null && this.worldPosition != null) {
            BlockState currentState = this.level.getBlockState(this.worldPosition);
            if (currentState.hasProperty(ObstacleDoorBlock.HAS_COPIED_BLOCK)) {
                // S'assure que la propriété HAS_COPIED_BLOCK est mise à jour
                this.level.setBlock(this.worldPosition, currentState.setValue(ObstacleDoorBlock.HAS_COPIED_BLOCK, true), 3);
                //System.out.println("DEBUG: ObstacleDoorBlockEntity.setCopiedBlock() - HAS_COPIED_BLOCK réglé sur true.");
            }
        }
        setChanged(); // Appeler setChanged() ici pour marquer la nécessité de mise à jour
    }

    public @Nullable Block getCopiedBlock() {
        if (copiedBlockId == null) {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(copiedBlockId);
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
	    return Integer.parseInt(this.canal);
	}

	private static void findAllConnectedBlocks(Level level, BlockPos pos, Set<BlockPos> result) {
	    if (result.contains(pos) || !isValidBlock(level, pos)) return;
	    
	    result.add(pos);
	    for (Direction dir : Direction.values()) {
	        findAllConnectedBlocks(level, pos.relative(dir), result);
	    }
	}

	private static boolean isValidBlock(Level level, BlockPos pos) {
	    return level.getBlockState(pos).getBlock() instanceof net.mcreator.zombierool.block.ObstacleDoorBlock;
	}

   @Override
	public void load(CompoundTag compound) {
	    super.load(compound);
	    this.prix = compound.getInt("Prix");
	    this.canal = compound.getString("Canal");
        if (compound.contains("CopiedBlockId")) {
            this.copiedBlockId = new ResourceLocation(compound.getString("CopiedBlockId"));
            //System.out.println("DEBUG: ObstacleDoorBlockEntity.load() - copiedBlockId chargé : " + this.copiedBlockId.toString()); 
        } else { 
            this.copiedBlockId = null;
            //System.out.println("DEBUG: ObstacleDoorBlockEntity.load() - copiedBlockId non trouvé, réinitialisé à null.");
        }
	}
	
    @Override
	protected void saveAdditional(CompoundTag compound) {
	    super.saveAdditional(compound);
	    compound.putInt("Prix", this.prix);
	    compound.putString("Canal", this.canal);
        if (this.copiedBlockId != null) {
            compound.putString("CopiedBlockId", this.copiedBlockId.toString());
            //System.out.println("DEBUG: ObstacleDoorBlockEntity.saveAdditional() - copiedBlockId sauvegardé : " + this.copiedBlockId.toString()); 
        } else {
            //System.out.println("DEBUG: ObstacleDoorBlockEntity.saveAdditional() - copiedBlockId est null, non sauvegardé."); 
        }
	}
	
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag() {
        //System.out.println("DEBUG: ObstacleDoorBlockEntity.getUpdateTag() appelé."); 
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
            //System.out.println("DEBUG: ObstacleDoorBlockEntity.setChanged() - sendBlockUpdated appelé."); 
	    }
	}
		
	@Override
	public void handleUpdateTag(CompoundTag tag) {
	    super.handleUpdateTag(tag);
	    this.prix = tag.getInt("Prix");
	    this.canal = tag.getString("Canal");
        if (tag.contains("CopiedBlockId")) {
            this.copiedBlockId = new ResourceLocation(tag.getString("CopiedBlockId"));
           // System.out.println("DEBUG: ObstacleDoorBlockEntity.handleUpdateTag() - copiedBlockId reçu : " + this.copiedBlockId.toString()); 
        } else {
            this.copiedBlockId = null; 
           // System.out.println("DEBUG: ObstacleDoorBlockEntity.handleUpdateTag() - copiedBlockId non trouvé dans le tag de mise à jour, réinitialisé à null."); 
        }
	}
}