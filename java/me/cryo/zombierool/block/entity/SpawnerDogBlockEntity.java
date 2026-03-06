package me.cryo.zombierool.block.entity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;

public class SpawnerDogBlockEntity extends AbstractSpawnerBlockEntity {
    public SpawnerDogBlockEntity(BlockPos pos, BlockState state) {
        super(ZombieroolModBlockEntities.SPAWNER_DOG.get(), pos, state);
    }

    @Override
    public Component getDefaultName() {
        return Component.literal("spawner_dog");
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Spawner Dog");
    }
}