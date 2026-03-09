package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ZombieSoundHandler {

    private static final SoundEvent GAME_MUSIC_DEFAULT = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "zombie_soundtrack"));
    private static final SoundEvent GAME_MUSIC_ILLUSION = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "zombie_soundtrack_illusion"));
    private static final SoundEvent MENU_MUSIC = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "menu_music"));
    private static final SoundEvent AMBIENT_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ambient_loop"));

    private static final int CHECK_DELAY_TICKS = 100; 
    private static final int FADE_TICKS = 160; 

    private static SimpleSoundInstance currentMusic;
    private static boolean musicPlaying = false;
    public static String currentClientMusicPreset = "default";

    private static float targetMusicVolume = 0.05f; 
    private static float currentMusicVolume = 0.05f; 
    private static boolean fadingOut = false;
    private static boolean fadingIn = false;
    private static int fadeTickCount = 0;

    public static boolean isGamePausedClient = false;

    private static SimpleSoundInstance currentAmbientSound;
    private static boolean ambientSoundPlaying = false;

    public static int tickCounter = 0;

    private static final java.util.Set<String> OVERRIDE_SOUND_WEAPONS = java.util.Set.of(
        "m40a3", "deagle", "kar98k", "barret", "fg42", "ppsh41", "intervention", "usp45", "m14", "m1garand", "gewehr43"
    );

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        SoundSource source = event.getSound().getSource();
        ResourceLocation soundLoc = event.getSound().getLocation();
        
        String path = soundLoc.getPath().toLowerCase();
        String namespace = soundLoc.getNamespace().toLowerCase();
        if ((namespace.equals("tacz") || namespace.equals("ww") || namespace.equals("elitex") || namespace.equals("hamster") || namespace.equals("ronmc") || namespace.equals("mw_guns") || namespace.equals("rainforest") || namespace.equals("halor6") || namespace.equals("valorant")) 
            && (path.contains("fire") || path.contains("shoot"))) {
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Player closest = mc.level.getNearestPlayer(event.getSound().getX(), event.getSound().getY(), event.getSound().getZ(), 2.0, false);
                if (closest != null) {
                    ItemStack stack = closest.getMainHandItem();
                    if (WeaponFacade.isTaczWeapon(stack)) {
                        WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
                        if (def != null && OVERRIDE_SOUND_WEAPONS.contains(def.id.replace("zombierool:", ""))) {
                            if (def.sounds != null && def.sounds.fire != null && !def.sounds.fire.isEmpty()) {
                                event.setSound(null); 
                            }
                        }
                    }
                }
            }
        }

        if (source == SoundSource.MUSIC &&
            !soundLoc.equals(GAME_MUSIC_DEFAULT.getLocation()) &&
            !soundLoc.equals(GAME_MUSIC_ILLUSION.getLocation()) &&
            !soundLoc.equals(MENU_MUSIC.getLocation())) {
            event.setSound(null); 
        }

        if (source == SoundSource.MUSIC &&
            (soundLoc.equals(GAME_MUSIC_DEFAULT.getLocation()) ||
            soundLoc.equals(GAME_MUSIC_ILLUSION.getLocation()) ||
            soundLoc.equals(MENU_MUSIC.getLocation()))) {
            musicPlaying = true;
        }

        if (source == SoundSource.AMBIENT && soundLoc.equals(AMBIENT_SOUND.getLocation())) {
            ambientSoundPlaying = true;
        }
    }

    private static SoundEvent determineTargetMusic(Minecraft mc, SoundManager manager) {
        String targetMusicPreset;
        if (mc.level == null) { 
            targetMusicPreset = "menu";
        } else if (isGamePausedClient) { 
            targetMusicPreset = "default";
        } else { 
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

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        SoundManager manager = mc.getSoundManager();

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
            } else { 
                newCalculatedVolume = targetMusicVolume * ((float) fadeTickCount / FADE_TICKS);
                if (newCalculatedVolume > targetMusicVolume) {
                    newCalculatedVolume = targetMusicVolume;
                    fadingIn = false;
                    fadeTickCount = 0;
                }
            }

            if (Math.abs(currentMusicVolume - newCalculatedVolume) > 0.001f || (fadingOut && newCalculatedVolume == 0.0f) || (fadingIn && newCalculatedVolume == targetMusicVolume)) {
                currentMusicVolume = newCalculatedVolume;
                musicStateChangedByFade = true;
            }
        }

        if (musicStateChangedByFade || (tickCounter % CHECK_DELAY_TICKS == 0)) {
            SoundEvent targetMusic = determineTargetMusic(mc, manager);

            boolean needsRestart = !musicPlaying || currentMusic == null || !manager.isActive(currentMusic) ||
                                   (targetMusic != null && !targetMusic.getLocation().equals(currentMusic.getLocation()));

            if (targetMusic == null || currentMusicVolume == 0.0f) { 
                if (currentMusic != null && manager.isActive(currentMusic)) {
                    manager.stop(currentMusic);
                    currentMusic = null;
                    musicPlaying = false;
                }
            } else { 
                if (needsRestart) {
                    if (currentMusic != null) {
                        manager.stop(currentMusic); 
                    }
                    currentMusic = createLoopingMusic(targetMusic); 
                    manager.play(currentMusic);
                    musicPlaying = true;
                } else {
                    if (currentMusic.getVolume() != currentMusicVolume) {
                        manager.stop(currentMusic); 
                        currentMusic = createLoopingMusic(targetMusic); 
                        manager.play(currentMusic); 
                    }
                }
            }
        }

        if (currentMusic != null && !manager.isActive(currentMusic)) {
            musicPlaying = false;
        }

        if (mc.level != null) {
            if (currentAmbientSound == null || !manager.isActive(currentAmbientSound)) {
                if (currentAmbientSound != null) {
                    manager.stop(currentAmbientSound); 
                }
                currentAmbientSound = createLoopingAmbientSound(AMBIENT_SOUND);
                manager.play(currentAmbientSound);
                ambientSoundPlaying = true;
            }
        } else { 
            if (currentAmbientSound != null && manager.isActive(currentAmbientSound)) {
                manager.stop(currentAmbientSound);
                currentAmbientSound = null;
                ambientSoundPlaying = false;
            }
        }

        tickCounter++; 
    }

    private static SimpleSoundInstance createLoopingMusic(SoundEvent sound) {
        return new SimpleSoundInstance(
            sound.getLocation(),
            SoundSource.MUSIC,
            currentMusicVolume, 
            1.0f,
            RandomSource.create(),
            true,
            0,
            SimpleSoundInstance.Attenuation.NONE,
            0, 0, 0,
            true
        );
    }

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

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();
        if (message.startsWith("ZOMBIEROOL_MUSIC_PRESET:")) {
            String[] parts = message.split(":");
            if (parts.length == 2) {
                currentClientMusicPreset = parts[1];
                tickCounter = CHECK_DELAY_TICKS; 
            }
        } else if (message.startsWith("ZOMBIEROOL_GAME_PAUSED:")) {
            String[] parts = message.split(":");
            if (parts.length == 2) {
                isGamePausedClient = Boolean.parseBoolean(parts[1]);
                tickCounter = CHECK_DELAY_TICKS; 
            }
        }
    }
}