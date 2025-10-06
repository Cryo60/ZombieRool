package net.mcreator.zombierool.block.entity;

import io.netty.buffer.Unpooled;

import net.mcreator.zombierool.init.ZombieroolModBlockEntities;
import net.mcreator.zombierool.world.inventory.PerksInterfaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.IntStream;

import net.mcreator.zombierool.block.PerksLowerBlock; // <-- NOUVEL IMPORT

public class PerksLowerBlockEntity extends RandomizableContainerBlockEntity implements WorldlyContainer {

    private NonNullList<ItemStack> stacks = NonNullList.withSize(0, ItemStack.EMPTY);
    private final LazyOptional<? extends IItemHandler>[] handlers = SidedInvWrapper.create(this, Direction.values());
    private int savedPrice = 0;
    private String savedPerkId = "";
    private final Map<Player, Boolean> localTouchMap = new HashMap<>();

    public PerksLowerBlockEntity(BlockPos position, BlockState state) {
        super(ZombieroolModBlockEntities.PERKS_LOWER.get(), position, state);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("savedPrice", savedPrice);
        tag.putString("savedPerkId", savedPerkId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.savedPrice = tag.getInt("savedPrice");
        this.savedPerkId = tag.getString("savedPerkId");
    }

    public void setSavedPrice(int p) {
        this.savedPrice = p;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void setSavedPerkId(String id) {
        this.savedPerkId = id;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public int getSavedPrice() {
        return savedPrice;
    }

    public String getSavedPerkId() {
        return savedPerkId;
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
        for (ItemStack itemstack : this.stacks) {
            if (!itemstack.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.stacks.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack itemstack = ContainerHelper.removeItem(this.stacks, index, count);
        if (!itemstack.isEmpty())
            this.setChanged();
        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.stacks, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.stacks.set(index, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public void clearContent() {
        this.stacks.clear();
        this.setChanged();
    }

    @Override
    public Component getDefaultName() {
        return Component.literal("perks_lower");
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(this.worldPosition);
        buf.writeInt(this.savedPrice);
        buf.writeUtf(this.savedPerkId);
        return new PerksInterfaceMenu(id, inventory, buf);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Perks Lower");
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
        if (!this.remove && facing != null && capability == ForgeCapabilities.ITEM_HANDLER) {
            return handlers[facing.ordinal()].cast();
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        for (LazyOptional<? extends IItemHandler> handler : handlers) {
            handler.invalidate();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void clientTick(Level world, BlockPos pos, BlockState state, PerksLowerBlockEntity blockEntity) {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;

        // Vérifie si le bloc est alimenté via sa propre propriété POWERED
        if (state.hasProperty(PerksLowerBlock.POWERED) && state.getValue(PerksLowerBlock.POWERED)) {
            double distanceSq = player.position().distanceToSqr(pos.getCenter());
            if (distanceSq < 16) {
                world.playLocalSound(pos.getX(), pos.getY(), pos.getZ(),
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_ambiant")),
                        SoundSource.BLOCKS, 0.1f, 1f, false);
            }
        }

        AABB blockBox = new AABB(pos);
        boolean wasTouching = blockEntity.localTouchMap.getOrDefault(player, false);
        boolean touching = blockBox.inflate(0.1).intersects(player.getBoundingBox());

        if (touching && !wasTouching) {
            blockEntity.localTouchMap.put(player, true);
            world.playLocalSound(pos.getX(), pos.getY(), pos.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_collision")),
                    SoundSource.BLOCKS, 1f, 1f, false);
        } else if (!touching && wasTouching) {
            blockEntity.localTouchMap.put(player, false);
        }
    }
}
