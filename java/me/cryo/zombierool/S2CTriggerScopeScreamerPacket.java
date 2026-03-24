package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientSniperHandler;

import java.util.function.Supplier;

public class S2CTriggerScopeScreamerPacket {

    public S2CTriggerScopeScreamerPacket() {}

    public S2CTriggerScopeScreamerPacket(FriendlyByteBuf buf) {}

    public static void encode(S2CTriggerScopeScreamerPacket msg, FriendlyByteBuf buf) {}

    public static S2CTriggerScopeScreamerPacket decode(FriendlyByteBuf buf) {
        return new S2CTriggerScopeScreamerPacket();
    }

    public static void handle(S2CTriggerScopeScreamerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Déclenche le screamer pour 30 ticks (1.5 secondes)
                ClientSniperHandler.triggerScreamer(30);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}