package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SRestartLevelPacket {
    public C2SRestartLevelPacket() {}

    public static void encode(C2SRestartLevelPacket msg, FriendlyByteBuf buf) {}

    public static C2SRestartLevelPacket decode(FriendlyByteBuf buf) {
        return new C2SRestartLevelPacket();
    }

    public static void handle(C2SRestartLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        Packets.onServer(ctx, C2SRestartLevelPacket::restart);
    }

    private static void restart(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) {
            return;
        }
        boolean host = server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2);
        if (!host) {
            player.displayClientMessage(Component.translatable("message.zombierool.pause.restart_denied"), true);
            return;
        }
        WaveManager.restartLevel(server.overworld());
    }
}
