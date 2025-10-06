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
import net.minecraft.client.renderer.GameRenderer;

// Ces imports ne sont plus nécessaires car le code qui les utilisait est supprimé
// import com.mojang.blaze3d.platform.NativeImage;
// import com.mojang.blaze3d.platform.TextureUtil;
// import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
// import net.minecraft.client.renderer.texture.TextureManager;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class CherryReloadAnimationHandler {

    private static int animationTimer = 0;
    private static Runnable onFinish = null;

    private static final int FRAME_WIDTH = 64;
    private static final int FRAME_HEIGHT = 64;
    private static final int TOTAL_FRAMES = 64;

    private static final int ANIMATION_DURATION_TICKS = 30; // Accélère l'animation

    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation("zombierool", "textures/screens/animation_cherry_screen.png");

    private static final int TEXTURE_TOTAL_WIDTH = FRAME_WIDTH; // Doit être 64
    private static final int TEXTURE_TOTAL_HEIGHT = FRAME_HEIGHT * TOTAL_FRAMES; // Doit être 4096

    private static final ResourceLocation CHERRY_RELOAD_SOUND = new ResourceLocation("zombierool", "reloading_with_cherry");

    public static void startCherryAnimation(Runnable whenDone) {
        if (animationTimer <= 0) {
            animationTimer = ANIMATION_DURATION_TICKS;
            onFinish = whenDone;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                float volume = 0.3f; // Volume réduit
                float pitch = 0.8f;  // Tempo ralentie #0.8f

                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(CHERRY_RELOAD_SOUND),
                    SoundSource.PLAYERS, volume, pitch, false
                );
            }
        }
    }

    public static boolean isRunning() {
        return animationTimer > 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
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
        // La main n'est plus cachée.
    }

    @SubscribeEvent
	public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
	    Minecraft mc = Minecraft.getInstance();
	    if (mc.player == null || mc.options.hideGui) return;
	
	    if (animationTimer > 0) {
	        int currentFrame = TOTAL_FRAMES - (animationTimer * TOTAL_FRAMES / ANIMATION_DURATION_TICKS);
	        currentFrame = Math.max(0, Math.min(currentFrame, TOTAL_FRAMES - 1));
	
	        int frameV = currentFrame * FRAME_HEIGHT;
	
	        int screenWidth = mc.getWindow().getGuiScaledWidth();
	        int screenHeight = mc.getWindow().getGuiScaledHeight();
	
	        RenderSystem.enableBlend();
	        RenderSystem.defaultBlendFunc();
	        RenderSystem.setShader(GameRenderer::getPositionTexShader);
	        RenderSystem.setShaderTexture(0, TEXTURE_LOCATION);
	
	        GuiGraphics graphics = event.getGuiGraphics();
	
	        // Les UV sont en float, exprimés sur la taille totale de la texture
	       int u = 0;
			int v = frameV;
			int uWidth = FRAME_WIDTH;
			int vHeight = FRAME_HEIGHT;

	
	        graphics.blit(
	            TEXTURE_LOCATION,
	            0, 0,  // position à l'écran
	            screenWidth, screenHeight,  // taille de dessin
	            u, v,  // coordonnée UV haut gauche
	            uWidth, vHeight,  // taille de la portion
	            TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT  // taille de la texture complète
	        );
	
	        RenderSystem.disableBlend();
	    }
	}
}