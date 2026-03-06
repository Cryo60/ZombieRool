package me.cryo.zombierool.util; 

import me.cryo.zombierool.init.ZombieroolModSounds; 
import net.minecraft.sounds.SoundEvent; 
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack; 
import net.minecraft.world.level.Level;
import me.cryo.zombierool.api.IReloadable; 
import me.cryo.zombierool.WorldConfig; 
import me.cryo.zombierool.core.system.WeaponSystem; // Important import
import net.minecraft.server.level.ServerLevel; 
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Random;

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

    // ... (resolveVoiceSound reste inchangé) ...
    private static SoundEvent resolveVoiceSound(String voicePreset, String soundType) {
        if ("none".equals(voicePreset)) {
            return null;
        }
        SoundEvent defaultReloading = ZombieroolModSounds.PLAYER_UK_INFORM_RELOADING.get();
        SoundEvent defaultKillfirm = ZombieroolModSounds.PLAYER_UK_INFORM_KILLFIRM.get();
        SoundEvent defaultMelee = ZombieroolModSounds.PLAYER_VOICE_MELEE_ATTACK.get(); 
        SoundEvent selectedSound = null;
        switch (voicePreset) {
            case "uk": 
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_UK_INFORM_RELOADING.get(); break; 
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_UK_INFORM_KILLFIRM.get(); break; 
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "us":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_US_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_US_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "ru":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_RU_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_RU_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "fr":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_FR_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_FR_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "ger":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_GER_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_GER_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "default":
            default: 
                switch (soundType) {
                    case "inform_reloading": selectedSound = defaultReloading; break;
                    case "inform_killfirm": selectedSound = defaultKillfirm; break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break;
                }
                break;
        }
        if (selectedSound == null || selectedSound.getLocation() == null) {
            switch (soundType) {
                case "inform_reloading": return defaultReloading;
                case "inform_killfirm": return defaultKillfirm;
                case "voice_melee_attack": return defaultMelee;
                default: return defaultReloading; 
            }
        }
        return selectedSound;
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

    public static void playReloadingSound(Player player, Level level) {
        if (level.isClientSide) {
            return;
        }
        String voicePreset = "uk"; 
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }
        if ("none".equals(voicePreset)) {
            return;
        }
        if (!canInitiateVoiceLine(player, level)) {
            return; 
        }
        SoundEvent soundToPlay = resolveVoiceSound(voicePreset, "inform_reloading");
        if (soundToPlay != null) { 
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    soundToPlay, 
                    SoundSource.PLAYERS, 1.0f, 1.0f); 
        }
    }

    public static void playKillConfirmedSound(Player player, Level level) {
        if (level.isClientSide) {
            return;
        }
        String voicePreset = "uk"; 
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }
        if ("none".equals(voicePreset)) {
            return;
        }
        long currentTick = level.getGameTime(); 
        UUID playerId = player.getUUID(); 
        long lastPlayTime = lastKillConfirmedPlayTime.getOrDefault(playerId, 0L);
        if (currentTick - lastPlayTime < KILL_CONFIRM_COOLDOWN_TICKS) {
            return; 
        }
        if (random.nextInt(100) < KILL_CONFIRM_CHANCE_PERCENT) {
            if (!canInitiateVoiceLine(player, level)) {
                return; 
            }
            scheduledKillConfirmedPlays.put(playerId, currentTick + KILL_CONFIRM_DELAY_TICKS);
            lastKillConfirmedPlayTime.put(playerId, currentTick);
        }
    }

    public static void checkAndPlayReloadingSoundOnTick(Player player) {
        if (player.level().isClientSide) {
            return; 
        }
        Level level = player.level();
        long currentTick = level.getGameTime();
        UUID playerId = player.getUUID();
        String voicePreset = "uk"; 
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }
        if ("none".equals(voicePreset)) {
            scheduledKillConfirmedPlays.remove(playerId); 
            return;
        }
        
        // Gestion Kill Confirmed (décalé)
        SoundEvent killConfirmedSound = resolveVoiceSound(voicePreset, "inform_killfirm");
        if (scheduledKillConfirmedPlays.containsKey(playerId)) {
            long scheduledTick = scheduledKillConfirmedPlays.get(playerId);
            if (currentTick >= scheduledTick) {
                if (killConfirmedSound != null) { 
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            killConfirmedSound, 
                            SoundSource.PLAYERS, 1.0f, 1.0f); 
                }
                scheduledKillConfirmedPlays.remove(playerId); 
            }
        }
        
        // Gestion Reload
        ItemStack heldItem = player.getMainHandItem(); 
        boolean isCurrentlyReloading = false;
        
        // CORRECTION ICI : On utilise le NBT "IsReloading" plutôt que le timer > 0
        // Cela évite le spam lors du rechargement coup par coup
        if (heldItem.getItem() instanceof IReloadable) {
            isCurrentlyReloading = heldItem.getOrCreateTag().getBoolean("IsReloading");
        }
        
        boolean wasReloading = wasReloadingLastTick.getOrDefault(playerId, false);
        if (isCurrentlyReloading && !wasReloading) {
            playReloadingSound(player, player.level());
        }
        wasReloadingLastTick.put(playerId, isCurrentlyReloading);
    }
    
    // ... (playMeleeAttackSound reste inchangé) ...
    public static void playMeleeAttackSound(Player player, Level level) {
        if (level.isClientSide) {
            return;
        }
        String voicePreset = "uk"; 
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }
        if ("none".equals(voicePreset)) {
            return;
        }
        if (!canInitiateVoiceLine(player, level)) {
            return; 
        }
        SoundEvent soundToPlay = resolveVoiceSound(voicePreset, "voice_melee_attack");
        if (soundToPlay != null) { 
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    soundToPlay, 
                    SoundSource.PLAYERS, 1.0f, 1.0f); 
        }
    }
}