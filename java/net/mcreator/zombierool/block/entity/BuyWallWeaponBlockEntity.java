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
import net.minecraft.nbt.Tag; // <-- import de Tag pour les constants NBT
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import net.minecraft.resources.ResourceLocation;

import net.mcreator.zombierool.world.inventory.WallWeaponManagerMenu;
import net.mcreator.zombierool.init.ZombieroolModBlockEntities;

import javax.annotation.Nullable;

import java.util.stream.IntStream;

import io.netty.buffer.Unpooled;

public class BuyWallWeaponBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {
    private NonNullList<ItemStack> stacks = NonNullList.withSize(1, ItemStack.EMPTY);
    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());
    private int price = 0;
    private ResourceLocation itemToSell = null;
    private ResourceLocation capturedBlock = null;

    public BuyWallWeaponBlockEntity(BlockPos position, BlockState state) {
        super(ZombieroolModBlockEntities.BUY_WALL_WEAPON.get(), position, state);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        // Inventaire interne
        this.stacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.stacks);

        // Prix
        this.price = nbt.getInt("Price");

        // ItemToSell et CapturedBlock
        if (nbt.contains("ItemToSell", Tag.TAG_STRING)) {
            this.itemToSell = new ResourceLocation(nbt.getString("ItemToSell"));
        }
        if (nbt.contains("CapturedBlock", Tag.TAG_STRING)) {
            this.capturedBlock = new ResourceLocation(nbt.getString("CapturedBlock"));
        }
    }

    @Override
    public void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        ContainerHelper.saveAllItems(nbt, this.stacks);

        nbt.putInt("Price", this.price);
        if (this.itemToSell != null) {
            nbt.putString("ItemToSell", this.itemToSell.toString());
        }
        if (this.capturedBlock != null) {
            nbt.putString("CapturedBlock", this.capturedBlock.toString());
        }
    }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; setChanged(); }

    public ResourceLocation getItemToSell() { return itemToSell; }
    public void setItemToSell(ResourceLocation itemToSell) { this.itemToSell = itemToSell; setChanged(); }

    public ResourceLocation getCapturedBlock() { return capturedBlock; }
    public void setCapturedBlock(ResourceLocation capturedBlock) { this.capturedBlock = capturedBlock; setChanged(); }

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
