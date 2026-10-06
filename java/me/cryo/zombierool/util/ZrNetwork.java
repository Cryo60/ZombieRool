package me.cryo.zombierool.util;

import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class ZrNetwork {
    private ZrNetwork() {}

    public static void toAll(Object packet) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void playGlobalSound(ResourceLocation sound) {
        toAll(new me.cryo.zombierool.network.packet.S2CPlayGlobalSoundPacket(sound));
    }

    public static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    public static void broadcast(ServerLevel level, String message) {
        broadcast(level, Component.literal(message));
    }
}
