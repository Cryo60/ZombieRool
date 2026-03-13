package me.cryo.zombierool.block.legacy;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class LegacySpawners {

    public static abstract class AbstractLegacySpawnerBE extends BlockEntity {
        protected int canal = 0;

        public AbstractLegacySpawnerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        public int getCanal() {
            return canal;
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            this.canal = tag.getInt("SpawnerCanal");
        }
    }

    public static abstract class LegacySpawnerBlock extends Block implements EntityBlock {
        private final UniversalSpawnerSystem.SpawnerMobType targetType;

        public LegacySpawnerBlock(UniversalSpawnerSystem.SpawnerMobType targetType) {
            super(BlockBehaviour.Properties.of().noOcclusion());
            this.targetType = targetType;
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return !level.isClientSide ? (lvl, pos, st, be) -> {
                if (be instanceof AbstractLegacySpawnerBE oldBe) {
                    int canal = oldBe.getCanal();
                    BlockState newState = UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState()
                            .setValue(UniversalSpawnerSystem.UniversalSpawnerBlock.MOB_TYPE, targetType);
                    lvl.setBlock(pos, newState, 3);
                    if (lvl.getBlockEntity(pos) instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity newBe) {
                        newBe.setConfig(targetType, "", String.valueOf(canal), "0", false, 1);
                    }
                }
            } : null;
        }
    }
}