
package net.mcreator.zombierool.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Collections;

public class AmmoCrateBlock extends Block implements SimpleWaterloggedBlock {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	public AmmoCrateBlock() {
		super(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(-1, 3600000).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
		return state.getFluidState().isEmpty();
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
		return switch (state.getValue(FACING)) {
			default -> Shapes.or(box(1.5, 0, 2.5, 13.5, 1.25, 13.5), box(1.5, 7.75, 2.5, 13.5, 9, 13.5), box(11, 9, 7, 12.5, 10, 9), box(2.5, 9, 7, 4, 10, 9), box(12.5, 1.25, 3, 13, 7.75, 3.5), box(2, 1.25, 12.5, 2.5, 7.75, 13),
					box(2, 1.25, 3, 2.5, 7.75, 3.5), box(12.5, 1.25, 12.5, 13, 7.75, 13), box(2, 1.25, 3.5, 2.5, 7.75, 12.5), box(12.5, 1.25, 3.5, 13, 7.75, 12.5), box(2.5, 1.25, 12.5, 12.5, 7.75, 13), box(2.5, 1.25, 3, 12.5, 7.75, 3.5));
			case NORTH -> Shapes.or(box(2.5, 0, 2.5, 14.5, 1.25, 13.5), box(2.5, 7.75, 2.5, 14.5, 9, 13.5), box(3.5, 9, 7, 5, 10, 9), box(12, 9, 7, 13.5, 10, 9), box(3, 1.25, 12.5, 3.5, 7.75, 13), box(13.5, 1.25, 3, 14, 7.75, 3.5),
					box(13.5, 1.25, 12.5, 14, 7.75, 13), box(3, 1.25, 3, 3.5, 7.75, 3.5), box(13.5, 1.25, 3.5, 14, 7.75, 12.5), box(3, 1.25, 3.5, 3.5, 7.75, 12.5), box(3.5, 1.25, 3, 13.5, 7.75, 3.5), box(3.5, 1.25, 12.5, 13.5, 7.75, 13));
			case EAST -> Shapes.or(box(2.5, 0, 2.5, 13.5, 1.25, 14.5), box(2.5, 7.75, 2.5, 13.5, 9, 14.5), box(7, 9, 3.5, 9, 10, 5), box(7, 9, 12, 9, 10, 13.5), box(3, 1.25, 3, 3.5, 7.75, 3.5), box(12.5, 1.25, 13.5, 13, 7.75, 14),
					box(3, 1.25, 13.5, 3.5, 7.75, 14), box(12.5, 1.25, 3, 13, 7.75, 3.5), box(3.5, 1.25, 13.5, 12.5, 7.75, 14), box(3.5, 1.25, 3, 12.5, 7.75, 3.5), box(12.5, 1.25, 3.5, 13, 7.75, 13.5), box(3, 1.25, 3.5, 3.5, 7.75, 13.5));
			case WEST -> Shapes.or(box(2.5, 0, 1.5, 13.5, 1.25, 13.5), box(2.5, 7.75, 1.5, 13.5, 9, 13.5), box(7, 9, 11, 9, 10, 12.5), box(7, 9, 2.5, 9, 10, 4), box(12.5, 1.25, 12.5, 13, 7.75, 13), box(3, 1.25, 2, 3.5, 7.75, 2.5),
					box(12.5, 1.25, 2, 13, 7.75, 2.5), box(3, 1.25, 12.5, 3.5, 7.75, 13), box(3.5, 1.25, 2, 12.5, 7.75, 2.5), box(3.5, 1.25, 12.5, 12.5, 7.75, 13), box(3, 1.25, 2.5, 3.5, 7.75, 12.5), box(12.5, 1.25, 2.5, 13, 7.75, 12.5));
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean flag = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, flag);
	}

	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	public BlockState mirror(BlockState state, Mirror mirrorIn) {
		return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
	}

	@Override
	public FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos currentPos, BlockPos facingPos) {
		if (state.getValue(WATERLOGGED)) {
			world.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
		}
		return super.updateShape(state, facing, facingState, world, currentPos, facingPos);
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> dropsOriginal = super.getDrops(state, builder);
		if (!dropsOriginal.isEmpty())
			return dropsOriginal;
		return Collections.singletonList(new ItemStack(this, 1));
	}
}
