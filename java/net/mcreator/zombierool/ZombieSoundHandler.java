package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ZombieSoundHandler {

    // --- Music Sound Events ---
    private static final SoundEvent GAME_MUSIC_DEFAULT = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "zombie_soundtrack"));
    private static final SoundEvent GAME_MUSIC_ILLUSION = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "zombie_soundtrack_illusion"));
    private static final SoundEvent MENU_MUSIC = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "menu_music"));

    // --- Ambient Sound Event ---
    private static final SoundEvent AMBIENT_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ambient_loop"));

    private static final int CHECK_DELAY_TICKS = 100; // How often to check music/sound state (in ticks)
    private static final int FADE_TICKS = 160; // Number of ticks for fade in/out (approx 8 seconds)

    // --- Music Related Variables ---
    private static SimpleSoundInstance currentMusic;
    private static boolean musicPlaying = false;
    private static String currentClientMusicPreset = "default";

    // NEW: Music volume control for fading
    private static float targetMusicVolume = 0.05f; // Normal volume
    private static float currentMusicVolume = 0.05f; // Actual playing volume
    private static boolean fadingOut = false;
    private static boolean fadingIn = false;
    private static int fadeTickCount = 0;

    // NEW: Game pause state (client-side)
    private static boolean isGamePausedClient = false;

    // --- Ambient Sound Related Variables ---
    private static SimpleSoundInstance currentAmbientSound;
    private static boolean ambientSoundPlaying = false;

    private static int tickCounter = 0;

    /**
     * Prevents vanilla music from playing and marks our custom music as active.
     * This event is fired when a sound is about to be played.
     * Sounds with SoundSource.AMBIENT are not affected by this logic.
     *
     * @param event The PlaySoundEvent instance.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;

        SoundSource source = event.getSound().getSource();
        ResourceLocation soundLoc = event.getSound().getLocation();

        // Block all vanilla music (MUSIC source) unless it's one of our custom tracks.
        if (source == SoundSource.MUSIC &&
            !soundLoc.equals(GAME_MUSIC_DEFAULT.getLocation()) &&
            !soundLoc.equals(GAME_MUSIC_ILLUSION.getLocation()) &&
            !soundLoc.equals(MENU_MUSIC.getLocation())) {
            event.setSound(null); // Prevent the sound from playing
        }

        // Mark our custom music as 'playing' if it's currently being launched.
        if (source == SoundSource.MUSIC &&
            (soundLoc.equals(GAME_MUSIC_DEFAULT.getLocation()) ||
            soundLoc.equals(GAME_MUSIC_ILLUSION.getLocation()) ||
            soundLoc.equals(MENU_MUSIC.getLocation()))) {
            musicPlaying = true;
        }
        
        // Mark our custom ambient sound as 'playing' if it's currently being launched.
        if (source == SoundSource.AMBIENT && soundLoc.equals(AMBIENT_SOUND.getLocation())) {
            ambientSoundPlaying = true;
        }
    }

    /**
     * Determines the target SoundEvent based on the game state and chosen preset.
     * @param mc Minecraft instance.
     * @param manager SoundManager instance.
     * @return The SoundEvent to play, or null if no music should play.
     */
    private static SoundEvent determineTargetMusic(Minecraft mc, SoundManager manager) {
        String targetMusicPreset;
        if (mc.level == null) { // In main menu
            targetMusicPreset = "menu";
        } else if (isGamePausedClient) { // Game is paused, play default game music
            targetMusicPreset = "default";
        } else { // In-game, not paused
            targetMusicPreset = currentClientMusicPreset;
        }

        SoundEvent targetMusic = null;
        if (targetMusicPreset.equals("menu")) {
            targetMusic = MENU_MUSIC;
        } else if (targetMusicPreset.equals("default")) {
            targetMusic = GAME_MUSIC_DEFAULT;
        } else if (targetMusicPreset.equals("illusion")) {
            targetMusic = GAME_MUSIC_ILLUSION;
        } else if (targetMusicPreset.equals("none")) {
            targetMusic = null;
        }
        return targetMusic;
    }

    /**
     * Handles the continuous playback of background music and ambient sounds.
     * This event is fired on the client side every tick.
     *
     * @param event The ClientTickEvent instance.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        SoundManager manager = mc.getSoundManager();

        // --- Music Fading Logic ---
        boolean musicStateChangedByFade = false;
        if (fadingOut || fadingIn) {
            fadeTickCount++;
            float newCalculatedVolume;
            if (fadingOut) {
                newCalculatedVolume = targetMusicVolume * (1.0f - (float) fadeTickCount / FADE_TICKS);
                if (newCalculatedVolume < 0.001f) {
                    newCalculatedVolume = 0.0f;
                    fadingOut = false;
                    fadeTickCount = 0;
                }
            } else { // fadingIn
                newCalculatedVolume = targetMusicVolume * ((float) fadeTickCount / FADE_TICKS);
                if (newCalculatedVolume > targetMusicVolume) {
                    newCalculatedVolume = targetMusicVolume;
                    fadingIn = false;
                    fadeTickCount = 0;
                }
            }

            // Only update if volume has actually changed significantly or if it's the end of a fade
            if (Math.abs(currentMusicVolume - newCalculatedVolume) > 0.001f || (fadingOut && newCalculatedVolume == 0.0f) || (fadingIn && newCalculatedVolume == targetMusicVolume)) {
                currentMusicVolume = newCalculatedVolume;
                musicStateChangedByFade = true;
            }
        }

        // --- Music Management Logic (triggered by fade or periodically) ---
        if (musicStateChangedByFade || (tickCounter % CHECK_DELAY_TICKS == 0)) {
            SoundEvent targetMusic = determineTargetMusic(mc, manager);

            boolean needsRestart = !musicPlaying || currentMusic == null || !manager.isActive(currentMusic) ||
                                   (targetMusic != null && !targetMusic.getLocation().equals(currentMusic.getLocation()));

            if (targetMusic == null || currentMusicVolume == 0.0f) { // No music, or faded to zero
                if (currentMusic != null && manager.isActive(currentMusic)) {
                    manager.stop(currentMusic);
                    currentMusic = null;
                    musicPlaying = false;
                }
            } else { // Music should be playing
                if (needsRestart) {
                    if (currentMusic != null) {
                        manager.stop(currentMusic); // Stop old music before starting new
                    }
                    currentMusic = createLoopingMusic(targetMusic); // Uses currentMusicVolume
                    manager.play(currentMusic);
                    musicPlaying = true;
                } else {
                    // If music is playing and it's the same track, just ensure its volume is correct
                    // This handles cases where volume changes during an active fade without full restart
                    // Recreate and replay the sound to apply the new volume
                    if (currentMusic.getVolume() != currentMusicVolume) {
                        manager.stop(currentMusic); // Stop the existing sound
                        currentMusic = createLoopingMusic(targetMusic); // Recreate with new volume
                        manager.play(currentMusic); // Play the new sound
                    }
                }
            }
        }

        // Re-evaluate 'musicPlaying' flag if the music somehow stopped externally
        if (currentMusic != null && !manager.isActive(currentMusic)) {
            musicPlaying = false;
        }

        // --- Ambient Sound Management Logic ---
        // This ensures the ambient sound plays continuously ONLY when a level is loaded (in-game).
        // It will stop when the player is in the main menu or not in a loaded world.
        if (mc.level != null) {
            if (currentAmbientSound == null || !manager.isActive(currentAmbientSound)) {
                if (currentAmbientSound != null) {
                    manager.stop(currentAmbientSound); // Stop any old instance if it somehow became inactive
                }
                currentAmbientSound = createLoopingAmbientSound(AMBIENT_SOUND);
                manager.play(currentAmbientSound);
                ambientSoundPlaying = true;
            }
        } else { // If not in-game
            if (currentAmbientSound != null && manager.isActive(currentAmbientSound)) {
                manager.stop(currentAmbientSound);
                currentAmbientSound = null;
                ambientSoundPlaying = false;
            }
        }

        tickCounter++; // Increment tick counter at the end
    }

    /**
     * Creates a SimpleSoundInstance configured for looping background music.
     * Uses SoundSource.MUSIC.
     *
     * @param sound The SoundEvent to play.
     * @return A SimpleSoundInstance ready for playback.
     */
    private static SimpleSoundInstance createLoopingMusic(SoundEvent sound) {
        return new SimpleSoundInstance(
            sound.getLocation(),
            SoundSource.MUSIC,
            currentMusicVolume, // Use the dynamically adjusted volume
            1.0f,
            RandomSource.create(),
            true,
            0,
            SimpleSoundInstance.Attenuation.NONE,
            0, 0, 0,
            true
        );
    }

    /**
     * Creates a SimpleSoundInstance configured for looping ambient sound.
     * Uses SoundSource.AMBIENT.
     *
     * @param sound The SoundEvent to play.
     * @return A SimpleSoundInstance ready for playback.
     */
    private static SimpleSoundInstance createLoopingAmbientSound(SoundEvent sound) {
        return new SimpleSoundInstance(
            sound.getLocation(),
            SoundSource.AMBIENT,
            0.15f,
            1.0f,
            RandomSource.create(),
            true,
            0,
            SimpleSoundInstance.Attenuation.NONE,
            0, 0, 0,
            true
        );
    }

    /**
     * Listens for client-side chat messages (including system messages) and updates the music preset and pause state.
     */
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (message.startsWith("ZOMBIEROOL_MUSIC_PRESET:")) {
            String[] parts = message.split(":");
            if (parts.length == 2) {
                currentClientMusicPreset = parts[1];
                tickCounter = CHECK_DELAY_TICKS; // Force a music update on the next tick
            }
        } else if (message.startsWith("ZOMBIEROOL_GAME_PAUSED:")) {
            String[] parts = message.split(":");
            if (parts.length == 2) {
                isGamePausedClient = Boolean.parseBoolean(parts[1]);
                tickCounter = CHECK_DELAY_TICKS; // Force a music update on the next tick
            }
        }
    }
}
