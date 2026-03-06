package me.cryo.zombierool.block.entity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;

public class SpawnerZombieBlockEntity extends AbstractSpawnerBlockEntity {
    public SpawnerZombieBlockEntity(BlockPos pos, BlockState state) {
        super(ZombieroolModBlockEntities.SPAWNER_ZOMBIE.get(), pos, state);
    }

    @Override
    public Component getDefaultName() {
        return Component.literal("spawner_zombie");
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Spawner Zombie");
    }
}