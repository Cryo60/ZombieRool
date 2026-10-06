package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import me.cryo.zombierool.core.manager.InteractableManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CSyncInteractablesPacket {
    private final Map<String, InteractableManager.Interactable> interactables;

    public S2CSyncInteractablesPacket(Map<String, InteractableManager.Interactable> interactables) {
        this.interactables = interactables;
    }

    public S2CSyncInteractablesPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.interactables = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            double radius = buf.readDouble();
            String langKey = buf.readUtf();
            this.interactables.put(id, new InteractableManager.Interactable(id, pos, radius, langKey));
        }
    }

    public static void encode(S2CSyncInteractablesPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.interactables.size());
        for (InteractableManager.Interactable inter : msg.interactables.values()) {
            buf.writeUtf(inter.id);
            buf.writeDouble(inter.pos.x);
            buf.writeDouble(inter.pos.y);
            buf.writeDouble(inter.pos.z);
            buf.writeDouble(inter.radius);
            buf.writeUtf(inter.langKey);
        }
    }

    public static S2CSyncInteractablesPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncInteractablesPacket(buf);
    }

    public static void handle(S2CSyncInteractablesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                me.cryo.zombierool.client.ClientInteractableManager.setInteractables(msg.interactables);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}