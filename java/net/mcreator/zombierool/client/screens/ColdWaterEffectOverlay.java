package net.mcreator.zombierool.client.screens;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent; // NEW: Import for FOV modification
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;

@Mod.EventBusSubscriber({Dist.CLIENT})
public class ColdWaterEffectOverlay {

    // Static variable to hold the cold water intensity received from the server
    private static float currentColdWaterIntensity = 0.0f;
    private static final ResourceLocation COLD_WATER_OUTLINE_TEXTURE = new ResourceLocation("zombierool:textures/screens/powder_snow_outline.png");

    // Max amount to zoom in (e.g., reduce FOV by 30 degrees at max intensity)
    private static final float MAX_ZOOM_AMOUNT = 30.0f;

    /**
     * Sets the cold water intensity for the client-side overlay and FOV.
     * Called by the packet handler (SyncColdWaterStatePacket).
     */
    public static void setColdWaterIntensity(float intensity) {
        currentColdWaterIntensity = intensity;
    }

    /**
     * Handles the rendering of the GUI overlay (the snow outline texture).
     * This event runs after the FOV has been computed, so it only draws the visual outline.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void renderColdWaterOutline(RenderGuiOverlayEvent.Pre event) {
        // Only render if the intensity is greater than a small threshold
        if (currentColdWaterIntensity > 0.001f) {
            Minecraft mc = Minecraft.getInstance();
            GuiGraphics guiGraphics = event.getGuiGraphics();
            PoseStack poseStack = guiGraphics.pose();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();

            poseStack.pushPose();

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);

            // The alpha for the outline should be proportional to the intensity,
            // making it fade in/out with the effect.
            float outlineAlpha = currentColdWaterIntensity;
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, outlineAlpha);

            // Render the powder snow outline texture scaled to fill the screen
            guiGraphics.blit(COLD_WATER_OUTLINE_TEXTURE, 0, 0, 0, 0, w, h, w, h);

            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            poseStack.popPose();
        }
    }

    /**
     * Handles the modification of the player's Field of View (FOV).
     * This event is fired when the game needs to compute the FOV.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // Only modify FOV if the cold water effect is enabled and has intensity
        if (currentColdWaterIntensity > 0.001f) {
            // Calculate the zoom amount based on current intensity
            // As intensity goes from 0 to 1, the zoom amount goes from 0 to MAX_ZOOM_AMOUNT
            float actualZoom = MAX_ZOOM_AMOUNT * currentColdWaterIntensity;

            // Reduce the FOV. A smaller FOV value means more zoom.
            // Use getFOV() and setFOV() methods provided by the event.
            // Explicitly cast to float to resolve the lossy conversion warning.
            float currentFov = (float) event.getFOV(); // Use the getter and cast to float
            float newFov = currentFov - actualZoom;

            // Ensure the FOV doesn't go below a reasonable minimum (e.g., 1.0f)
            event.setFOV(Math.max(1.0f, newFov)); // Use the setter
        }
        // If currentColdWaterIntensity is 0, the FOV will remain at its default/player setting
    }
}
