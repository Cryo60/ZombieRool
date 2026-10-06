package me.cryo.zombierool.block;

import me.cryo.zombierool.config.WorldConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.List;

public class RestrictBlock extends AbstractTechnicalBlock {
    public RestrictBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.EMPTY)
                .strength(-1, 3600000)
                .noOcclusion()
                .isSuffocating((state, world, pos) -> false)
                .isViewBlocking((state, world, pos) -> false)
                .lightLevel(state -> 0)
                .noLootTable()
        );
    }

    @Override
    protected void addTechnicalTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("block.zombierool.restrict.tooltip.1"));
        tooltip.add(Component.translatable("block.zombierool.restrict.tooltip.2"));
        tooltip.add(Component.translatable("block.zombierool.restrict.tooltip.3"));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Entity entity = null;
        if (context instanceof EntityCollisionContext ec) {
            entity = ec.getEntity();
        }
        if (entity == null || entity instanceof Projectile || (entity instanceof Player player && player.isCreative())) {
            return Shapes.empty();
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, Mob mob) {
        return BlockPathTypes.BLOCKED;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
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
}
