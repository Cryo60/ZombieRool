// [FILE:\main\java\me\cryo\zombierool\block\system\MeteoriteEasterEgg.java]
package me.cryo.zombierool.block.system;

import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.PlayGlobalSoundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MeteoriteEasterEgg {

    public static void onMeteoriteFound(ServerLevel level, ServerPlayer player) {
        WorldConfig config = WorldConfig.get(level);
        int found = config.getMeteoriteFragmentsFound() + 1;
        
        // Permet de boucler pour pouvoir re-déclencher l'easter egg si le joueur place de nouveaux blocs en créatif
        if (found > 3) {
            found = 1;
        }

        config.setMeteoriteFragmentsFound(found);

        ResourceLocation confirmSoundLoc = new ResourceLocation("zombierool", "easter_egg_confirm");

        if (found < 3) {
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), 
                new PlayGlobalSoundPacket(confirmSoundLoc, 1.0f, 1.0f));
        } else if (found == 3) {
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), 
                new PlayGlobalSoundPacket(confirmSoundLoc, 1.0f, 1.0f));
            WaveManager.currentSessionMusic = "secret";
            level.getServer().getPlayerList().getPlayers().forEach(p -> {
                p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:secret"));
            });
        }
    }

    public static class MeteoriteBlock extends Block {
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 6.0D, 12.0D);

        @OnlyIn(Dist.CLIENT)
        private static final Map<BlockPos, MeteoriteSoundInstance> activeSounds = new HashMap<>();

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

        // Permet au joueur de poser un bloc par-dessus la météorite une fois qu'elle est désactivée (invisible)
        @Override
        public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
            if (!state.getValue(ACTIVE)) {
                return true;
            }
            return super.canBeReplaced(state, context);
        }

        private static boolean isEnglishClient() {
            if (Minecraft.getInstance() == null) return false;
            return Minecraft.getInstance().options.languageCode.startsWith("en");
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            if (isEnglishClient()) {
                tooltip.add(Component.literal("§9Meteorite (Easter Egg)"));
                tooltip.add(Component.literal("§7Interactive block to activate a secret song."));
                tooltip.add(Component.literal("§7Place 3 meteorites on the map."));
                tooltip.add(Component.literal("§7Player must look at it and press Action (F)."));
            } else {
                tooltip.add(Component.literal("§9Météorite (Easter Egg)"));
                tooltip.add(Component.literal("§7Bloc interactif permettant d'activer une musique secrète."));
                tooltip.add(Component.literal("§7Placer 3 météorites sur la carte."));
                tooltip.add(Component.literal("§7Le joueur doit la regarder et appuyer sur Action (F)."));
            }
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
                MeteoriteSoundInstance sound = activeSounds.remove(pos);
                if (sound != null) sound.stopSound(); 
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(ACTIVE)) return;

            if (!activeSounds.containsKey(pos) || activeSounds.get(pos).isStopped()) {
                SoundEvent ambientSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "meteorite_ambient"));
                if (ambientSound != null) {
                    MeteoriteSoundInstance sound = new MeteoriteSoundInstance(ambientSound, pos.immutable());
                    Minecraft.getInstance().getSoundManager().play(sound);
                    activeSounds.put(pos.immutable(), sound);
                }
            }

            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.PORTAL, 
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5), 
                    pos.getY() + 0.5 + (random.nextDouble() - 0.5), 
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5), 
                    0, 0, 0);
            }
        }

        @Override
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
            return Collections.singletonList(new ItemStack(this, 1));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class MeteoriteSoundInstance extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final BlockPos pos;
        private boolean isStopped = false;

        public MeteoriteSoundInstance(SoundEvent sound, BlockPos pos) {
            super(sound, SoundSource.BLOCKS, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.pos = pos;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.8f;
            this.pitch = 1.0f;
            this.attenuation = Attenuation.LINEAR;
        }

        public void stopSound() {
            this.isStopped = true;
            this.stop();
        }

        @Override
        public void tick() {
            Level level = Minecraft.getInstance().level;
            if (level == null || this.isStopped) {
                this.stopSound();
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof MeteoriteBlock) || !state.getValue(MeteoriteBlock.ACTIVE)) {
                this.stopSound();
            }
        }

        @Override
        public boolean isStopped() {
            return this.isStopped || super.isStopped();
        }
    }
}