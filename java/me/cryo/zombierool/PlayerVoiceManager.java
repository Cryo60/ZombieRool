package me.cryo.zombierool.util; 

import me.cryo.zombierool.init.ZombieroolModSounds; 
import net.minecraft.sounds.SoundEvent; 
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.level.Level;
import me.cryo.zombierool.api.IReloadable; 
import me.cryo.zombierool.WorldConfig; 
import net.minecraft.server.level.ServerLevel; 

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public class PlayerVoiceManager {
    private static final Map<UUID, Long> lastKillConfirmedPlayTime = new HashMap<>();
    private static final int KILL_CONFIRM_COOLDOWN_TICKS = 40; 
    private static final int KILL_CONFIRM_CHANCE_PERCENT = 45; 

    private static final Random random = new Random();
    private static final Map<UUID, Boolean> wasReloadingLastTick = new HashMap<>();
    private static final Map<UUID, Long> lastVoiceLineInitiationTime = new HashMap<>();
    private static final int VOICE_LINE_GLOBAL_COOLDOWN_TICKS = 10; 
    
    private static final Map<UUID, Long> scheduledKillConfirmedPlays = new HashMap<>();
    private static final int KILL_CONFIRM_DELAY_TICKS = 12; 

    public static final Map<UUID, Long> lastShotTime = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> lastZombieSeenTime = new ConcurrentHashMap<>();

    private static SoundEvent resolveVoiceSound(String voicePreset, String soundType) {
        if ("none".equals(voicePreset)) {
            return null;
        }

        ResourceLocation customRes;
        if (soundType.equals("voice_melee_attack")) {
            customRes = new ResourceLocation("zombierool", "player_voice_melee_attack");
        } else {
            customRes = new ResourceLocation("zombierool", "player_" + voicePreset + "_" + soundType);
        }

        SoundEvent customSound = ForgeRegistries.SOUND_EVENTS.getValue(customRes);
        if (customSound != null) {
            return customSound;
        }
        
        return SoundEvent.createVariableRangeEvent(customRes);
    }

    private static boolean canInitiateVoiceLine(Player player, Level level) {
        long currentTick = level.getGameTime();
        UUID playerId = player.getUUID();

        long lastInitiationTime = lastVoiceLineInitiationTime.getOrDefault(playerId, 0L);
        if (currentTick - lastInitiationTime < VOICE_LINE_GLOBAL_COOLDOWN_TICKS) {
            return false; 
        }

        lastVoiceLineInitiationTime.put(playerId, currentTick); 
        return true;
    }

    public static void onPlayerShoot(Player player) {
        if (player.level().isClientSide) return;
        lastShotTime.put(player.getUUID(), player.level().getGameTime());
    }

    public static void onPlayerSeeZombie(Player player) {
        if (player.level().isClientSide) return;
        lastZombieSeenTime.put(player.getUUID(), player.level().getGameTime());
    }

    public static long getLastShotTime(Player player) {
        return lastShotTime.getOrDefault(player.getUUID(), 0L);
    }

    public static long getLastZombieSeenTime(Player player) {
        return lastZombieSeenTime.getOrDefault(player.getUUID(), 0L);
    }

    public static void playEmptyClipSound(Player player, Level level) {
        playSoundType(player, level, "empty_clip");
    }

    public static void playNoMoneySound(Player player, Level level) {
        playSoundType(player, level, "no_money");
    }

    public static void playPowerOnSound(Player player, Level level) {
        playSoundType(player, level, "power_on");
    }

    public static void playRandomChatter(Player player, Level level) {
        playSoundType(player, level, "random_chatter");
    }

    private static void playSoundType(Player player, Level level, String soundType) {
        if (level.isClientSide) return;
        
        String voicePreset = "uk";
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }

        if ("none".equals(voicePreset)) return;

        if (!canInitiateVoiceLine(player, level)) return;

        SoundEvent soundToPlay = resolveVoiceSound(voicePreset, soundType);
        
        if (soundToPlay != null) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    soundToPlay, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    public static void playReloadingSound(Player player, Level level) {
        if (level.isClientSide) return;
        playSoundType(player, level, "inform_reloading");
    }

    public static void playKillConfirmedSound(Player player, Level level) {
        if (level.isClientSide) return;

        long currentTick = level.getGameTime(); 
        UUID playerId = player.getUUID(); 
        long lastPlayTime = lastKillConfirmedPlayTime.getOrDefault(playerId, 0L);

        if (currentTick - lastPlayTime < KILL_CONFIRM_COOLDOWN_TICKS) {
            return; 
        }

        if (random.nextInt(100) < KILL_CONFIRM_CHANCE_PERCENT) {
            if (!canInitiateVoiceLine(player, level)) return; 
            
            scheduledKillConfirmedPlays.put(playerId, currentTick + KILL_CONFIRM_DELAY_TICKS);
            lastKillConfirmedPlayTime.put(playerId, currentTick);
        }
    }

    public static void checkAndPlayReloadingSoundOnTick(Player player) {
        if (player.level().isClientSide) return; 

        Level level = player.level();
        long currentTick = level.getGameTime();
        UUID playerId = player.getUUID();

        if (scheduledKillConfirmedPlays.containsKey(playerId)) {
            long scheduledTick = scheduledKillConfirmedPlays.get(playerId);
            if (currentTick >= scheduledTick) {
                playSoundType(player, level, "inform_killfirm");
                scheduledKillConfirmedPlays.remove(playerId); 
            }
        }

        ItemStack heldItem = player.getMainHandItem(); 
        boolean isCurrentlyReloading = false;

        if (heldItem.getItem() instanceof IReloadable) {
            isCurrentlyReloading = heldItem.getOrCreateTag().getBoolean("IsReloading");
        }

        boolean wasReloading = wasReloadingLastTick.getOrDefault(playerId, false);

        if (isCurrentlyReloading && !wasReloading) {
            playReloadingSound(player, level);
        }

        wasReloadingLastTick.put(playerId, isCurrentlyReloading);
    }

    public static void playMeleeAttackSound(Player player, Level level) {
        playSoundType(player, level, "voice_melee_attack");
    }
}