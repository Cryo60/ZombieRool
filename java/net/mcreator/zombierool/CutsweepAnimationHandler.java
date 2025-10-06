package net.mcreator.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class CutsweepAnimationHandler {

    private static int animationTimer = 0;
    private static Runnable onFinish = null;

    private static final int ANIMATION_DURATION_TICKS = 8; // Légèrement réduit pour une animation plus rapide (à tester)
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation("zombierool", "textures/screens/cut_sweep_animation.png");
    private static final ResourceLocation KNIFE_SWING_SOUND = new ResourceLocation("zombierool", "knife_swing");

    // Ces valeurs sont fixes si votre texture est toujours une bande verticale de 512x512 par frame
    private static final int FRAME_WIDTH = 512;
    private static final int FRAME_HEIGHT = 512;
    private static final int TOTAL_FRAMES = 8;

    // Pour la blit, ce sont les dimensions de la texture complète (utile si elle contient plusieurs frames)
    // Ici, si c'est une bande verticale, la largeur totale est la largeur d'une frame, et la hauteur totale est N frames * hauteur frame.
    // Votre configuration actuelle semble être une colonne de frames, donc c'est correct.
    private static final int TEXTURE_TOTAL_WIDTH = FRAME_WIDTH;
    private static final int TEXTURE_TOTAL_HEIGHT = FRAME_HEIGHT * TOTAL_FRAMES;


    public static void startCutsweepAnimation(Runnable whenDone) {
        // Seulement si l'animation n'est PAS déjà en cours ou n'est PAS en train de se terminer (timer > 0)
        if (animationTimer <= 0) { // S'assurer qu'on ne relance pas une animation déjà lancée
            animationTimer = ANIMATION_DURATION_TICKS;
            onFinish = whenDone;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) { // Vérifier que le niveau n'est pas nul
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(KNIFE_SWING_SOUND),
                    SoundSource.PLAYERS, 1.0f, 1.0f, false
                );
            }
        }
    }

    public static boolean isRunning() {
        return animationTimer > 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (animationTimer > 0) {
            animationTimer--;
            if (animationTimer == 0) {
                if (onFinish != null) {
                    onFinish.run();
                    onFinish = null;
                }
            }
        }
    }

    // Annule les entrées clavier et souris pendant l'animation pour éviter les actions non désirées
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

    // Cache la main du joueur pendant l'animation
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isRunning()) {
            event.setCanceled(true);
        }
    }

    // Rend l'animation en overlay GUI
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return; // Ajout de mc.level == null

        if (animationTimer > 0) {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            // Calcul de la frame actuelle
            // Le calcul est bon: plus le timer est bas, plus la frame avance.
            int currentFrame = TOTAL_FRAMES - (animationTimer * TOTAL_FRAMES / ANIMATION_DURATION_TICKS);
            currentFrame = Math.max(0, Math.min(currentFrame, TOTAL_FRAMES - 1)); // S'assurer que la frame est dans les limites

            int frameV = currentFrame * FRAME_HEIGHT; // Coordonnée V pour la texture de la frame actuelle

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1, 1, 1, 1); // S'assurer que la couleur est blanche (pas de teinte)

            GuiGraphics graphics = event.getGuiGraphics();

            graphics.blit(
                TEXTURE_LOCATION,
                0, 0, // Position (x, y) de l'overlay sur l'écran
                screenWidth, screenHeight, // Largeur et hauteur de l'overlay (prend tout l'écran)
                0, frameV, // Coordonnées U, V de départ dans la texture (0 pour U, frameV pour V)
                FRAME_WIDTH, FRAME_HEIGHT, // Largeur et hauteur de la "source" dans la texture (taille d'une frame)
                TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT // Largeur et hauteur totales de la texture complète
            );

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}