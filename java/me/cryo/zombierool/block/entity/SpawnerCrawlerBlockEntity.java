package me.cryo.zombierool.block.entity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;

public class SpawnerCrawlerBlockEntity extends AbstractSpawnerBlockEntity {
    public SpawnerCrawlerBlockEntity(BlockPos pos, BlockState state) {
        super(ZombieroolModBlockEntities.SPAWNER_CRAWLER.get(), pos, state);
    }

    @Override
    public Component getDefaultName() {
        return Component.literal("spawner_crawler");
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Spawner Crawler");
    }
}