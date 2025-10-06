package net.mcreator.zombierool.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.mcreator.zombierool.client.CutsweepAnimationHandler;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.MeleeAttackPacket;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {
    public static KeyMapping REPAIR_AND_PURCHASE_KEY;
    public static KeyMapping RELOAD_KEY;
    public static KeyMapping MELEE_ATTACK_KEY;

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        REPAIR_AND_PURCHASE_KEY = new KeyMapping(
            "key.zombierool.repair_purchase",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F,
            "category.zombierool.keys"
        );
        event.register(REPAIR_AND_PURCHASE_KEY);

        RELOAD_KEY = new KeyMapping(
            "key.zombierool.reload",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "category.zombierool.keys"
        );
        event.register(RELOAD_KEY);

        MELEE_ATTACK_KEY = new KeyMapping(
            "key.zombierool.melee_attack",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            "category.zombierool.keys"
        );
        event.register(MELEE_ATTACK_KEY);
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT)
    public static class KeyInputHandler {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (Minecraft.getInstance().player == null) return;

            // If the player is down, block jump (client side input level)
            if (net.mcreator.zombierool.player.PlayerDownManager.isPlayerDown(Minecraft.getInstance().player.getUUID())) {
                // Block jump input
                Minecraft.getInstance().options.keyJump.setDown(false);
            }

            // Check if MELEE_ATTACK_KEY is pressed and no animation is running
            if (MELEE_ATTACK_KEY.consumeClick() && !CutsweepAnimationHandler.isRunning()) {
                CutsweepAnimationHandler.startCutsweepAnimation(() -> {});
                NetworkHandler.INSTANCE.sendToServer(new MeleeAttackPacket());
            }
        }
    }
}