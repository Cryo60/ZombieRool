package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.network.ClientPacketEffects;
import me.cryo.zombierool.network.Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CPlayPositionalSoundPacket {

    private final ResourceLocation soundLocation;
    private final BlockPos pos;
    private final float volume;
    private final float pitch;

    public S2CPlayPositionalSoundPacket(ResourceLocation soundLocation, BlockPos pos, float volume, float pitch) {
        this.soundLocation = soundLocation;
        this.pos = pos;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(S2CPlayPositionalSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundLocation);
        buf.writeBlockPos(msg.pos);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
    }

    public static S2CPlayPositionalSoundPacket decode(FriendlyByteBuf buf) {
        return new S2CPlayPositionalSoundPacket(
            buf.readResourceLocation(),
            buf.readBlockPos(),
            buf.readFloat(),
            buf.readFloat()
        );
    }

    public static void handle(S2CPlayPositionalSoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        Packets.onClient(contextSupplier, () -> ClientPacketEffects.playPositionalSound(msg.soundLocation, msg.pos, msg.volume, msg.pitch));
    }
}
