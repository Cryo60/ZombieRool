package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientVoiceManager {
    private static final Map<UUID, SoundInstance> activeVoices = new ConcurrentHashMap<>();

    public static void handleVoicePacket(int entityId, String soundEventName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity == null) return;
        UUID uuid = entity.getUUID();

        if (isSpeaking(uuid)) return;

        SoundInstance dynamicVoice = DynamicSoundLoader.getSoundByExactName(soundEventName, SoundSource.VOICE, 1.0f, 1.0f, false, entity);
        if (dynamicVoice != null) {
            mc.getSoundManager().play(dynamicVoice);
            activeVoices.put(uuid, dynamicVoice);
            return;
        }

        ResourceLocation loc = new ResourceLocation(soundEventName);
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(loc);
        if (event == null) event = SoundEvent.createVariableRangeEvent(loc);

        EntityVoiceSoundInstance instance = new EntityVoiceSoundInstance(event, entity);
        mc.getSoundManager().play(instance);
        activeVoices.put(uuid, instance);
    }

    public static boolean isSpeaking(UUID uuid) {
        SoundInstance instance = activeVoices.get(uuid);
        if (instance != null) {
            if (Minecraft.getInstance().getSoundManager().isActive(instance)) {
                return true;
            } else {
                activeVoices.remove(uuid);
                return false;
            }
        }
        return false;
    }

    public static class EntityVoiceSoundInstance extends AbstractTickableSoundInstance {
        private final Entity entity;

        public EntityVoiceSoundInstance(SoundEvent event, Entity entity) {
            super(event, SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
            this.volume = 1.0f;
            this.pitch = 1.0f;
            this.looping = false;
            this.attenuation = Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (this.entity.isRemoved()) {
                this.stop();
            } else {
                this.x = this.entity.getX();
                this.y = this.entity.getY();
                this.z = this.entity.getZ();
            }
        }
    }
}