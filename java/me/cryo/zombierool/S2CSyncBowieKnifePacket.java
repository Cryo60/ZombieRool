package me.cryo.zombierool.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncBowieKnifePacket {
    private final int entityId;
    private final boolean hasBowie;

    public S2CSyncBowieKnifePacket(int entityId, boolean hasBowie) {
        this.entityId = entityId;
        this.hasBowie = hasBowie;
    }

    public S2CSyncBowieKnifePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.hasBowie = buf.readBoolean();
    }

    public static void encode(S2CSyncBowieKnifePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.hasBowie);
    }

    public static S2CSyncBowieKnifePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncBowieKnifePacket(buf);
    }

    public static void handle(S2CSyncBowieKnifePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().level != null) {
                    Entity entity = Minecraft.getInstance().level.getEntity(msg.entityId);
                    if (entity != null) {
                        entity.getPersistentData().putBoolean("zr_has_bowie_knife", msg.hasBowie);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}