package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientHUDHandler;
import me.cryo.zombierool.WaveManager; 
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public class StartGameAnimationPacket {
    private final int waveNumber;
    public StartGameAnimationPacket(int waveNumber) {
        this.waveNumber = waveNumber;
    }
    public static void encode(StartGameAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.waveNumber);
    }
    public static StartGameAnimationPacket decode(FriendlyByteBuf buf) {
        return new StartGameAnimationPacket(buf.readInt());
    }
    public static void handle(StartGameAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft.getInstance().tell(() -> {
                ClientHUDHandler.triggerStartGameAnimation(msg.waveNumber);
                WaveManager.setClientWave(msg.waveNumber); 
            });
        });
        context.setPacketHandled(true);
    }
}