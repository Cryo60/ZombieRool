package me.cryo.zombierool.network.packet; 

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries; 
import net.minecraft.util.RandomSource;

import java.util.function.Supplier;

public class PlayGlobalSoundPacket {

    private final ResourceLocation soundLocation;
    private final float volume;
    private final float pitch;

    public PlayGlobalSoundPacket(ResourceLocation soundLocation) {
        this(soundLocation, 1.0f, 1.0f);
    }

    public PlayGlobalSoundPacket(ResourceLocation soundLocation, float volume, float pitch) {
        this.soundLocation = soundLocation;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(PlayGlobalSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundLocation);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
    }

    public static PlayGlobalSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayGlobalSoundPacket(buf.readResourceLocation(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(PlayGlobalSoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(msg.soundLocation);
            if (sound != null) {
                Minecraft mc = Minecraft.getInstance();
                SimpleSoundInstance uiSound = new SimpleSoundInstance(
                    sound.getLocation(),
                    SoundSource.MASTER,
                    msg.volume,
                    msg.pitch,
                    RandomSource.create(),
                    false, // isLooping
                    0, // delay
                    SimpleSoundInstance.Attenuation.NONE, // No 3D attenuation!
                    0, 0, 0, // Pos
                    true // relative
                );
                mc.getSoundManager().play(uiSound);
            } else {
                System.err.println("Son non trouvé côté client pour ResourceLocation: " + msg.soundLocation);
            }
        });
        context.setPacketHandled(true);
    }
}