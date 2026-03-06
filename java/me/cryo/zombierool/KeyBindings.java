package me.cryo.zombierool.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.client.CutsweepAnimationHandler;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.MeleeAttackPacket;
import me.cryo.zombierool.network.packet.RequestConfigMenuPacket;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static KeyMapping REPAIR_AND_PURCHASE_KEY;
    public static KeyMapping RELOAD_KEY;
    public static KeyMapping MELEE_ATTACK_KEY;
    public static KeyMapping STABILIZE_KEY;
    public static KeyMapping CYCLE_CHANNEL_KEY;
    public static KeyMapping TOGGLE_SURVIVAL_VIEW_KEY;
    public static KeyMapping CONFIG_MENU_KEY;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        REPAIR_AND_PURCHASE_KEY = new KeyMapping("key.zombierool.repair_purchase", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "category.zombierool.keys");
        event.register(REPAIR_AND_PURCHASE_KEY);

        RELOAD_KEY = new KeyMapping("key.zombierool.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.zombierool.keys");
        event.register(RELOAD_KEY);

        MELEE_ATTACK_KEY = new KeyMapping("key.zombierool.melee_attack", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.zombierool.keys");
        event.register(MELEE_ATTACK_KEY);

        STABILIZE_KEY = new KeyMapping("key.zombierool.stabilize", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, "category.zombierool.keys");
        event.register(STABILIZE_KEY);

        CYCLE_CHANNEL_KEY = new KeyMapping("key.zombierool.cycle_channel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, "category.zombierool.keys");
        event.register(CYCLE_CHANNEL_KEY);

        TOGGLE_SURVIVAL_VIEW_KEY = new KeyMapping("key.zombierool.toggle_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "category.zombierool.keys");
        event.register(TOGGLE_SURVIVAL_VIEW_KEY);

        CONFIG_MENU_KEY = new KeyMapping("key.zombierool.config_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.zombierool.keys");
        event.register(CONFIG_MENU_KEY);
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class KeyInputHandler {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(mc.player.getUUID())) {
                mc.options.keyJump.setDown(false);
            }

            if (MELEE_ATTACK_KEY.consumeClick() && !CutsweepAnimationHandler.isRunning()) {
                CutsweepAnimationHandler.startCutsweepAnimation(() -> {});
                NetworkHandler.INSTANCE.sendToServer(new MeleeAttackPacket());
            }

            if (CONFIG_MENU_KEY.consumeClick()) {
                Player player = mc.player;
                if (player != null && player.hasPermissions(2)) {
                    NetworkHandler.INSTANCE.sendToServer(new RequestConfigMenuPacket());
                } else if (player != null) {
                    player.displayClientMessage(Component.literal("§cVous devez être opérateur pour ouvrir ce menu."), true);
                }
            }
        }
    }
}