package me.cryo.zombierool.block.entity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;
import me.cryo.zombierool.WorldConfig;

public class MysteryBoxBlockEntity extends BlockEntity {
	private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
	public MysteryBoxBlockEntity(BlockPos pos, BlockState state) {
	    super(ZombieroolModBlockEntities.MYSTERY_BOX.get(), pos, state);
	}
	
	@Override
	public void onLoad() {
	    super.onLoad();
	    if (this.level != null && !this.level.isClientSide && this.level instanceof ServerLevel serverLevel) {
	        WorldConfig config = WorldConfig.get(serverLevel);
	        config.addMysteryBoxPosition(this.worldPosition);
	    }
	}
	
	public static void clientTick(Level pLevel, BlockPos pPos, BlockState pState, MysteryBoxBlockEntity pBlockEntity) {
	}
	
	@Override
	public void load(CompoundTag pTag) {
	    super.load(pTag);
	}
	
	@Override
	protected void saveAdditional(CompoundTag pTag) {
	    super.saveAdditional(pTag);
	}
	
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
	    return ClientboundBlockEntityDataPacket.create(this);
	}
	
	@Override
	public CompoundTag getUpdateTag() {
	    return saveWithoutMetadata();
	}
}