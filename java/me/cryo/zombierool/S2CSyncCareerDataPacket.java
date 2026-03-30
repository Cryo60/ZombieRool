package me.cryo.zombierool.network.packet;

import com.google.gson.Gson;
import me.cryo.zombierool.career.CareerManager;
import me.cryo.zombierool.client.gui.CareerScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncCareerDataPacket {
    private final String jsonData;

    public S2CSyncCareerDataPacket(CareerManager.CareerData data) {
        this.jsonData = new Gson().toJson(data);
    }

    public S2CSyncCareerDataPacket(FriendlyByteBuf buf) {
        this.jsonData = buf.readUtf(262144);
    }

    public static void encode(S2CSyncCareerDataPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.jsonData, 262144);
    }

    public static S2CSyncCareerDataPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncCareerDataPacket(buf);
    }

    public static void handle(S2CSyncCareerDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                CareerManager.CareerData parsed = new Gson().fromJson(msg.jsonData, CareerManager.CareerData.class);
                CareerScreen.updateClientData(parsed);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}