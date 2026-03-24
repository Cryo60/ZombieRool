package me.cryo.zombierool.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.RandomSource;

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
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(msg.soundLocation);
            if (sound == null) {
                sound = SoundEvent.createVariableRangeEvent(msg.soundLocation);
            }
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                SimpleSoundInstance positionalSound = new SimpleSoundInstance(
                    sound,
                    SoundSource.PLAYERS,
                    msg.volume,
                    msg.pitch,
                    RandomSource.create(),
                    msg.pos.getX() + 0.5D,
                    msg.pos.getY() + 0.5D,
                    msg.pos.getZ() + 0.5D
                );
                mc.getSoundManager().play(positionalSound);
            }
        });
        context.setPacketHandled(true);
    }
}