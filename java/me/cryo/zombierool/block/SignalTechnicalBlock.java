package me.cryo.zombierool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** Signal markers stay visible to map makers; survival visibility is optional. */
public abstract class SignalTechnicalBlock extends AbstractTechnicalBlock {
    public static final BooleanProperty SURVIVAL_VISIBLE = BooleanProperty.create("survival_visible");
    protected SignalTechnicalBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(defaultBlockState().setValue(SURVIVAL_VISIBLE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(SURVIVAL_VISIBLE); }
    @Override public RenderShape getRenderShape(BlockState state) { return state.getValue(SURVIVAL_VISIBLE) ? RenderShape.MODEL : super.getRenderShape(state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SURVIVAL_VISIBLE) ? Shapes.block() : super.getShape(state, level, pos, context);
    }
    @Override public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }
    @Override protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
}
