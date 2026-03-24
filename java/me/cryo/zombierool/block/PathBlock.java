package me.cryo.zombierool.block;

import me.cryo.zombierool.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PathBlock extends AbstractTechnicalBlock {
    public PathBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.EMPTY)
                .strength(-1, 3600000)
                .noCollission() 
                .noOcclusion()
                .randomTicks()
        );
    }

    @Override
    protected void addTechnicalTooltip(List<Component> list) {
        list.add(Component.translatable("block.zombierool.path.tooltip.1"));
        list.add(Component.translatable("block.zombierool.path.tooltip.2"));
        list.add(Component.translatable("block.zombierool.path.tooltip.3"));
        list.add(Component.translatable("block.zombierool.path.tooltip.4"));
        list.add(Component.translatable("block.zombierool.path.tooltip.5"));
    }

    @Override
    protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty()) return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random random) {
        world.sendBlockUpdated(pos, state, state, 3);
        world.scheduleTick(pos, this, 20);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        world.scheduleTick(pos, this, 20);
        if (!world.isClientSide() && world instanceof ServerLevel serverLevel) {
            WorldConfig.get(serverLevel).addPathPosition(pos.immutable(), serverLevel);
        }
        super.onPlace(state, world, pos, oldState, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!world.isClientSide() && world instanceof ServerLevel serverLevel && state.getBlock() != newState.getBlock()) {
            WorldConfig.get(serverLevel).removePathPosition(pos.immutable(), serverLevel);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND;
    }
}