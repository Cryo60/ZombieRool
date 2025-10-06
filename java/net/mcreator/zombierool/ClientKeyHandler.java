package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.init.KeyBindings;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.ReloadWeaponMessage;
import net.mcreator.zombierool.api.IReloadable;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientKeyHandler {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        // Lorsqu'on appuie sur R
        if (KeyBindings.RELOAD_KEY.consumeClick()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof IReloadable) {
                // Envoi du message au serveur pour lancer le reload
                NetworkHandler.INSTANCE.sendToServer(new ReloadWeaponMessage());
            }
        }
    }
}
