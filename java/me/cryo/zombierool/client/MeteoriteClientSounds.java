package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.MeteoriteEasterEgg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public final class MeteoriteClientSounds {
    private static final Map<BlockPos, MeteoriteSoundInstance> ACTIVE = new HashMap<>();

    private MeteoriteClientSounds() {}

    public static void stop(BlockPos pos) {
        MeteoriteSoundInstance sound = ACTIVE.remove(pos);
        if (sound != null) {
            sound.stopSound();
        }
    }

    public static void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!ACTIVE.containsKey(pos) || ACTIVE.get(pos).isStopped()) {
            SoundEvent ambientSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "meteorite_ambient"));
            if (ambientSound != null) {
                MeteoriteSoundInstance sound = new MeteoriteSoundInstance(ambientSound, pos.immutable());
                Minecraft.getInstance().getSoundManager().play(sound);
                ACTIVE.put(pos.immutable(), sound);
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

    private static final class MeteoriteSoundInstance extends AbstractTickableSoundInstance {
        private final BlockPos pos;
        private boolean stopped;

        private MeteoriteSoundInstance(SoundEvent sound, BlockPos pos) {
            super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
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

        private void stopSound() {
            this.stopped = true;
            this.stop();
        }

        @Override
        public void tick() {
            Level level = Minecraft.getInstance().level;
            if (level == null || this.stopped) {
                this.stopSound();
                return;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof MeteoriteEasterEgg.MeteoriteBlock)
                    || !state.getValue(MeteoriteEasterEgg.MeteoriteBlock.ACTIVE)) {
                this.stopSound();
            }
        }

        @Override
        public boolean isStopped() {
            return this.stopped || super.isStopped();
        }
    }
}
