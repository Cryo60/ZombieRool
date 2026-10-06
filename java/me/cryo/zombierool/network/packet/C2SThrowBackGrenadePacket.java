package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity;
import me.cryo.zombierool.item.throwable.Stielhandgranate.StielhandgranateEntity;
import me.cryo.zombierool.item.throwable.ThrowableCore;
import me.cryo.zombierool.handlers.LethalWeaponManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

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
                if (target instanceof ThrowableCore.BaseThrowableEntity thrown && player.distanceToSqr(thrown) <= 9.0) {
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(thrown.getItem().getItem());
                    String type = key != null ? key.toString() : "zombierool:grenade";
                    int cookedTicks = 0;
                    if (thrown instanceof GrenadeEntity grenade) {
                        cookedTicks = Math.max(0, Math.min(90, 100 - grenade.getFuse()));
                    } else if (thrown instanceof StielhandgranateEntity stiel) {
                        cookedTicks = Math.max(0, Math.min(90, 100 - stiel.getFuse()));
                    } else if (type.contains("molotov")) {
                        cookedTicks = 24;
                    }
                    thrown.discard();
                    LethalWeaponManager.startCookingPickedUpGrenade(player, cookedTicks, type);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}