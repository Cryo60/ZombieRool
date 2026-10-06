package me.cryo.zombierool.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncBlueFirePacket {
    private final int entityId;
    private final boolean isBlue;

    public S2CSyncBlueFirePacket(int entityId, boolean isBlue) {
        this.entityId = entityId;
        this.isBlue = isBlue;
    }

    public S2CSyncBlueFirePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isBlue = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isBlue);
    }

    public static S2CSyncBlueFirePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncBlueFirePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(this));
        });
        ctx.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CSyncBlueFirePacket msg) {
            if (Minecraft.getInstance().level == null) return;
            Entity entity = Minecraft.getInstance().level.getEntity(msg.entityId);
            if (entity == null) return;
            if (msg.isBlue) {
                entity.getPersistentData().putBoolean("BlueFire", true);
            } else {
                entity.getPersistentData().remove("BlueFire");
            }
        }
    }
}