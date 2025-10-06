package net.mcreator.zombierool.network;

import net.mcreator.zombierool.client.BloodOverlayManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SyncBloodOverlaysPacket {
    private final Map<String, String> overlays;

    public SyncBloodOverlaysPacket(Map<String, String> overlays) {
        this.overlays = overlays;
    }

    public SyncBloodOverlaysPacket(FriendlyByteBuf buf) {
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
                BloodOverlayManager.setOverlays(overlays);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}