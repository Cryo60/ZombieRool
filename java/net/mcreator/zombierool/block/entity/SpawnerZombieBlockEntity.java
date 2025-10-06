// Dans net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity.java

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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;

import net.mcreator.zombierool.world.inventory.SpawnerManagerMenu;
import net.mcreator.zombierool.init.ZombieroolModBlockEntities;
import net.mcreator.zombierool.spawner.SpawnerRegistry;

import javax.annotation.Nullable;
import java.util.stream.IntStream;

import io.netty.buffer.Unpooled;
import net.minecraft.world.level.Level; // NOUVEL IMPORT

/**
 * SpawnerZombieBlockEntity with persistent canal and forced re‑activation on canal 0.
 */
public class SpawnerZombieBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    
    private int canal = 0;
    private boolean active = false;

    private static final String CANAL_TAG = "SpawnerCanal";
    private static final String ACTIVE_TAG = "SpawnerActive";

    private NonNullList<ItemStack> stacks = NonNullList.withSize(9, ItemStack.EMPTY);
    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());

    public SpawnerZombieBlockEntity(BlockPos pos, BlockState state) {
        super(ZombieroolModBlockEntities.SPAWNER_ZOMBIE.get(), pos, state);
        this.canal = 0;
        this.active = false;
    }

    public int getCanal() {
        return this.canal;
    }

    public void setCanal(int newCanal) {
        int old = this.canal;
        this.canal = newCanal;

        // MODIFICATION ICI : Passer 'this.level'
        if (this.level != null) {
            SpawnerRegistry.unregisterSpawner(this.level, old, this);
            SpawnerRegistry.registerSpawner(this.level, this.canal, this);
        }

        // activation logic
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
                // MODIFICATION ICI : Passer 'this.level'
                SpawnerRegistry.registerSpawner(this.level, this.canal, this);
            } else if (!flag && this.active) {
                // MODIFICATION ICI : Passer 'this.level'
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
        System.out.println("[save] canal=" + this.canal + ", active=" + this.active);
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
            // MODIFICATION ICI : Passer 'this.level'
            SpawnerRegistry.registerSpawner(this.level, this.canal, this);
    
            if (this.canal == 0) {
                this.setActive(true);
            } else {
                this.setActive(this.active);
            }
    
            System.out.println("[onLoad] Spawner chargé: pos=" + this.getBlockPos() + ", canal=" + this.canal + ", active=" + this.active);
        }
    }


    @Override
    public void setRemoved() {
        super.setRemoved();
        for (LazyOptional<? extends IItemHandler> h : handlers) h.invalidate();
        if (this.level != null && !this.level.isClientSide()) {
            // MODIFICATION ICI : Passer 'this.level'
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

    // --- inventory ---

    @Override public int getContainerSize() { return stacks.size(); }
    @Override public boolean isEmpty() { return stacks.stream().allMatch(ItemStack::isEmpty); }
    @Override public int getMaxStackSize() { return 64; }
    @Override public Component getDefaultName() { return Component.literal("spawner_zombie"); }
    @Override public Component getDisplayName() { return Component.literal("Spawner Zombie"); }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new SpawnerManagerMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(this.worldPosition));
    }

    @Override protected NonNullList<ItemStack> getItems() { return this.stacks; }
    @Override protected void setItems(NonNullList<ItemStack> stacks) { this.stacks = stacks; }
    @Override public boolean canPlaceItem(int i, ItemStack s) { return true; }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return IntStream.range(0, this.getContainerSize()).toArray();
    }

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