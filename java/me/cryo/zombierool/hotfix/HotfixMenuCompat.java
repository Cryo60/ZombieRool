package me.cryo.zombierool.hotfix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;

public final class HotfixMenuCompat {
    private HotfixMenuCompat() {}
    public static boolean canOpenLan() {
        var mc=Minecraft.getInstance();
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer()!=null && !mc.getSingleplayerServer().isPublished();
    }
    public static Runnable lanAction(Screen parent) {
        return ()->Minecraft.getInstance().setScreen(new ShareToLanScreen(parent));
    }
}
