package me.cryo.zombierool.block.system;

import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PickableManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CPlayGlobalSoundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import me.cryo.zombierool.client.MeteoriteClientSounds;
import net.minecraftforge.network.PacketDistributor;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import java.util.Collections;
import java.util.List;

public class MeteoriteEasterEgg {

    public static void onMeteoriteFound(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PickableManager.collect(level, "meteorite", pos.asLong() + "");
        int found = PickableManager.getCollectedCount("meteorite");
        int total = PickableManager.getTotalCount("meteorite");

        ResourceLocation confirmSoundLoc = new ResourceLocation("zombierool", "easter_egg_confirm");

        if (found < total) {
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), 
                new S2CPlayGlobalSoundPacket(confirmSoundLoc, 1.0f, 1.0f));
        } else if (found == total && total > 0) {
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), 
                new S2CPlayGlobalSoundPacket(confirmSoundLoc, 1.0f, 1.0f));
            WaveManager.currentSessionMusic = "secret";
            level.getServer().getPlayerList().getPlayers().forEach(p -> {
                p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:secret"));
            });
        }
    }

    public static class MeteoriteBlock extends Block {
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
        private static final VoxelShape SHAPE = Shapes.block();

        public MeteoriteBlock() {
            super(BlockBehaviour.Properties.of()
                .sound(SoundType.STONE)
                .strength(-1, 3600000)
                .noCollission()
                .noOcclusion());
            this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        @Override
        public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            if (!state.getValue(ACTIVE)) {
                return true;
            }
            return super.canBeReplaced(state, context);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(Component.translatable("block.zombierool.meteorite.tooltip.1").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("block.zombierool.meteorite.tooltip.2").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("block.zombierool.meteorite.tooltip.3").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("block.zombierool.meteorite.tooltip.4").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
            return state.getValue(ACTIVE) ? SHAPE : Shapes.empty();
        }

        @Override
        public RenderShape getRenderShape(BlockState state) {
            return state.getValue(ACTIVE) ? RenderShape.MODEL : RenderShape.INVISIBLE;
        }

        @Override
        public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel) {
                WorldConfig.get(serverLevel).addMeteoritePosition(pos.immutable());
            }
            super.onPlace(state, world, pos, oldState, isMoving);
        }

        @Override
        public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!world.isClientSide() && world instanceof ServerLevel serverLevel && state.getBlock() != newState.getBlock()) {
                WorldConfig.get(serverLevel).removeMeteoritePosition(pos.immutable());
            }
            if (world.isClientSide) {
                MeteoriteClientSounds.stop(pos);
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(ACTIVE)) return;
            MeteoriteClientSounds.animate(state, level, pos, random);
        }

        @Override
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
            return Collections.singletonList(new ItemStack(this, 1));
        }
    }
}