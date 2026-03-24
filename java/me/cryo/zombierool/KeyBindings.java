package me.cryo.zombierool.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
    public static KeyMapping CRAWL_KEY;
    public static KeyMapping LETHAL_GRENADE_KEY;
    public static KeyMapping TACTICAL_GRENADE_KEY;

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
        
        CRAWL_KEY = new KeyMapping("key.zombierool.crawl", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.zombierool.keys");
        event.register(CRAWL_KEY);
        
        LETHAL_GRENADE_KEY = new KeyMapping("key.zombierool.lethal_grenade", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.zombierool.keys");
        event.register(LETHAL_GRENADE_KEY);
        
        TACTICAL_GRENADE_KEY = new KeyMapping("key.zombierool.tactical_grenade", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_APOSTROPHE, "category.zombierool.keys");
        event.register(TACTICAL_GRENADE_KEY);
    }
}