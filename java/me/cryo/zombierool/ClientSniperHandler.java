package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.init.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientSniperHandler {
    private static final ResourceLocation SNIPER_SCOPE = new ResourceLocation("zombierool", "textures/misc/sniper_scope_overlay.png");
    private static final int TEX_WIDTH = 64;
    private static final int TEX_HEIGHT = 64;
    
    private static double currentZoomFov = 15.0;
    private static boolean isScoping = false;
    private static BreathState breathState = BreathState.IDLE;
    private static int breathTimer = 0;
    private static final int MAX_HOLD_DURATION = 100;
    private static final int HEARTBEAT_INTERVAL = 24;
    private static final int RECOVERY_COOLDOWN = 60;
    private static int recoveryTimer = 0;
    private static int shakyTimer = 0;
    private static final int MAX_SHAKY_DURATION = 80;
    private static float swayTime = 0;
    private static float currentSwayScale = 0.3f;
    
    private static int lastSlot = -1;
    private static boolean requireNewClick = false;

    private enum BreathState {
        IDLE, INHALING, STABILIZED, EXHALED_SHAKY
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        
        int currentSlot = player.getInventory().selected;
        if (currentSlot != lastSlot) {
            if (isScoping) {
                isScoping = false;
                enterBreathState(BreathState.IDLE, player);
                requireNewClick = true;
            }
            lastSlot = currentSlot;
        }

        ItemStack stack = player.getMainHandItem();
        boolean holdingSniper = false;
        
        if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun) {
            WeaponSystem.Definition def = gun.getDefinition();
            if (def.scoped != null && def.scoped.isScoped) {
                holdingSniper = true;
                String zoomStr = def.scoped.zoom;
                if ("8x".equalsIgnoreCase(zoomStr)) currentZoomFov = 5.0;
                else if ("4x".equalsIgnoreCase(zoomStr)) currentZoomFov = 15.0;
                else if ("2x".equalsIgnoreCase(zoomStr)) currentZoomFov = 35.0;
                else currentZoomFov = 15.0;
            }
        }
        
        boolean rightClickHeld = mc.options.keyUse.isDown();
        if (!rightClickHeld) {
            requireNewClick = false;
        }
        
        if (holdingSniper && rightClickHeld && !requireNewClick) {
            if (!isScoping) {
                isScoping = true;
                enterBreathState(BreathState.IDLE, player);
                currentSwayScale = 0.3f;
            }
            handleBreathLogic(player, mc);
        } else {
            if (isScoping) {
                isScoping = false;
                enterBreathState(BreathState.IDLE, player);
            }
        }
        
        float targetScale = 0.3f;
        switch (breathState) {
            case STABILIZED: targetScale = 0.02f; break;
            case EXHALED_SHAKY: targetScale = 1.2f; break;
            case IDLE: targetScale = (recoveryTimer > 0) ? 0.8f : 0.3f; break;
        }
        currentSwayScale = Mth.lerp(0.1f, currentSwayScale, targetScale);
    }
    
    private static void handleBreathLogic(LocalPlayer player, Minecraft mc) {
        boolean stabilizeKeyHeld = KeyBindings.STABILIZE_KEY.isDown();
        switch (breathState) {
            case IDLE:
                if (recoveryTimer > 0) {
                    swayTime += 0.15f;
                    recoveryTimer--;
                } else {
                    swayTime += 0.05f;
                    if (stabilizeKeyHeld) {
                        enterBreathState(BreathState.INHALING, player);
                    }
                }
                break;
            case INHALING:
                enterBreathState(BreathState.STABILIZED, player);
                break;
            case STABILIZED:
                if (!stabilizeKeyHeld) {
                    enterBreathState(BreathState.IDLE, player);
                    return;
                }
                breathTimer++;
                swayTime += 0.005f;
                if (breathTimer % HEARTBEAT_INTERVAL == 0 && breathTimer < MAX_HOLD_DURATION) {
                    player.level().playSound(player, player.blockPosition(),
                        ZombieroolModSounds.HEART_BEAT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                }
                if (breathTimer >= MAX_HOLD_DURATION) {
                    enterBreathState(BreathState.EXHALED_SHAKY, player);
                }
                break;
            case EXHALED_SHAKY:
                swayTime += 0.2f;
                shakyTimer--;
                if (shakyTimer <= 0) {
                    recoveryTimer = RECOVERY_COOLDOWN;
                    enterBreathState(BreathState.IDLE, player);
                }
                break;
        }
    }
    
    private static void enterBreathState(BreathState newState, LocalPlayer player) {
        BreathState oldState = breathState;
        boolean shouldExhale = (oldState == BreathState.STABILIZED && (newState == BreathState.IDLE || newState == BreathState.EXHALED_SHAKY));
        if (shouldExhale) {
            float pitch = (newState == BreathState.EXHALED_SHAKY) ? 0.7f : 0.8f;
            player.level().playSound(player, player.blockPosition(),
                SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, pitch);
        }
        breathState = newState;
        if (newState == BreathState.INHALING) {
            player.level().playSound(player, player.blockPosition(),
                SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 1.2f);
            breathTimer = 0;
        } else if (newState == BreathState.EXHALED_SHAKY) {
            shakyTimer = MAX_SHAKY_DURATION;
        } else if (newState == BreathState.STABILIZED) {
            breathTimer = 0;
        }
    }
    
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (isScoping && KeyBindings.STABILIZE_KEY.isDown()) {
            event.getInput().shiftKeyDown = false;
        }
    }
    
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isScoping) {
            event.setFOV(currentZoomFov);
        }
    }
    
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (isScoping) {
            float swayPitch = Mth.sin(swayTime) * currentSwayScale;
            float swayYaw = Mth.cos(swayTime * 0.8f) * currentSwayScale;
            if (breathState == BreathState.EXHALED_SHAKY) {
                float t = swayTime;
                swayPitch += Mth.sin(t * 2.5f) * (currentSwayScale * 0.5f);
                swayYaw += Mth.cos(t * 1.8f) * (currentSwayScale * 0.5f);
            }
            event.setPitch(event.getPitch() + swayPitch);
            event.setYaw(event.getYaw() + swayYaw);
        }
    }
    
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (isScoping) {
            String id = event.getOverlay().id().toString();
            if (id.contains("crosshair") || id.contains("hotbar") || 
                id.contains("experience_bar") || id.contains("jump_bar") || 
                id.contains("chat_panel") || id.contains("player_health") || 
                id.contains("armor_level") || id.contains("food_level") || 
                id.contains("item_name")) {
                event.setCanceled(true);
            }
        }
    }
    
    @SubscribeEvent
    public static void onRenderGameOverlayPost(RenderGuiEvent.Post event) {
        if (isScoping) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.options.getCameraType().isFirstPerson()) {
                return;
            }
            
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            
            // Scope de taille 80% de l'écran pour un aspect naturel
            int scopeSize = (int) (screenHeight * 0.8f);
            int scopeX = (screenWidth - scopeSize) / 2;
            int scopeY = (screenHeight - scopeSize) / 2;
            
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            GuiGraphics graphics = event.getGuiGraphics();
            
            int blackColor = 0xFF000000;
            
            // Remplir avec du noir autour du scope pour couvrir l'écran
            graphics.fill(0, 0, scopeX, screenHeight, blackColor); // gauche
            graphics.fill(scopeX + scopeSize, 0, screenWidth, screenHeight, blackColor); // droite
            graphics.fill(scopeX, 0, scopeX + scopeSize, scopeY, blackColor); // haut
            graphics.fill(scopeX, scopeY + scopeSize, scopeX + scopeSize, screenHeight, blackColor); // bas
            
            RenderSystem.setShaderTexture(0, SNIPER_SCOPE);
            graphics.blit(SNIPER_SCOPE, scopeX, scopeY, scopeSize, scopeSize, 0, 0, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
            
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;
            int dotSize = 1;
            int redColor = 0xFFFF0000;
            graphics.fill(centerX - dotSize, centerY - dotSize, centerX + dotSize, centerY + dotSize, redColor);
            
            if (breathState == BreathState.IDLE && recoveryTimer <= 0) {
                String keyName = KeyBindings.STABILIZE_KEY.getTranslatedKeyMessage().getString().toUpperCase();
                Component tip = Component.translatable("zombierool.hud.sniper.stabilize", keyName);
                if (tip.getString().equals("zombierool.hud.sniper.stabilize")) {
                    tip = Component.literal("Hold [" + keyName + "] to stabilize");
                }
                graphics.drawCenteredString(mc.font, tip, centerX, centerY + scopeSize / 2 + 20, 0xFFFFFFFF);
            }
            
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }
    
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (isScoping) {
            event.setCanceled(true);
        }
    }
    
    public static boolean isScoping() {
        return isScoping;
    }

    public static boolean isStabilizing() {
        return isScoping && breathState == BreathState.STABILIZED;
    }
}