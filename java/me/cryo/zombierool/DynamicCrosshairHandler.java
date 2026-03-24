package me.cryo.zombierool.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.handlers.KeyInputHandler;
import me.cryo.zombierool.client.ClientSniperHandler;
import me.cryo.zombierool.handlers.LethalWeaponManager;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DynamicCrosshairHandler {

    private static float currentSpreadVisual = 0.0f;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        if (ClientSniperHandler.isScoping()) {
            event.setCanceled(true);
            return;
        }

        boolean isCooking = LethalWeaponManager.isCooking(player.getUUID());
        ItemStack stack = player.getMainHandItem();
        boolean isWeapon = WeaponFacade.isWeapon(stack);

        float targetSpread = 0.0f;
        if (isWeapon && stack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
            targetSpread = gun.getDynamicSpread(stack, player);
        }

        // L'interpolation se fait par frame (FPS) et non par tick (TPS)
        // Cela garantit un mouvement de crosshair extrêmement fluide.
        currentSpreadVisual += (targetSpread - currentSpreadVisual) * 0.2f;

        if (isCooking || isWeapon) {
            event.setCanceled(true);
            GuiGraphics graphics = event.getGuiGraphics();
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            int cx = width / 2;
            int cy = height / 2;

            if (isCooking) {
                int cookTimer = KeyInputHandler.clientGrenadeCookTimer;
                drawGrenadeCrosshair(graphics, cx, cy, cookTimer);
            } else if (isWeapon) {
                WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
                boolean isLauncher = def != null && ("LAUNCHER".equalsIgnoreCase(def.type) || "ROCKET".equalsIgnoreCase(def.ballistics.type) || "PROJECTILE".equalsIgnoreCase(def.ballistics.type));
                drawWeaponCrosshair(graphics, cx, cy, currentSpreadVisual, isLauncher);
            }
        }
    }

    private static void drawGrenadeCrosshair(GuiGraphics graphics, int cx, int cy, int cookTimer) {
        float progress = Math.min(1.0f, cookTimer / 100.0f); 
        int gap = (int) (20 - (15 * progress)); 
        int length = 4;
        int thickness = 1;
        int color = 0xAAFFFFFF;

        graphics.fill(cx - gap - length, cy - thickness, cx - gap, cy + thickness, color); 
        graphics.fill(cx + gap, cy - thickness, cx + gap + length, cy + thickness, color); 
        graphics.fill(cx - thickness, cy - gap - length, cx + thickness, cy - gap, color); 
        graphics.fill(cx - thickness, cy + gap, cx + thickness, cy + gap + length, color); 
        
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xAAFF0000);
    }

    private static void drawWeaponCrosshair(GuiGraphics graphics, int cx, int cy, float spread, boolean isLauncher) {
        int gap = (int) (3 + spread * 10.0f); 
        int color = 0xBBFFFFFF;
        int shadow = 0x44000000;

        if (isLauncher) {
            graphics.fill(cx - 1, cy - gap - 8, cx + 1, cy - gap, shadow); 
            graphics.fill(cx - gap - 10, cy - 1, cx - gap, cy + 1, shadow); 
            graphics.fill(cx + gap, cy - 1, cx + gap + 10, cy + 1, shadow); 
            
            graphics.fill(cx, cy - gap - 7, cx + 1, cy - gap - 1, color); 
            graphics.fill(cx - gap - 9, cy, cx - gap - 1, cy + 1, color); 
            graphics.fill(cx + gap + 1, cy, cx + gap + 9, cy + 1, color); 

            int ladStart = cy + gap;
            graphics.fill(cx - 1, ladStart, cx + 1, ladStart + 24, shadow); 
            graphics.fill(cx, ladStart + 1, cx + 1, ladStart + 23, color); 

            drawHorizontalNotch(graphics, cx, ladStart + 4, 10, color, shadow);
            drawHorizontalNotch(graphics, cx, ladStart + 10, 8, color, shadow);
            drawHorizontalNotch(graphics, cx, ladStart + 16, 6, color, shadow);
            drawHorizontalNotch(graphics, cx, ladStart + 22, 4, color, shadow);
        } else {
            int length = 5;
            int thick = 1;
            
            graphics.fill(cx - gap - length - 1, cy - thick - 1, cx - gap + 1, cy + thick, shadow);
            graphics.fill(cx + gap - 1, cy - thick - 1, cx + gap + length + 1, cy + thick, shadow);
            graphics.fill(cx - thick - 1, cy - gap - length - 1, cx + thick, cy - gap + 1, shadow);
            graphics.fill(cx - thick - 1, cy + gap - 1, cx + thick, cy + gap + length + 1, shadow);

            graphics.fill(cx - gap - length, cy - thick, cx - gap, cy + thick, color); 
            graphics.fill(cx + gap, cy - thick, cx + gap + length, cy + thick, color); 
            graphics.fill(cx - thick, cy - gap - length, cx + thick, cy - gap, color); 
            graphics.fill(cx - thick, cy + gap, cx + thick, cy + gap + length, color); 
        }
    }

    private static void drawHorizontalNotch(GuiGraphics graphics, int cx, int y, int width, int color, int shadow) {
        graphics.fill(cx - width/2 - 1, y - 1, cx + width/2 + 1, y + 1, shadow);
        graphics.fill(cx - width/2, y, cx + width/2, y + 1, color);
    }
}