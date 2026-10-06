package me.cryo.zombierool.client.screens;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class BarrierStaticOverlay {
    private static final int SIZE = 128;
    private static final ResourceLocation TEXTURE = new ResourceLocation("zombierool", "dynamic/barrier_static");

    private static float target;
    private static float shown;
    private static int lastPacketTick = -1000;
    private static int noiseFrame;
    private static DynamicTexture texture;

    private BarrierStaticOverlay() {}

    public static void setIntensity(float intensity) {
        Minecraft minecraft = Minecraft.getInstance();
        target = Mth.clamp(intensity, 0.0F, 1.0F);
        lastPacketTick = minecraft.player != null ? minecraft.player.tickCount : 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        target = 0.0F;
        shown = 0.0F;
        lastPacketTick = -1000;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        if (minecraft.player.tickCount - lastPacketTick > 12) target = 0.0F;
        shown += (target - shown) * 0.22F;
        if (shown < 0.012F) return;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        redrawNoise();

        int fogAlpha = (int) (shown * 150.0F);
        event.getGuiGraphics().fill(0, 0, width, height, (fogAlpha << 24) | 0xB7BCC2);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(0.92F, 0.94F, 0.96F, 0.28F + shown * 0.62F);
        event.getGuiGraphics().blit(TEXTURE, 0, 0, 0, 0, width, height, SIZE, SIZE);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int edge = ((int) (shown * 190.0F) << 24) | 0xD5D8DC;
        int band = Math.max(8, height / 5);
        event.getGuiGraphics().fillGradient(0, 0, width, band, edge, 0);
        event.getGuiGraphics().fillGradient(0, height - band, width, height, 0, edge);

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void redrawNoise() {
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, false);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) return;
        int frame = ++noiseFrame;
        for (int y = 0; y < SIZE; y++) {
            boolean scanline = (y & 3) == 0;
            for (int x = 0; x < SIZE; x++) {
                int hash = x * 374761393 + y * 668265263 + frame * 1442695041;
                hash = (hash ^ (hash >> 13)) * 1274126177;
                hash ^= hash >> 16;
                int spark = hash & 255;
                int value;
                int alpha;
                int kind = hash & 7;
                if (kind == 0) {
                    value = 12 + (spark & 31);
                    alpha = 170 + (spark & 70);
                } else if (kind <= 2) {
                    value = 214 + (spark & 41);
                    alpha = 150 + ((hash >>> 8) & 90);
                } else {
                    value = 176 + (spark & 47);
                    alpha = 18 + ((hash >>> 10) & 70);
                }
                if (scanline) alpha = alpha * 2 / 3;
                image.setPixelRGBA(x, y, (alpha << 24) | (value << 16) | (value << 8) | value);
            }
        }
        texture.upload();
    }
}
