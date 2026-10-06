package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CStartWunderfizzDrinkAnimationPacket {
    private final String perkId;

    public S2CStartWunderfizzDrinkAnimationPacket(String perkId) {
        this.perkId = perkId;
    }

    public S2CStartWunderfizzDrinkAnimationPacket(FriendlyByteBuf buf) {
        this.perkId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(perkId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply()));
        ctx.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply() {
            me.cryo.zombierool.client.DrinkPerkAnimationHandler.startAnimation(() -> {});
        }
    }
}