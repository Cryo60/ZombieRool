package net.mcreator.zombierool.client.screens;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;

@Mod.EventBusSubscriber({Dist.CLIENT})
public class HitmarkerOverlay {

    private static boolean shouldDisplayHitmarker = false;
    private static final int DISPLAY_DURATION_TICKS = 10; // 0.5 seconds
    private static int hitmarkerDisplayTimer = 0;

    public static void triggerHitmarkerDisplay() {
        shouldDisplayHitmarker = true;
        hitmarkerDisplayTimer = DISPLAY_DURATION_TICKS;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void eventHandler(RenderGuiEvent.Pre event) {
        if (!shouldDisplayHitmarker) {
            return;
        }

        if (hitmarkerDisplayTimer > 0) {
            hitmarkerDisplayTimer--;
        } else {
            shouldDisplayHitmarker = false;
            return;
        }

        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Ancien: 3x3 (trop petit)
        // Maintenant, essayons une taille plus visible, par exemple 8x8 ou 16x16.
        // Vous pouvez ajuster ces valeurs si vous souhaitez une autre taille.
        int hitmarkerWidth = 16;  // TAILE DU HITMARKER : 16 pixels de large
        int hitmarkerHeight = 16; // TAILE DU HITMARKER : 16 pixels de haut

        int renderX = centerX - (hitmarkerWidth / 2);
        int renderY = centerY - (hitmarkerHeight / 2);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        
        event.getGuiGraphics().blit(new ResourceLocation("zombierool:textures/screens/hit.png"), renderX, renderY, 0, 0, hitmarkerWidth, hitmarkerHeight, hitmarkerWidth, hitmarkerHeight);
        
        RenderSystem.depthMask(true);
    RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}