package me.cryo.zombierool.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncBlueFirePacket {
    private final int entityId;
    private final boolean isBlue;

    public SyncBlueFirePacket(int entityId, boolean isBlue) {
        this.entityId = entityId;
        this.isBlue = isBlue;
    }

    public SyncBlueFirePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isBlue = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isBlue);
    }

    public static SyncBlueFirePacket decode(FriendlyByteBuf buf) {
        return new SyncBlueFirePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().level != null) {
                    Entity entity = Minecraft.getInstance().level.getEntity(entityId);
                    if (entity != null) {
                        if (isBlue) {
                            entity.getPersistentData().putBoolean("BlueFire", true);
                        } else {
                            entity.getPersistentData().remove("BlueFire");
                        }
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}