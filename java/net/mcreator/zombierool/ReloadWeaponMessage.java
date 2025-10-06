package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.api.IReloadable;

import java.util.function.Supplier;

public class ReloadWeaponMessage {
    public ReloadWeaponMessage() {}

    public static void encode(ReloadWeaponMessage msg, FriendlyByteBuf buf) { }

    public static ReloadWeaponMessage decode(FriendlyByteBuf buf) {
        return new ReloadWeaponMessage();
    }

    public static void handler(ReloadWeaponMessage msg, Supplier<NetworkEvent.Context> ctx) {
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
