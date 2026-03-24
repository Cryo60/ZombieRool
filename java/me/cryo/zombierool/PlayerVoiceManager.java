package me.cryo.zombierool.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CPlayEntityVoiceSoundPacket;
import net.minecraftforge.network.PacketDistributor;
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

    public static void playEmptyClipSound(Player player, Level level)   { playSoundType(player, level, "empty_clip"); }
    public static void playNoMoneySound(Player player, Level level)      { playSoundType(player, level, "no_money"); }
    public static void playPowerOnSound(Player player, Level level)      { playSoundType(player, level, "power_on"); }
    public static void playRandomChatter(Player player, Level level)     { playSoundType(player, level, "random_chatter"); }

    public static void playLevelStart(Player player, Level level)        { playSoundType(player, level, "level_start"); }
    public static void playSpecialStart(Player player, Level level)      { playSoundType(player, level, "special_start"); }
    
    public static void playKillHeadshot(Player player, Level level)      { playSoundType(player, level, "kill_headshot"); }
    public static void playKillHellhound(Player player, Level level)     { playSoundType(player, level, "kill_hellhound"); }
    public static void playKillCrawler(Player player, Level level)       { playSoundType(player, level, "kill_crawler"); }
    
    public static void playRespawn(Player player, Level level)           { playSoundType(player, level, "respawn"); }
    
    public static void playGeneralHit(Player player, Level level)        { playSoundType(player, level, "general_hit"); }
    public static void playZombieHit(Player player, Level level)         { playSoundType(player, level, "zombie_hit"); }
    public static void playCrawlerHit(Player player, Level level)        { playSoundType(player, level, "crawler_hit"); }
    public static void playHellhoundHit(Player player, Level level)      { playSoundType(player, level, "hellhound_hit"); }
    
    public static void playBoxMove(Player player, Level level)           { playSoundType(player, level, "box_move"); }
    public static void playWeaponUpgraded(Player player, Level level)    { playSoundType(player, level, "weapon_upgraded"); }
    public static void playTookPerk(Player player, Level level)          { playSoundType(player, level, "took_perk"); }
    
    public static void playWasRevived(Player player, Level level)        { playSoundType(player, level, "was_revived"); }
    public static void playIsReviving(Player player, Level level)        { playSoundType(player, level, "is_reviving"); }
    public static void playHasRevived(Player player, Level level)        { playSoundType(player, level, "has_revived"); }

    public static void playMeleeAttackSound(Player player, Level level)  { playSoundType(player, level, "voice_melee_attack"); }

    private static void playSoundType(Player player, Level level, String soundType) {
        if (level.isClientSide) return;

        int charId = player.getPersistentData().getInt("zr_character_id");
        if (charId < 1 || charId > 4) charId = 1;
        String charEventName = "player_" + charId + "_" + soundType;

        if (DynamicResourceManager.hasCustomVoice(charEventName)) {
            if (!canInitiateVoiceLine(player, level)) return;
            String randomKey = DynamicResourceManager.getRandomCustomVoiceKey(charEventName);
            NetworkHandler.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new S2CPlayEntityVoiceSoundPacket(player.getId(), randomKey)
            );
            return;
        }

        String voicePreset = player.getPersistentData().getString("zr_voice_preset");
        if (voicePreset == null || voicePreset.isEmpty() || voicePreset.equals("default")) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                voicePreset = me.cryo.zombierool.WorldConfig.get(serverLevel).getVoicePreset();
            } else {
                voicePreset = "none";
            }
        }

        if ("none".equals(voicePreset)) return;

        String presetEventName = voicePreset.startsWith("player_")
            ? voicePreset + "_" + soundType
            : "player_" + voicePreset + "_" + soundType;

        if (DynamicResourceManager.hasCustomVoice(presetEventName)) {
            if (!canInitiateVoiceLine(player, level)) return;
            String randomKey = DynamicResourceManager.getRandomCustomVoiceKey(presetEventName);
            NetworkHandler.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new S2CPlayEntityVoiceSoundPacket(player.getId(), randomKey)
            );
            return;
        }

        String[] builtin = {"uk", "us", "ru", "fr", "ger"};
        if (Arrays.asList(builtin).contains(voicePreset)) {
            ResourceLocation legacyLoc;
            if (soundType.equals("voice_melee_attack")) {
                legacyLoc = new ResourceLocation("zombierool", "player_voice_melee_attack");
            } else {
                legacyLoc = new ResourceLocation("zombierool", "player_" + voicePreset + "_" + soundType);
            }

            // On ne vérifie PLUS ForgeRegistries car Minecraft peut jouer des sons déclarés 
            // uniquement dans sounds.json sans avoir d'objet de Registre attribué.

            if (!canInitiateVoiceLine(player, level)) return;

            NetworkHandler.INSTANCE.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new S2CPlayEntityVoiceSoundPacket(player.getId(), legacyLoc.toString())
            );
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

        if (currentTick - lastPlayTime < KILL_CONFIRM_COOLDOWN_TICKS) return;

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
}