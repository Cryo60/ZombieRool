package me.cryo.zombierool.client;

import me.cryo.zombierool.core.system.OverlaySystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class ChalkClient {
    private ChalkClient() {}

    public static void openSelection(ItemStack stack, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new OverlaySystem.ChalkSelectionScreen(stack, hand));
    }
}
