package net.mcreator.zombierool.world.inventory;

import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;

import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.mcreator.zombierool.init.ZombieroolModMenus;

import java.util.function.Supplier;
import java.util.Map;
import java.util.HashMap;

public class WallWeaponManagerMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
    public final static HashMap<String, Object> guistate = new HashMap<>();
    public final Level world;
    public final Player entity;
    public int x, y, z;
    private ContainerLevelAccess access = ContainerLevelAccess.NULL;
    private IItemHandler internal;
    private final Map<Integer, Slot> customSlots = new HashMap<>();
    private boolean bound = false;
    private Supplier<Boolean> boundItemMatcher = null;
    private Entity boundEntity = null;
    private BlockEntity boundBlockEntity = null;
    private final ContainerData data;

    public WallWeaponManagerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
	    super(ZombieroolModMenus.WALL_WEAPON_MANAGER.get(), id);
	    this.entity = inv.player;
	    this.world = inv.player.level();
	
	    // Au départ, un handler vide d’un slot
	    this.internal = new ItemStackHandler(1);
	
	    // Slot de synchronisation du prix
	    this.data = new ContainerData() {
	        @Override public int get(int index) {
	            if (boundBlockEntity instanceof BuyWallWeaponBlockEntity be) {
	                return be.getPrice();
	            }
	            return 0;
	        }
	        @Override public void set(int index, int value) {
	            if (boundBlockEntity instanceof BuyWallWeaponBlockEntity be) {
	                be.setPrice(value);
	            }
	        }
	        @Override public int getCount() { return 1; }
	    };
	    this.addDataSlots(data);
	
	    // Lecture de la position
	    BlockPos pos = null;
	    if (extraData != null) {
	        pos = extraData.readBlockPos();
	        this.x = pos.getX();
	        this.y = pos.getY();
	        this.z = pos.getZ();
	        access = ContainerLevelAccess.create(world, pos);
	    }
	
	    // ——— BIND au BlockEntity et à sa capability ———
	    if (pos != null) {
	        boundBlockEntity = world.getBlockEntity(pos);
	        if (boundBlockEntity != null) {
	            boundBlockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(cap -> {
	                this.internal = cap;
	                this.bound = true;
	            });
	        }
	    }
	
	    // Copier l’item déjà stocké dans le BE (slot 0) vers internal
	    if (boundBlockEntity instanceof BuyWallWeaponBlockEntity) {
	        ItemStack stored = internal.getStackInSlot(0); 
	        if (!stored.isEmpty() && internal instanceof ItemStackHandler handler) {
	            handler.setStackInSlot(0, stored.copy());
	        }
	    }
	
	    // ——— Ajout des slots GUI ———
	    // Slot de configuration (slot 0 du handler)
	    this.customSlots.put(0, this.addSlot(new SlotItemHandler(internal, 0, 5, 23)));
	
	    // Inventaire joueur (3 lignes × 9 colonnes)
	    for (int si = 0; si < 3; ++si)
	        for (int sj = 0; sj < 9; ++sj)
	            this.addSlot(new Slot(inv, sj + (si + 1) * 9, 8 + sj * 18, 84 + si * 18));
	
	    // Hotbar (9 slots)
	    for (int si = 0; si < 9; ++si)
	        this.addSlot(new Slot(inv, si, 8 + si * 18, 142));
	}


    /** Récupère le prix synchronisé depuis le serveur */
    public int getConfiguredPrice() {
        return this.data.get(0);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.bound) {
            if (this.boundItemMatcher != null) {
                return this.boundItemMatcher.get();
            } else if (this.boundBlockEntity != null) {
                return stillValid(this.access, player, this.boundBlockEntity.getBlockState().getBlock());
            } else if (this.boundEntity != null) {
                return this.boundEntity.isAlive();
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();
            if (index < 1) {
                if (!this.moveItemStackTo(stackInSlot, 1, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, result);
            } else if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) {
                if (index < 1 + 27) {
                    if (!this.moveItemStackTo(stackInSlot, 1 + 27, this.slots.size(), true)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.moveItemStackTo(stackInSlot, 1, 1 + 27, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (stackInSlot.getCount() == 0) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stackInSlot.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(playerIn, stackInSlot);
        }
        return result;
    }

    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }

    @Override
    public void removed(Player playerIn) {
        super.removed(playerIn);
        if (!bound && playerIn instanceof ServerPlayer) {
            for (int j = 1; j < internal.getSlots(); ++j) {
                ItemStack extracted = internal.extractItem(j, internal.getStackInSlot(j).getCount(), false);
                if (!extracted.isEmpty()) {
                    playerIn.drop(extracted, false);
                }
            }
        }
    }

    @Override
    public Map<Integer, Slot> get() {
        return customSlots;
    }
}
