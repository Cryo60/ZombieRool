package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
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
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ZombieSoundHandler {

    private static final SoundEvent GAME_MUSIC_DEFAULT = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "zombie_soundtrack"));
    private static final SoundEvent GAME_MUSIC_DAMNED = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "zombie_soundtrack_damned"));
    private static final SoundEvent MENU_MUSIC = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "menu_music"));
    private static final SoundEvent AMBIENT_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ambient_loop"));

    private static final int CHECK_DELAY_TICKS = 100;
    private static final int FADE_TICKS = 160;

    private static SoundInstance currentMusic;
    private static String zonePreset="",pendingZone;
    private static float zoneGain=1,pendingGain=1,zoneStartVolume;
    private static int zoneFade=40,zoneRemaining,zoneFadeIn;
    public static void resetMusicVolume(){zonePreset="";pendingZone=null;zoneGain=1;zoneRemaining=zoneFadeIn=0;}
    public static void restoreMapMusic(String preset){resetMusicVolume();if(currentMusic!=null)Minecraft.getInstance().getSoundManager().stop(currentMusic);currentMusic=null;musicPlaying=false;currentClientMusicPreset="default";applyMusicPreset(preset);}
    public static void applyMusicVolume(net.minecraft.nbt.CompoundTag n){String preset=n.isEmpty()?"":n.getString("preset");float gain=n.isEmpty()?1:(float)n.getDouble("volume");if(preset.equals(pendingZone==null?zonePreset:pendingZone)&&gain==(pendingZone==null?zoneGain:pendingGain))return;pendingZone=preset;pendingGain=gain;zoneFade=n.isEmpty()?zoneFade:Math.max(0,n.getInt("fadeTicks"));zoneRemaining=zoneFade;zoneStartVolume=currentMusicVolume;}
    private static String effectiveMusicPreset(Minecraft mc){return mc.level==null?"menu":"secret".equals(currentClientMusicPreset)?"secret":!zonePreset.isEmpty()?zonePreset:currentClientMusicPreset;}
    private static void updateMusicVolume(){if(currentMusic instanceof DynamicSoundLoader.FileSoundInstance file)file.setVolume(currentMusicVolume);else if(currentMusic instanceof MutableMusic sound)sound.volume(currentMusicVolume);}
    private static final class MutableMusic extends SimpleSoundInstance {
        MutableMusic(ResourceLocation id,float volume,boolean loop){super(id,SoundSource.MUSIC,volume,1,RandomSource.create(),loop,0,SoundInstance.Attenuation.NONE,0,0,0,true);}
        void volume(float value){volume=Math.max(0,value);}
    }
    private static boolean musicPlaying = false;

    public static String currentClientMusicPreset = "default";
    public static String previousClientMusicPreset = "default";

    private static float targetMusicVolume = 0.05f;
    private static float currentMusicVolume = 0.05f;

    private static boolean fadingOut = false;
    private static boolean fadingIn = false;
    private static int fadeTickCount = 0;

    public static boolean isGamePausedClient = false;
    public static int forceRestartDelay = -1;

    private static SoundInstance currentAmbientSound;
    private static boolean ambientSoundPlaying = false;
    private static int ambientRetryCooldown = 0;
    public static int tickCounter = 0;

    private static final java.util.Set<String> OVERRIDE_SOUND_WEAPONS = java.util.Set.of(
        "m40a3", "deagle", "kar98k", "barret", "fg42", "ppsh41", "intervention", "usp45", "m14", "m1garand", "gewehr43"
    );

    public static void applyMusicPreset(String newPreset) {
        if(currentClientMusicPreset.equals("secret")&&!newPreset.equals("secret")){previousClientMusicPreset=newPreset;return;}
        if(!newPreset.equals("secret")&&(!zonePreset.isEmpty()||pendingZone!=null)){if(!currentClientMusicPreset.equals("secret")){currentClientMusicPreset=newPreset;previousClientMusicPreset=newPreset;return;}}
        if(newPreset.equals(currentClientMusicPreset)&&currentMusic!=null&&musicPlaying)return;
        if (!newPreset.equals("secret")) {
            previousClientMusicPreset = newPreset;
        }
        currentClientMusicPreset = newPreset;

        SoundManager manager = Minecraft.getInstance().getSoundManager();
        if (currentMusic != null) {
            manager.stop(currentMusic);
            currentMusic = null;
        }

        musicPlaying = false;
        fadingOut = false;
        fadingIn = false;
        fadeTickCount = 0;
        forceRestartDelay = -1;
        currentMusicVolume = 0.05f;

        tickCounter = CHECK_DELAY_TICKS;
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientJoin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.AMBIENT);
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.RECORDS);
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        currentClientMusicPreset = "default";
        previousClientMusicPreset = "default";
        musicPlaying = false;
        fadingOut = false;
        fadingIn = false;
        isGamePausedClient = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(null, SoundSource.MUSIC);
            mc.getSoundManager().stop(null, SoundSource.AMBIENT);

            if (currentMusic != null) {
                mc.getSoundManager().stop(currentMusic);
                currentMusic = null;
            }
            if (currentAmbientSound != null) {
                mc.getSoundManager().stop(currentAmbientSound);
                currentAmbientSound = null;
                ambientSoundPlaying = false;
            }
        }
    }

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

        if (source == SoundSource.MUSIC) {
            if (!soundLoc.getNamespace().equals("zombierool")&&event.getSound()!=currentMusic) {
                event.setSound(null);
            } else {
                musicPlaying = true;
            }
        }

        if (source == SoundSource.AMBIENT && soundLoc.equals(AMBIENT_SOUND.getLocation())) {
            ambientSoundPlaying = true;
        }
    }

    private static SoundEvent determineTargetMusic(Minecraft mc, SoundManager manager) {
        String targetMusicPreset=effectiveMusicPreset(mc);

        SoundEvent targetMusic = null;
        if (targetMusicPreset.equals("menu")) {
            targetMusic = MENU_MUSIC;
            targetMusicVolume = 0.05f;
        } else if (targetMusicPreset.equals("default")) {
            targetMusic = GAME_MUSIC_DEFAULT;
            targetMusicVolume = 0.05f;
        } else if (targetMusicPreset.equals("damned")) {
            targetMusic = GAME_MUSIC_DAMNED;
            targetMusicVolume = 0.08f;
        } else if (targetMusicPreset.equals("secret")) {
            targetMusic = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "secret_song"));
            if (targetMusic == null) targetMusic = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "secret_song"));
            targetMusicVolume = 1.0f;
        } else if (targetMusicPreset.equals("none")) {
            targetMusic = null;
            targetMusicVolume = 0.0f;
        } else {
            var resource=new ResourceLocation(targetMusicPreset.contains(":")?targetMusicPreset:"zombierool:"+targetMusicPreset);
            targetMusic = ForgeRegistries.SOUND_EVENTS.getValue(resource);
            if (targetMusic == null) targetMusic = SoundEvent.createVariableRangeEvent(resource);
            targetMusicVolume = 0.05f;
        }

        if(!zonePreset.isEmpty()&&!targetMusicPreset.equals("secret"))targetMusicVolume*=zoneGain;
        return targetMusic;
    }

    private static SoundInstance createMusicInstance(SoundEvent targetMusic, String preset, boolean loop) {
        SoundInstance dynamicMusic = preset.contains(":")?DynamicSoundLoader.getSoundByExactName(preset,SoundSource.MUSIC,currentMusicVolume,1,loop,null):DynamicSoundLoader.nextMusic(preset, currentMusicVolume);
        if (dynamicMusic != null) {
            return dynamicMusic;
        }
        return new MutableMusic(targetMusic.getLocation(),currentMusicVolume,loop);
    }

    public static boolean continuesDuringPause(SoundInstance sound) {
        return sound == currentAmbientSound || sound == currentMusic;
    }

    private static SoundInstance createAmbientInstance() {
        SoundInstance dynamicAmbient = DynamicSoundLoader.getSoundByPrefix("zombierool:sfx/ambient/", SoundSource.AMBIENT, 0.15f, 1.0f, true, 0, 0, 0);
        if (dynamicAmbient != null) {
            return dynamicAmbient;
        }
        return new SimpleSoundInstance(
            AMBIENT_SOUND.getLocation(), SoundSource.AMBIENT, 0.15f, 1.0f, RandomSource.create(), true, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true
        );
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Bloquer la lecture et les timers si le jeu est en train de télécharger des sons !
        if (DynamicSoundLoader.isSyncing()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        SoundManager manager = mc.getSoundManager();

        if(pendingZone!=null&&!currentClientMusicPreset.equals("secret")){
            if(zoneRemaining>0&&currentMusic!=null){currentMusicVolume=zoneStartVolume*(--zoneRemaining/(float)Math.max(1,zoneFade));updateMusicVolume();return;}
            if(currentMusic!=null)manager.stop(currentMusic);currentMusic=null;musicPlaying=false;
            zonePreset=pendingZone;zoneGain=pendingGain;pendingZone=null;zoneFadeIn=zoneFade;
            fadingIn=fadingOut=false;forceRestartDelay=-1;currentMusicVolume=0;tickCounter=CHECK_DELAY_TICKS;
        }

        if (forceRestartDelay > 0) {
            forceRestartDelay--;
            if (forceRestartDelay == 0) {
                if (currentMusic != null) {
                    manager.stop(currentMusic);
                    currentMusic = null;
                }
                musicPlaying = false;
                tickCounter = CHECK_DELAY_TICKS;
            }
        }

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

            if (Math.abs(currentMusicVolume - newCalculatedVolume) > 0.001f
                    || (fadingOut && newCalculatedVolume == 0.0f)
                    || (fadingIn && newCalculatedVolume == targetMusicVolume)) {

                currentMusicVolume = newCalculatedVolume;
                musicStateChangedByFade = true;
            }
        }

        if(zoneFadeIn>0){zoneFadeIn--;musicStateChangedByFade=true;}
        updateMusicVolume();
        if (musicStateChangedByFade || (tickCounter % CHECK_DELAY_TICKS == 0)) {
            SoundEvent targetMusic = determineTargetMusic(mc, manager);
            String targetMusicPreset = effectiveMusicPreset(mc);

            if (!fadingIn && !fadingOut) {
                currentMusicVolume = targetMusicVolume;
                if(zoneFadeIn>0)currentMusicVolume*=1-zoneFadeIn/(float)Math.max(1,zoneFade);
            }

            boolean endedSecret=currentClientMusicPreset.equals("secret")&&currentMusic!=null&&!manager.isActive(currentMusic);
            if(endedSecret){currentClientMusicPreset=previousClientMusicPreset;currentMusic=null;musicPlaying=false;tickCounter=CHECK_DELAY_TICKS;return;}
            boolean needsRestart = !musicPlaying || currentMusic == null || !manager.isActive(currentMusic);

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
                    boolean loop = !"secret".equals(currentClientMusicPreset);
                    currentMusic = createMusicInstance(targetMusic, targetMusicPreset, loop);
                    manager.play(currentMusic);
                    musicPlaying = true;
                } else {
                    if (currentMusic instanceof SimpleSoundInstance && !(currentMusic instanceof MutableMusic) && ((SimpleSoundInstance)currentMusic).getVolume() != currentMusicVolume) {
                        manager.stop(currentMusic);
                        boolean loop = !"secret".equals(currentClientMusicPreset);
                        currentMusic = createMusicInstance(targetMusic, targetMusicPreset, loop);
                        manager.play(currentMusic);
                    }
                }
            }
        }

        if (currentMusic != null && !manager.isActive(currentMusic)) {
            musicPlaying = false;
            if ("secret".equals(currentClientMusicPreset)) {
                currentClientMusicPreset = previousClientMusicPreset;
                tickCounter = CHECK_DELAY_TICKS;
            }
        }

        if (ambientRetryCooldown > 0) ambientRetryCooldown--;

        if (mc.level != null) {
            if (ambientRetryCooldown == 0 && (currentAmbientSound == null || !manager.isActive(currentAmbientSound))) {
                if (currentAmbientSound != null) {
                    manager.stop(currentAmbientSound);
                }
                currentAmbientSound = createAmbientInstance();
                manager.play(currentAmbientSound);
                ambientSoundPlaying = true;
                ambientRetryCooldown = 100;
            } else if (currentAmbientSound != null && manager.isActive(currentAmbientSound)) {
                ambientRetryCooldown = 100;
            }
        } else {
            if (currentAmbientSound != null) {
                manager.stop(currentAmbientSound);
                currentAmbientSound = null;
                ambientSoundPlaying = false;
            }
        }

        tickCounter++;
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        String message = event.getMessage().getString();

        if (message.startsWith("ZOMBIEROOL_MUSIC_PRESET:")) {
            event.setCanceled(true);
            String[] parts = message.split(":");
            if (parts.length == 2) {
                applyMusicPreset(parts[1].trim());
            }
        } else if (message.startsWith("ZOMBIEROOL_GAME_PAUSED:")) {
            event.setCanceled(true);
            String[] parts = message.split(":");
            if (parts.length == 2) {
                isGamePausedClient = Boolean.parseBoolean(parts[1].trim());
                tickCounter = CHECK_DELAY_TICKS;
            }
        }
    }
}
