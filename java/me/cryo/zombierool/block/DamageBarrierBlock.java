package me.cryo.zombierool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class DamageBarrierBlock extends AbstractTechnicalBlock {
    public DamageBarrierBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.EMPTY)
                .strength(-1, 3600000)
                .noCollission()
                .noOcclusion()
                .isSuffocating((state, world, pos) -> false)
                .isViewBlocking((state, world, pos) -> false)
                .noLootTable()
        );
    }

    @Override
    protected void addTechnicalTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("block.zombierool.damage_barrier.tooltip.1"));
        tooltip.add(Component.translatable("block.zombierool.damage_barrier.tooltip.2"));
        tooltip.add(Component.translatable("block.zombierool.damage_barrier.tooltip.3"));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) return;
        PlayerBarrier.touch(player, false);
    }
}
