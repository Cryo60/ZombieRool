package me.cryo.zombierool.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Containers;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;

import java.util.List;
import java.util.Collections;

public class DerWunderfizzBlock extends Block implements EntityBlock {
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<WunderfizzPerk> PERK_TYPE = EnumProperty.create("perk_type", WunderfizzPerk.class);

	private static final VoxelShape SHAPE = Shapes.or(
		box(0, 0, 0, 16, 16, 16),  
		box(0, 16, 0, 16, 32, 16)  
	);

	public enum WunderfizzPerk implements StringRepresentable {
		IDLE("idle"),
		MASTODONTE("mastodonte"),
		SPEED_COLA("speed_cola"),
		DOUBLE_TAPE("double_tape"),
		ROYAL_BEER("royal_beer"),
		BLOOD_RAGE("blood_rage"),
		PHD_FLOPPER("phd_flopper"),
		CHERRY("cherry"),
		QUICK_REVIVE("quick_revive"),
		VULTURE("vulture"),
        CUSTOM("custom");

		private final String name;

		WunderfizzPerk(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public static WunderfizzPerk fromString(String str) {
			if (str == null || str.isEmpty()) return IDLE;
			for (WunderfizzPerk type : values()) {
				if (type.name.equals(str)) return type;
			}
			return CUSTOM;
		}

		public static WunderfizzPerk fromIndex(int index) {
			if (index <= 0 || index >= values().length) return IDLE;
			return values()[index];
		}

		public int toIndex() {
			return this.ordinal();
		}
	}

	public DerWunderfizzBlock() {
	    super(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(-1, 3600000).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
	    this.registerDefaultState(this.stateDefinition.any()
	        .setValue(FACING, Direction.NORTH)
	        .setValue(PERK_TYPE, WunderfizzPerk.IDLE)
	        .setValue(POWERED, false)); 
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
		return true;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
		return 0;
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	    builder.add(FACING, PERK_TYPE, POWERED); 
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(PERK_TYPE, WunderfizzPerk.IDLE);
	}

	public static void updatePerkType(Level level, BlockPos pos, String perkId) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof DerWunderfizzBlock) {
			WunderfizzPerk newType = WunderfizzPerk.fromString(perkId);
			level.setBlock(pos, state.setValue(PERK_TYPE, newType), 3);
		}
	}

	public static void updatePerkTypeByIndex(Level level, BlockPos pos, int index) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof DerWunderfizzBlock) {
			WunderfizzPerk newType = WunderfizzPerk.fromIndex(index);
			level.setBlock(pos, state.setValue(PERK_TYPE, newType), 3);
		}
	}

	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	public BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}

	@Override
	public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
		return true;
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> dropsOriginal = super.getDrops(state, builder);
		if (!dropsOriginal.isEmpty())
			return dropsOriginal;
		return Collections.singletonList(new ItemStack(this, 1));
	}

	@Override
	public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
		BlockEntity tileEntity = worldIn.getBlockEntity(pos);
		return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DerWunderfizzBlockEntity(pos, state);
	}

	@Override
	public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int eventID, int eventParam) {
		super.triggerEvent(state, world, pos, eventID, eventParam);
		BlockEntity blockEntity = world.getBlockEntity(pos);
		return blockEntity == null ? false : blockEntity.triggerEvent(eventID, eventParam);
	}

	@Override
	public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
	    super.onPlace(blockstate, world, pos, oldState, isMoving);
	    if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
	        WorldConfig config = WorldConfig.get(serverLevel);
	        config.addWunderfizzPosition(pos);
	    }
	    if (!world.isClientSide) {
	        this.neighborChanged(blockstate, world, pos, this, pos, false);
	    }
	}

	@Override
	public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
	    super.neighborChanged(state, world, pos, block, fromPos, isMoving);
	    if (!world.isClientSide) {
	        boolean powered = world.hasNeighborSignal(pos);
	        if (powered != state.getValue(POWERED)) {
	            world.setBlock(pos, state.setValue(POWERED, powered), 3);
	        }
	    }
	}

	@Override
	public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
		if (state.getBlock() != newState.getBlock()) {
			if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
				WorldConfig config = WorldConfig.get(serverLevel);
				config.removeWunderfizzPosition(pos);
			}
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof DerWunderfizzBlockEntity be) {
				Containers.dropContents(world, pos, be);
				world.updateNeighbourForOutputSignal(pos, this);
			}
			super.onRemove(state, world, pos, newState, isMoving);
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide ? null : (lvl, pos, st, blockEntity) -> {
			if (blockEntity instanceof DerWunderfizzBlockEntity entity) {
				DerWunderfizzBlockEntity.tick(lvl, pos, st, entity);
			}
		};
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos) {
		BlockEntity tileentity = world.getBlockEntity(pos);
		if (tileentity instanceof DerWunderfizzBlockEntity be)
			return AbstractContainerMenu.getRedstoneSignalFromContainer(be);
		else
			return 0;
	}

	public static boolean isPowered(Level level, BlockPos pos) {
	    BlockState state = level.getBlockState(pos);
	    return state.hasProperty(POWERED) && state.getValue(POWERED);
	}
}