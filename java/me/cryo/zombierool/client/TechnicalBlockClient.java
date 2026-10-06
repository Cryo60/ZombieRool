package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class TechnicalBlockClient {
    private TechnicalBlockClient() {}

    public static boolean shouldBeInvisible() {
        Player player = Minecraft.getInstance().player;
        return player != null && (!player.isCreative() || LinkRenderer.isSurvivalViewEnabled);
    }
}
