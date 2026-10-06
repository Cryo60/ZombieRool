package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.network.ClientPacketEffects;
import me.cryo.zombierool.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

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
        Packets.onClient(contextSupplier, () -> ClientPacketEffects.playGlobalSound(msg.soundLocation, msg.volume, msg.pitch));
    }
}