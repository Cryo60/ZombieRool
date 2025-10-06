package net.mcreator.zombierool.client.screens;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;

import net.mcreator.zombierool.handlers.KeyInputHandler; // Import de KeyInputHandler

@Mod.EventBusSubscriber({Dist.CLIENT})
public class DownBloodScreenOverlay {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void eventHandler(RenderGuiEvent.Pre event) {
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        // The overlay is always drawn at the top-left corner, so posX and posY are not directly used for drawing position.
        // int posX = w / 2;
        // int posY = h / 2;
        
        // Ensure we are on the client side and have a player
        Minecraft mc = Minecraft.getInstance();
        Player entity = mc.player;
        if (entity == null) {
            return; // No player, no overlay
        }

        // HOTFIX START: Check if the *local* player is in the downed state using the shared 'downPlayers' set.
        // This set is updated for all players on the client side.
        boolean isLocalPlayerDown = KeyInputHandler.downPlayers.contains(entity.getUUID());

        if (isLocalPlayerDown) { // Render the overlay only if the local player is down
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            
            event.getGuiGraphics().blit(new ResourceLocation("zombierool:textures/screens/down_blood_screen.png"), 0, 0, 0, 0, w, h, w, h);
            
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        // HOTFIX END
    }
}