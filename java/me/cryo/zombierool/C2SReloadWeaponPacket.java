package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.api.IReloadable;

import java.util.function.Supplier;

public class C2SReloadWeaponPacket {
    public C2SReloadWeaponPacket() {}

    public static void encode(C2SReloadWeaponPacket msg, FriendlyByteBuf buf) { }

    public static C2SReloadWeaponPacket decode(FriendlyByteBuf buf) {
        return new C2SReloadWeaponPacket();
    }

    public static void handler(C2SReloadWeaponPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level world = player.level();  // ← utilisation de level()
            var stack = player.getMainHandItem();
            if (stack.getItem() instanceof IReloadable reloadable) {
                reloadable.startReload(stack, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
