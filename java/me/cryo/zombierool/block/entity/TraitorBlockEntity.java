package me.cryo.zombierool.block.entity;

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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.world.phys.AABB;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.block.system.MimicSystem;

import javax.annotation.Nullable;
import java.util.stream.IntStream;

public class TraitorBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer, MimicSystem.IMimicContainer {

    private BlockState mimicBlockState = null;
    private NonNullList<ItemStack> stacks = NonNullList.<ItemStack>withSize(9, ItemStack.EMPTY);
    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    public TraitorBlockEntity(BlockPos position, BlockState state) {
        super(ZombieroolModBlockEntities.TRAITOR.get(), position, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TraitorBlockEntity blockEntity) {
        if (!level.isClientSide) {
            // Check for nearby zombies
            for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 2, 1))) {
                if (level.getBlockEntity(nearby) != null && !nearby.equals(pos)) continue; 
                
                if (!level.getEntitiesOfClass(ZombieEntity.class, new AABB(nearby)).isEmpty()) {
                    // Play break sound and particles
                    level.levelEvent(2001, pos, Block.getId(state));
                    // Replace Traitor Block with Path Block
                    level.setBlock(pos, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
                    break;
                }
            }
        }
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

    public @Nullable Block getCopiedBlock() {
        if (mimicBlockState == null)
            return null;
        return mimicBlockState.getBlock();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.mimicBlockState = MimicSystem.loadMimic(tag, this.level, "CopiedBlock", true);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        MimicSystem.saveMimic(tag, this.mimicBlockState);
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