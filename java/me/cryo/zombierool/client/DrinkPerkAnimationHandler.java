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
import me.cryo.zombierool.gameplay.PerkDrinkTiming;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DrinkPerkAnimationHandler {
    private static int animationTimer = 0;
    private static Runnable onFinish = null;
    private static final int DURATION_TICKS = PerkDrinkTiming.DURATION_TICKS;
    private static boolean bottlePlayed, burpPlayed;
    private static SoundInstance bottleSound, burpSound;

    private static int blurTimer = 0;
    private static final int BLUR_DURATION = 15;

    public static void startAnimation(Runnable whenDone) {
        if (animationTimer <= 0 && blurTimer <= 0) {
            stopSounds();
            bottlePlayed = burpPlayed = false;
            animationTimer = DURATION_TICKS;
            onFinish = whenDone;
        }
    }

    public static boolean isRunning() {
        return animationTimer > 0 || blurTimer > 0;
    }

    public static void reset() {
        stopSounds();
        animationTimer = 0;
        blurTimer = 0;
        onFinish = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) { reset(); return; }
        if (mc.isPaused()) return;
        if (animationTimer > 0) {
            int elapsed = "drink_perk".equals(ThirdPersonAnimHandler.currentAnim(mc.player.getUUID()))
                    ? ThirdPersonAnimHandler.animationTick(mc.player.getUUID()) : DURATION_TICKS - animationTimer + 1;
            animationTimer = Math.max(0, DURATION_TICKS - elapsed);
            if (!bottlePlayed && elapsed >= PerkDrinkTiming.BOTTLE_SOUND_TICK) {
                bottlePlayed = true;
                bottleSound = play("perks_glook");
            }
            if (!burpPlayed && elapsed >= PerkDrinkTiming.BURP_SOUND_TICK) {
                burpPlayed = true;
                burpSound = play("perks_rot");
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

    private static SoundInstance play(String name) {
        var mc = Minecraft.getInstance();
        var sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", name));
        if (sound == null) return null;
        var instance = new SimpleSoundInstance(sound, SoundSource.PLAYERS, .8f, 1f,
                SoundInstance.createUnseededRandom(), mc.player.getX(), mc.player.getY(), mc.player.getZ());
        mc.getSoundManager().play(instance);
        return instance;
    }

    private static void stopSounds() {
        var manager = Minecraft.getInstance().getSoundManager();
        if (bottleSound != null) manager.stop(bottleSound);
        if (burpSound != null) manager.stop(burpSound);
        bottleSound = burpSound = null;
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

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
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (isRunning() && event.isCancelable()) {
            event.setCanceled(true);
            event.setSwingHand(false);
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
