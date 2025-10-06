package net.mcreator.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.renderer.GameRenderer; // Import ajouté pour GameRenderer

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class DrinkPerkAnimationHandler {

    private static int animationTimer = 0;
    private static Runnable onFinish = null;

    private static final int DURATION_TICKS = 62;
    private static final ResourceLocation TEXTURE = new ResourceLocation("zombierool", "textures/screens/drink_a_cola_zbr.png");

    private static final int FRAME_WIDTH = 512;
    private static final int FRAME_HEIGHT = 512;
    private static final int TOTAL_FRAMES = 62;

    private static final int TEXTURE_TOTAL_WIDTH = FRAME_WIDTH;
    private static final int TEXTURE_TOTAL_HEIGHT = FRAME_HEIGHT * TOTAL_FRAMES;

    private static int blurTimer = 0;
    private static final int BLUR_DURATION = 10; // 0.5 seconde (10 ticks à 20 TPS)

    public static void startAnimation(Runnable whenDone) {
        // Only start if not already running an animation or blur
        if (animationTimer <= 0 && blurTimer <= 0) {
            animationTimer = DURATION_TICKS;
            onFinish = whenDone;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_glook")),
                    SoundSource.PLAYERS, 1f, 1f, false
                );
            }
        }
    }

    public static boolean isRunning() {
        return animationTimer > 0 || blurTimer > 0; // Consider blur part of "running" to block input
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return; // Only process at the end of the tick

        // Handle the main animation timer
        if (animationTimer > 0) {
            animationTimer--;
            if (animationTimer == 0) {
                // Animation finished, play sound and start blur
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.level.playLocalSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_rot")),
                        SoundSource.PLAYERS, 1f, 1f, false
                    );
                }
                blurTimer = BLUR_DURATION; // Activate the blur
                if (onFinish != null) {
                    onFinish.run();
                    onFinish = null; // Clear the runnable
                }
            }
        }

        // Handle the blur timer (decrements regardless of animationTimer state)
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
    public static void onRenderHand(RenderHandEvent event) {
        if (isRunning()) {
            event.setCanceled(true); // Hide the hand
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Render the main animation frames if active
        if (animationTimer > 0) {
            int currentFrame = DURATION_TICKS - animationTimer;
            currentFrame = Math.max(0, Math.min(currentFrame, TOTAL_FRAMES - 1));
            int frameV = currentFrame * FRAME_HEIGHT;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Utiliser le shader pour les textures
            RenderSystem.setShader(GameRenderer::getPositionTexShader); // Ajouté pour le shader
            RenderSystem.setShaderTexture(0, TEXTURE); // Liaison de la texture

            GuiGraphics graphics = event.getGuiGraphics();

            // Dessiner l'image de l'animation en plein écran
            graphics.blit(
                TEXTURE,
                0, 0,  // position à l'écran (coin supérieur gauche)
                screenWidth, screenHeight,  // taille de dessin (largeur et hauteur de l'écran)
                0, frameV,  // coordonnée UV haut gauche de la portion de l'image (u=0, v=début de la frame)
                FRAME_WIDTH, FRAME_HEIGHT,  // taille de la portion à lire dans la texture
                TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT  // taille de la texture complète
            );

            RenderSystem.disableBlend();
        }

        // Render the blur effect if active
        if (blurTimer > 0) {
            event.getGuiGraphics().fill(0, 0, screenWidth, screenHeight, 0x11000000);
        }
    }
}