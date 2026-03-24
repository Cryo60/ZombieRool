package me.cryo.zombierool.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import me.cryo.zombierool.block.system.MimicSystem;

import java.util.Collections;
import java.util.List;

public class ZRSandbagBlock extends HorizontalDirectionalBlock {

    public enum WallShape implements net.minecraft.util.StringRepresentable {
        STRAIGHT("straight"),
        CORNER("corner"),
        T_JUNCTION("t"),
        CROSS("cross");

        private final String name;

        WallShape(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }

        @Override
        public String toString() { return name; }
    }

    public static final EnumProperty<WallShape> WALL_SHAPE =
        EnumProperty.create("shape", WallShape.class, 
            java.util.Arrays.asList(WallShape.values()));

    protected static final VoxelShape SHAPE_BOX =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 15.9D, 16.0D);

    public ZRSandbagBlock(SoundType sound) {
        super(BlockBehaviour.Properties.of()
                .sound(sound)
                .strength(-1.0f, 3600000.0f)
                .noLootTable()
                .noOcclusion());
        
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WALL_SHAPE, WallShape.STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WALL_SHAPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getClockWise();
        BlockState state = this.defaultBlockState().setValue(FACING, facing);
        return computeShape(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return computeShape(state, level, pos);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static BlockState computeShape(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean n = isSandbag(level, pos.relative(Direction.NORTH));
        boolean s = isSandbag(level, pos.relative(Direction.SOUTH));
        boolean e = isSandbag(level, pos.relative(Direction.EAST));
        boolean w = isSandbag(level, pos.relative(Direction.WEST));
        int count = (n?1:0) + (s?1:0) + (e?1:0) + (w?1:0);

        if (count == 4)
            return state.setValue(WALL_SHAPE, WallShape.CROSS);
        if (count == 3) {
            Direction open = !n ? Direction.NORTH 
                           : !s ? Direction.SOUTH
                           : !e ? Direction.EAST
                           :      Direction.WEST;
            return state.setValue(WALL_SHAPE, WallShape.T_JUNCTION).setValue(FACING, open);
        }
        if ((n && s) || (!e && !w && (n || s)))
            return state.setValue(WALL_SHAPE, WallShape.STRAIGHT).setValue(FACING, Direction.NORTH);
        if ((e && w) || (!n && !s && (e || w)))
            return state.setValue(WALL_SHAPE, WallShape.STRAIGHT).setValue(FACING, Direction.EAST);
        if (s && e) return state.setValue(WALL_SHAPE, WallShape.CORNER).setValue(FACING, Direction.WEST);  
        if (s && w) return state.setValue(WALL_SHAPE, WallShape.CORNER).setValue(FACING, Direction.NORTH); 
        if (n && w) return state.setValue(WALL_SHAPE, WallShape.CORNER).setValue(FACING, Direction.EAST);  
        if (n && e) return state.setValue(WALL_SHAPE, WallShape.CORNER).setValue(FACING, Direction.SOUTH); 
        
        return state.setValue(WALL_SHAPE, WallShape.STRAIGHT);
    }

    private static boolean isSandbag(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ZRSandbagBlock) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MimicSystem.IMimicContainer container) {
            BlockState mimic = container.getMimic();
            return mimic != null && mimic.getBlock() instanceof ZRSandbagBlock;
        }
        return false;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) { return true; }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BOX;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof ZRSandbagBlock) {
                BlockState updated = computeShape(neighborState, level, neighborPos);
                level.setBlock(neighborPos, updated, 2);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof ZRSandbagBlock) {
                    BlockState updated = computeShape(neighborState, level, neighborPos);
                    level.setBlock(neighborPos, updated, 2);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
