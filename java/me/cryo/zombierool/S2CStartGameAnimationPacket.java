// [main\java\me\cryo\zombierool\network\packet\S2CStartGameAnimationPacket.java]
package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientHUDHandler;
import me.cryo.zombierool.WaveManager;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public class S2CStartGameAnimationPacket {
    private final int waveNumber;
    private final String musicPreset;

    public S2CStartGameAnimationPacket(int waveNumber, String musicPreset) {
        this.waveNumber = waveNumber;
        this.musicPreset = musicPreset;
    }

    public static void encode(S2CStartGameAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.waveNumber);
        buf.writeUtf(msg.musicPreset);
    }

    public static S2CStartGameAnimationPacket decode(FriendlyByteBuf buf) {
        int waveNumber = buf.readInt();
        String musicPreset = buf.readUtf();
        return new S2CStartGameAnimationPacket(waveNumber, musicPreset);
    }

    public static void handle(S2CStartGameAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft.getInstance().tell(() -> {
                WaveManager.setClientGameRunning(true);
                ClientHUDHandler.triggerStartGameAnimation(msg.waveNumber);
                WaveManager.setClientWave(msg.waveNumber);
                me.cryo.zombierool.client.ZombieSoundHandler.applyMusicPreset(msg.musicPreset);
            });
        });
        context.setPacketHandled(true);
    }
}