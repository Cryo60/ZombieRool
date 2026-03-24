package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraftforge.registries.ForgeRegistries; 
import net.minecraft.util.RandomSource;

import java.util.function.Supplier;

public class S2CPlayGlobalSoundPacket {
    private final ResourceLocation soundLocation;
    private final float volume;
    private final float pitch;

    public S2CPlayGlobalSoundPacket(ResourceLocation soundLocation) {
        this(soundLocation, 1.0f, 1.0f);
    }

    public S2CPlayGlobalSoundPacket(ResourceLocation soundLocation, float volume, float pitch) {
        this.soundLocation = soundLocation;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(S2CPlayGlobalSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundLocation);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
    }

    public static S2CPlayGlobalSoundPacket decode(FriendlyByteBuf buf) {
        return new S2CPlayGlobalSoundPacket(buf.readResourceLocation(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CPlayGlobalSoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            String path = msg.soundLocation.getPath();
            String prefix = null;
            
            if (path.equals("start_zombie")) prefix = "zombierool:sfx/start/";
            else if (path.equals("next_wave_zombie")) prefix = "zombierool:sfx/round_change/";
            else if (path.equals("special_change")) prefix = "zombierool:sfx/special_change/";
            else if (path.equals("fetch_me_their_souls")) prefix = "zombierool:sfx/special_start/";
            else if (path.equals("secret_song")) prefix = "zombierool:music/secret/";
            else if (path.equals("menu_music")) prefix = "zombierool:music/menu/";
            else if (path.equals("zombie_soundtrack")) prefix = "zombierool:music/default/";
            else if (path.equals("zombie_soundtrack_damned")) prefix = "zombierool:music/damned/";

            SoundInstance dynamicSound = prefix != null ? me.cryo.zombierool.client.DynamicSoundLoader.getSoundByPrefix(prefix, SoundSource.MASTER, msg.volume, msg.pitch, false, 0, 0, 0) : null;
            
            if (dynamicSound == null) {
                dynamicSound = me.cryo.zombierool.client.DynamicSoundLoader.getSoundByExactName(msg.soundLocation.toString(), SoundSource.MASTER, msg.volume, msg.pitch, false, null);
            }

            if (dynamicSound != null) {
                Minecraft.getInstance().getSoundManager().play(dynamicSound);
            } else {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundLocation);
                if (sound == null) {
                    sound = SoundEvent.createVariableRangeEvent(msg.soundLocation);
                }
                
                Minecraft mc = Minecraft.getInstance();
                SimpleSoundInstance uiSound = new SimpleSoundInstance(
                    sound.getLocation(),
                    SoundSource.MASTER,
                    msg.volume,
                    msg.pitch,
                    RandomSource.create(),
                    false, 
                    0, 
                    SimpleSoundInstance.Attenuation.NONE, 
                    0, 0, 0, 
                    true 
                );
                mc.getSoundManager().play(uiSound);
            }
        });
        context.setPacketHandled(true);
    }
}