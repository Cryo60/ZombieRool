package me.cryo.zombierool.core.network;

import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketShoot {
    private final float charge;
    private final boolean isLeft;

    public PacketShoot() {
        this.charge = 0.0f;
        this.isLeft = false;
    }

    public PacketShoot(float charge, boolean isLeft) {
        this.charge = charge;
        this.isLeft = isLeft;
    }

    public static void encode(PacketShoot msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.charge);
        buf.writeBoolean(msg.isLeft);
    }

    public static PacketShoot decode(FriendlyByteBuf buf) {
        return new PacketShoot(buf.readFloat(), buf.readBoolean());
    }

    public static void handle(PacketShoot msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
                    gun.tryShoot(stack, player, msg.charge, msg.isLeft);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}