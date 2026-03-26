package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DrinkPerkAnimationHandler {
    private static int animationTimer = 0;
    private static Runnable onFinish = null;
    private static final int DURATION_TICKS = 70; 
    
    private static int blurTimer = 0;
    private static final int BLUR_DURATION = 15; 

    public static void startAnimation(Runnable whenDone) {
        if (animationTimer <= 0 && blurTimer <= 0) {
            animationTimer = DURATION_TICKS;
            onFinish = whenDone;
        }
    }

    public static boolean isRunning() {
        return animationTimer > 0 || blurTimer > 0; 
    }

    public static void reset() {
        animationTimer = 0;
        blurTimer = 0;
        onFinish = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return; 
        
        if (animationTimer > 0) {
            animationTimer--;
            Minecraft mc = Minecraft.getInstance();

            // 0.83s -> 16.6 ticks écoulés. 70 - 17 = 53
            if (animationTimer == 53 && mc.player != null) {
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_glook")),
                    SoundSource.PLAYERS, 1f, 1f, false
                );
            }
            
            // 2.38s -> 47.6 ticks écoulés. 70 - 48 = 22
            if (animationTimer == 22 && mc.player != null) {
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_rot")),
                    SoundSource.PLAYERS, 1f, 1f, false
                );
            }

            if (animationTimer == 0) {
                blurTimer = BLUR_DURATION; 
                if (onFinish != null) {
                    onFinish.run();
                    onFinish = null; 
                }
            }
        }
        
        if (blurTimer > 0) {
            blurTimer--;
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (isRunning() && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        if (isRunning() && event.isCancelable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        if (blurTimer > 0) {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, screenWidth, screenHeight, 0x15000000);
        }
    }
}