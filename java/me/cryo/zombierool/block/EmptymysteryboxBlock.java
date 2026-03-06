package me.cryo.zombierool.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class EmptymysteryboxBlock extends HorizontalDirectionalBlock {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty PART = BooleanProperty.create("part");
	public EmptymysteryboxBlock() {
	    super(BlockBehaviour.Properties.of()
	            .mapColor(MapColor.COLOR_BROWN) 
	            .instrument(NoteBlockInstrument.BASEDRUM)
	            .sound(SoundType.WOOD)
	            .strength(-1.0F, 3600000.0F)
	            .isRedstoneConductor((bs, br, bp) -> false)
	            .pushReaction(PushReaction.BLOCK)
	    );
	    this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, false));
	}
	
	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
	    return 15; 
	}
	
	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	    return Shapes.block(); 
	}
	
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	    return Shapes.block(); 
	}
	
	@Override
	public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
	    return Shapes.block(); 
	}
	
	@Override
	public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
	    return Shapes.block(); 
	}
	
	public boolean isRedstoneConductor(BlockState state, BlockGetter level, BlockPos pos) {
	    return false;
	}
	
	@Override
	public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
	    return false;
	}
	
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	    builder.add(FACING, PART);
	}
	
	@Override
	public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
	    if (state.getBlock() != newState.getBlock() && !isMoving) {
	        // Logique de suppression éventuelle si nécessaire, mais sans spam console
	    }
	    super.onRemove(state, worldIn, pos, newState, isMoving);
	}
	
	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
	    return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}
	
	@Override
	public BlockState mirror(BlockState state, Mirror mirrorIn) {
	    Direction facing = state.getValue(FACING);
	    Boolean part = state.getValue(PART);
	    Direction newFacing = mirrorIn.mirror(facing);
	    return state.setValue(FACING, newFacing).setValue(PART, part); 
	}
}