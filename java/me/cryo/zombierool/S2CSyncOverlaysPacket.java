package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.core.system.OverlaySystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncOverlaysPacket {
    private final Map<String, String> overlays;

    public S2CSyncOverlaysPacket(Map<String, String> overlays) {
        this.overlays = overlays;
    }

    public S2CSyncOverlaysPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.overlays = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            String value = buf.readUtf();
            overlays.put(key, value);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(overlays.size());
        for (Map.Entry<String, String> entry : overlays.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                OverlaySystem.setOverlays(overlays);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}