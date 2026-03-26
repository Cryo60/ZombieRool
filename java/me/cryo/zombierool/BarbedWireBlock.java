package me.cryo.zombierool.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BarbedWireBlock extends RotatedPillarBlock {

    protected static final VoxelShape Y_AXIS_AABB = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 16.0D);
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D);

    public BarbedWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        Direction.Axis axis = state.getValue(AXIS);
        switch (axis) {
            case X: return X_AXIS_AABB;
            case Z: return Z_AXIS_AABB;
            case Y: default: return Y_AXIS_AABB;
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity) {
            entity.makeStuckInBlock(state, new Vec3(0.25D, 0.05D, 0.25D));
            
            if (!level.isClientSide && (entity.xOld != entity.getX() || entity.zOld != entity.getZ())) {
                entity.hurt(level.damageSources().cactus(), 2.0F); 
            }
        }
    }
}