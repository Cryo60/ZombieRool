package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.ClientTabListRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncPickablesPacket {
    private final Map<String, Integer> collected;
    private final Map<String, Integer> totals;

    public S2CSyncPickablesPacket(Map<String, Integer> collected, Map<String, Integer> totals) {
        this.collected = collected;
        this.totals = totals;
    }

    public S2CSyncPickablesPacket(FriendlyByteBuf buf) {
        int cSize = buf.readInt();
        this.collected = new HashMap<>();
        for (int i = 0; i < cSize; i++) {
            this.collected.put(buf.readUtf(), buf.readInt());
        }

        int tSize = buf.readInt();
        this.totals = new HashMap<>();
        for (int i = 0; i < tSize; i++) {
            this.totals.put(buf.readUtf(), buf.readInt());
        }
    }

    public static void encode(S2CSyncPickablesPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.collected.size());
        for (Map.Entry<String, Integer> entry : msg.collected.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(msg.totals.size());
        for (Map.Entry<String, Integer> entry : msg.totals.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public static S2CSyncPickablesPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncPickablesPacket(buf);
    }

    public static void handle(S2CSyncPickablesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientTabListRenderer.updatePickables(msg.collected, msg.totals);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}