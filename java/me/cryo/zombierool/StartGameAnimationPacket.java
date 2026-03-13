package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientHUDHandler;
import me.cryo.zombierool.WaveManager;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
public class StartGameAnimationPacket {
    private final int waveNumber;
    private final String musicPreset;
    public StartGameAnimationPacket(int waveNumber, String musicPreset) {
        this.waveNumber = waveNumber;
        this.musicPreset = musicPreset;
    }
    public static void encode(StartGameAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.waveNumber);
        buf.writeUtf(msg.musicPreset);
    }
    public static StartGameAnimationPacket decode(FriendlyByteBuf buf) {
        int waveNumber = buf.readInt();
        String musicPreset = buf.readUtf();
        return new StartGameAnimationPacket(waveNumber, musicPreset);
    }
    public static void handle(StartGameAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft.getInstance().tell(() -> {
                ClientHUDHandler.triggerStartGameAnimation(msg.waveNumber);
                WaveManager.setClientWave(msg.waveNumber);
                me.cryo.zombierool.client.ZombieSoundHandler.applyMusicPreset(msg.musicPreset);
            });
        });
        context.setPacketHandled(true);
    }
}