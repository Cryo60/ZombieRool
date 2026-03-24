package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity;
import me.cryo.zombierool.handlers.LethalWeaponManager;

import java.util.function.Supplier;

public class C2SThrowBackGrenadePacket {
    private final int entityId;

    public C2SThrowBackGrenadePacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(C2SThrowBackGrenadePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static C2SThrowBackGrenadePacket decode(FriendlyByteBuf buf) {
        return new C2SThrowBackGrenadePacket(buf.readInt());
    }

    public static void handle(C2SThrowBackGrenadePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity target = player.level().getEntity(msg.entityId);
                if (target instanceof GrenadeEntity grenade && player.distanceToSqr(grenade) <= 16.0) {
                    int fuseLeft = grenade.getFuse();
                    int cookedTicks = 100 - fuseLeft;
                    grenade.discard();
                    LethalWeaponManager.startCookingPickedUpGrenade(player, cookedTicks, "zombierool:grenade");
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}