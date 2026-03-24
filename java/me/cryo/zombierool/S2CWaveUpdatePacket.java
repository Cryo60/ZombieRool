package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft; 
import java.util.function.Supplier;

import me.cryo.zombierool.WaveManager; 

public class S2CWaveUpdatePacket {
    private final int wave;

    public S2CWaveUpdatePacket(int wave) {
        this.wave = wave;
    }

    public static S2CWaveUpdatePacket decode(FriendlyByteBuf buffer) {
        return new S2CWaveUpdatePacket(buffer.readInt());
    }

    public static void encode(S2CWaveUpdatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.wave);
    }

    public static void handle(S2CWaveUpdatePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) {
                WaveManager.setClientWave(msg.wave); 
                if (msg.wave == 0) {
                    me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponRenderer.clearAllPurchases();
                    me.cryo.zombierool.client.DrinkPerkAnimationHandler.reset();
                }
            }
        });
        context.setPacketHandled(true);
    }
}