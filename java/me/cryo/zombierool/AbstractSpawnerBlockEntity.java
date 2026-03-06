package me.cryo.zombierool.block.entity;

import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.FriendlyByteBuf;
import me.cryo.zombierool.world.inventory.SpawnerManagerMenu;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import javax.annotation.Nullable;
import java.util.stream.IntStream;
import io.netty.buffer.Unpooled;

public abstract class AbstractSpawnerBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    protected int canal = 0;
    protected boolean active = false;
    protected static final String CANAL_TAG = "SpawnerCanal";
    protected static final String ACTIVE_TAG = "SpawnerActive";
    
    private NonNullList<ItemStack> stacks = NonNullList.withSize(9, ItemStack.EMPTY);
    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    public AbstractSpawnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getCanal() {
        return this.canal;
    }

    public void setCanal(int newCanal) {
        int old = this.canal;
        this.canal = newCanal;
        if (this.level != null && !this.level.isClientSide) {
            SpawnerRegistry.unregisterSpawner(this.level, old, this);
            SpawnerRegistry.registerSpawner(this.level, this.canal, this);
        }
        if (this.canal == 0) this.setActive(true);
        else this.setActive(false);
        this.setChanged();
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean flag) {
        if (this.level != null && !this.level.isClientSide()) {
            if (flag && !this.active) {
                SpawnerRegistry.registerSpawner(this.level, this.canal, this);
            } else if (!flag && this.active) {
                SpawnerRegistry.unregisterSpawner(this.level, this.canal, this);
            }
        }
        this.active = flag;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(CANAL_TAG, this.canal);
        tag.putBoolean(ACTIVE_TAG, this.active);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.canal = tag.getInt(CANAL_TAG);
        this.active = tag.getBoolean(ACTIVE_TAG);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide()) {
            SpawnerRegistry.registerSpawner(this.level, this.canal, this);
            if (this.canal == 0) {
                this.setActive(true);
            } else {
                this.setActive(this.active);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (LazyOptional<? extends IItemHandler> h : handlers) h.invalidate();
        if (this.level != null && !this.level.isClientSide()) {
            SpawnerRegistry.unregisterSpawner(this.level, this.canal, this);
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

    @Override public int getContainerSize() { return stacks.size(); }
    @Override public boolean isEmpty() { return stacks.stream().allMatch(ItemStack::isEmpty); }
    @Override public int getMaxStackSize() { return 64; }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new SpawnerManagerMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition));
    }

    @Override protected NonNullList<ItemStack> getItems() { return this.stacks; }
    @Override protected void setItems(NonNullList<ItemStack> stacks) { this.stacks = stacks; }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return true; }
    @Override public int[] getSlotsForFace(Direction side) { return IntStream.range(0, this.getContainerSize()).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int i, ItemStack s, @Nullable Direction d) { return true; }
    @Override public boolean canTakeItemThroughFace(int i, ItemStack s, Direction d) { return true; }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!this.remove && side != null && cap == ForgeCapabilities.ITEM_HANDLER) {
            return handlers[side.ordinal()].cast();
        }
        return super.getCapability(cap, side);
    }
}