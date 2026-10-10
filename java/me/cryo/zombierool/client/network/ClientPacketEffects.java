package me.cryo.zombierool.client.network;

import me.cryo.zombierool.client.ClientPlayerDownSoundManager;
import me.cryo.zombierool.client.DynamicSoundLoader;
import me.cryo.zombierool.handlers.KeyInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public final class ClientPacketEffects {
    private ClientPacketEffects() {}

    public static void applyRecoil(float pitchRecoil, float yawRecoil) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.setXRot(player.getXRot() - pitchRecoil);
            player.setYRot(player.getYRot() + yawRecoil);
        }
    }

    public static void applyPlayerDown(boolean isDown, UUID playerUUID) {
        Minecraft mc = Minecraft.getInstance();
        if (isDown) {
            KeyInputHandler.downPlayers.add(playerUUID);
        } else {
            KeyInputHandler.downPlayers.remove(playerUUID);
        }

        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) {
            KeyInputHandler.isLocalPlayerDown = isDown;
            if (isDown) {
                mc.player.sendSystemMessage(Component.translatable("message.zombierool.down.downed_self"));
                ClientPlayerDownSoundManager.startLastStandSound();
            } else {
                mc.player.sendSystemMessage(Component.translatable("message.zombierool.down.revived_self"));
                ClientPlayerDownSoundManager.stopLastStandSound();
            }
        }
    }

    public static void playGlobalSound(ResourceLocation soundLocation, float volume, float pitch) {
        String path = soundLocation.getPath();
        String prefix = switch (path) {
            case "start_zombie" -> "zombierool:sfx/start/";
            case "next_wave_zombie" -> "zombierool:sfx/round_change/";
            case "special_change" -> "zombierool:sfx/special_change/";
            case "fetch_me_their_souls" -> "zombierool:sfx/special_start/";
            case "secret_song" -> "zombierool:music/secret/";
            case "menu_music" -> "zombierool:music/menu/";
            case "zombie_soundtrack" -> "zombierool:music/default/";
            case "zombie_soundtrack_damned" -> "zombierool:music/damned/";
            default -> null;
        };

        SoundInstance dynamicSound = prefix != null
                ? DynamicSoundLoader.getSoundByPrefix(prefix, SoundSource.MASTER, volume, pitch, false, 0, 0, 0)
                : null;

        if (dynamicSound == null) {
            dynamicSound = DynamicSoundLoader.getSoundByExactName(soundLocation.toString(), SoundSource.MASTER, volume, pitch, false, null);
        }

        Minecraft mc = Minecraft.getInstance();
        if (dynamicSound != null) {
            mc.getSoundManager().play(dynamicSound);
            return;
        }

        soundLocation = me.cryo.zombierool.hotfix.HotfixGameplay.resolveLegacySound(soundLocation);
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundLocation);
        if (sound == null) {
            sound = SoundEvent.createVariableRangeEvent(soundLocation);
        }

        SimpleSoundInstance uiSound = new SimpleSoundInstance(
                sound.getLocation(),
                SoundSource.MASTER,
                volume,
                pitch,
                RandomSource.create(),
                false,
                0,
                SimpleSoundInstance.Attenuation.NONE,
                0, 0, 0,
                true
        );
        mc.getSoundManager().play(uiSound);
    }

    public static void playPositionalSound(ResourceLocation soundLocation, BlockPos pos, float volume, float pitch) {
        soundLocation = me.cryo.zombierool.hotfix.HotfixGameplay.resolveLegacySound(soundLocation);
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundLocation);
        if (sound == null) {
            sound = SoundEvent.createVariableRangeEvent(soundLocation);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        SimpleSoundInstance positionalSound = new SimpleSoundInstance(
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch,
                RandomSource.create(),
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        mc.getSoundManager().play(positionalSound);
    }
}
