package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class OpenConfigMenuPacket {
    public final CompoundTag configData;
    public final boolean isGameRunning;
    public final int currentWave;

    public OpenConfigMenuPacket(CompoundTag configData, boolean isGameRunning, int currentWave) {
        this.configData = configData;
        this.isGameRunning = isGameRunning;
        this.currentWave = currentWave;
    }

    public static void encode(OpenConfigMenuPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.configData);
        buf.writeBoolean(msg.isGameRunning);
        buf.writeInt(msg.currentWave);
    }

    public static OpenConfigMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenConfigMenuPacket(buf.readNbt(), buf.readBoolean(), buf.readInt());
    }

    public static void handle(OpenConfigMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                net.minecraft.client.Minecraft.getInstance().setScreen(new me.cryo.zombierool.client.gui.WorldConfigMenuScreen(msg));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}