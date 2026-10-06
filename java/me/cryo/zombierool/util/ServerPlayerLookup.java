package me.cryo.zombierool.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ServerPlayerLookup {
    private ServerPlayerLookup() {}

    public static ServerPlayer byUuid(ServerLevel level, String uuid) {
        try {
            return level.getServer().getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (Exception e) {
            return null;
        }
    }
}
