package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.network.Packets;

import java.util.function.Supplier;

public class C2SReloadWeaponPacket {
    public C2SReloadWeaponPacket() {}

    public static void encode(C2SReloadWeaponPacket msg, FriendlyByteBuf buf) { }

    public static C2SReloadWeaponPacket decode(FriendlyByteBuf buf) {
        return new C2SReloadWeaponPacket();
    }

    public static void handle(C2SReloadWeaponPacket msg, Supplier<NetworkEvent.Context> ctx) {
        Packets.onServer(ctx, player -> {
            var stack = player.getMainHandItem();
            if (stack.getItem() instanceof IReloadable reloadable) {
                reloadable.startReload(stack, player);
            }
        });
    }

    public static void handler(C2SReloadWeaponPacket msg, Supplier<NetworkEvent.Context> ctx) {
        handle(msg, ctx);
    }
}
