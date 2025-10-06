package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.mcreator.zombierool.init.ZombieroolModSounds;

import javax.annotation.Nullable;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientPlayerDownSoundManager {

    @Nullable
    private static SoundInstance currentLastStandSound = null;
    @Nullable
    private static SoundInstance currentScreamingEasterEggSound = null;

    public static void startLastStandSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            System.err.println("[ClientPlayerDownSoundManager] Cannot start sound: local player is null.");
            return;
        }

        // Prevent starting the sound if it's already active
        if (currentLastStandSound != null && mc.getSoundManager().isActive(currentLastStandSound)) {
            System.out.println("[ClientPlayerDownSoundManager] Sound 'laststand_lp' already active, skipping new instance.");
            return;
        }

        SoundEvent soundEvent = ZombieroolModSounds.LASTSTAND_LP.get();
        if (soundEvent == null) {
            System.err.println("[ClientPlayerDownSoundManager] Sound 'laststand_lp' not found in ZombieroolModSounds. Ensure it's registered.");
            return;
        }

        currentLastStandSound = new SimpleSoundInstance(
            soundEvent.getLocation(),
            SoundSource.AMBIENT, // Sound source for ambient/background sounds
            1.0F, // Volume
            1.0F, // Pitch
            RandomSource.create(), // Random source for internal use, usually just creates one
            true, // LOOPS
            0, // Delay ticks
            SoundInstance.Attenuation.NONE, // No attenuation, global sound
            0.0D, 0.0D, 0.0D, // Position (ignored with Attenuation.NONE)
            true // Is streamed (good for longer, looping sounds)
        );

        mc.getSoundManager().play(currentLastStandSound);
        System.out.println("[ClientPlayerDownSoundManager] Sound 'laststand_lp' started.");

        // --- Easter Egg: Super Rare Screaming Loop ---
        if (mc.player.getRandom().nextInt(50) == 0) {
            SoundEvent screamingSoundEvent = ZombieroolModSounds.SCREAMING.get();
            if (screamingSoundEvent != null) {
                // Stop previous screaming sound if it's somehow still playing
                if (currentScreamingEasterEggSound != null && mc.getSoundManager().isActive(currentScreamingEasterEggSound)) {
                    mc.getSoundManager().stop(currentScreamingEasterEggSound);
                }

                currentScreamingEasterEggSound = new SimpleSoundInstance(
                    screamingSoundEvent.getLocation(),
                    SoundSource.AMBIENT,
                    0.05F, // Very low volume
                    1.0F,
                    RandomSource.create(),
                    false, // MODIFICATION: Changed to 'false' so the sound does not loop
                    0,
                    SoundInstance.Attenuation.NONE,
                    0.0D, 0.0D, 0.0D,
                    true
                );
                // MODIFICATION: Removed the getDelay() override, as it is only for looping sounds
                mc.getSoundManager().play(currentScreamingEasterEggSound);
                System.out.println("[ClientPlayerDownSoundManager] Easter Egg: Screaming sound loop started.");
            } else {
                System.err.println("[ClientPlayerDownSoundManager] Easter Egg: Sound 'screaming' not found.");
            }
        }
    }

    public static void stopLastStandSound() {
        Minecraft mc = Minecraft.getInstance();
        if (currentLastStandSound != null) {
            mc.getSoundManager().stop(currentLastStandSound);
            currentLastStandSound = null;
            System.out.println("[ClientPlayerDownSoundManager] Sound 'laststand_lp' stopped.");
        }
        // Stop the screaming Easter egg sound if it's playing
        if (currentScreamingEasterEggSound != null) {
            mc.getSoundManager().stop(currentScreamingEasterEggSound);
            currentScreamingEasterEggSound = null;
            System.out.println("[ClientPlayerDownSoundManager] Easter Egg: Screaming sound loop stopped.");
        }
    }
}
