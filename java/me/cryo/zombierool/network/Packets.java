package me.cryo.zombierool.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Packets {
    private Packets() {}

    public static void finish(Supplier<NetworkEvent.Context> ctx, Runnable work) {
        ctx.get().enqueueWork(work);
        ctx.get().setPacketHandled(true);
    }

    public static void onServer(Supplier<NetworkEvent.Context> ctx, Consumer<ServerPlayer> work) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                work.accept(player);
            }
        });
        context.setPacketHandled(true);
    }

    public static void onClient(Supplier<NetworkEvent.Context> ctx, Runnable work) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> work));
        context.setPacketHandled(true);
    }
}
