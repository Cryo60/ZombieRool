package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DrinkPerkAnimationHandler {

    private static int animationTimer = 0;
    private static Runnable onFinish = null;
    private static final int DURATION_TICKS = 70; // Allongé à 3.5 sec
    private static int blurTimer = 0;
    private static final int BLUR_DURATION = 15; 

    public static void startAnimation(Runnable whenDone) {
        if (animationTimer <= 0 && blurTimer <= 0) {
            animationTimer = DURATION_TICKS;
            onFinish = whenDone;
        }
    }

    public static boolean isRunning() {
        return animationTimer > 0 || blurTimer > 0; 
    }

    public static void reset() {
        animationTimer = 0;
        blurTimer = 0;
        onFinish = null;
    }

    private static float easeOutQuad(float x) {
        return 1.0f - (1.0f - x) * (1.0f - x);
    }

    private static float easeInOutQuad(float x) {
        return x < 0.5f ? 2.0f * x * x : 1.0f - (float)Math.pow(-2.0f * x + 2.0f, 2.0) / 2.0f;
    }

    private static float easeInQuad(float x) {
        return x * x;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return; 

        if (animationTimer > 0) {
            animationTimer--;

            Minecraft mc = Minecraft.getInstance();
            if (animationTimer == 40 && mc.player != null) {
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.BOTTLE_FILL,
                    SoundSource.PLAYERS, 1f, 1.5f, false
                );
            }
            if (animationTimer == 35 && mc.player != null) {
                mc.level.playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_glook")),
                    SoundSource.PLAYERS, 1f, 1f, false
                );
            }
            if (animationTimer == 0) {
                if (mc.player != null) {
                    mc.level.playLocalSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_rot")),
                        SoundSource.PLAYERS, 1f, 1f, false
                    );
                }
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
            Minecraft mc = Minecraft.getInstance();
            // Laisse le ThirdPersonAnimHandler faire son travail s'il est actif
            if (mc.player != null && ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID())) {
                return; 
            }

            event.setCanceled(true); 

            if (event.getHand() == InteractionHand.MAIN_HAND && animationTimer > 0) {
                PoseStack poseStack = event.getPoseStack();
                MultiBufferSource buffer = event.getMultiBufferSource();
                int light = event.getPackedLight();

                float progress = 1.0f - ((float) animationTimer / DURATION_TICKS);

                poseStack.pushPose();

                if (progress < 0.2f) { 
                    float t = easeOutQuad(progress / 0.2f);
                    poseStack.translate(0.5 - (t * 0.5), -0.8 + (t * 0.5), -0.5);
                    poseStack.mulPose(Axis.XP.rotationDegrees(t * 10f));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(t * -10f));
                } else if (progress < 0.3f) { 
                    float t = (progress - 0.2f) / 0.1f;
                    float jolt = (float) Math.sin(t * Math.PI) * 0.1f;
                    poseStack.translate(0.0, -0.3 - jolt, -0.5);
                    poseStack.mulPose(Axis.XP.rotationDegrees(10f));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-10f));
                } else if (progress < 0.5f) { 
                    float t = easeInOutQuad((progress - 0.3f) / 0.2f);
                    poseStack.translate(0.0, -0.3 + (t * 0.1), -0.5 + (t * 0.2));
                    poseStack.mulPose(Axis.XP.rotationDegrees(10f + t * 60f));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-10f));
                } else if (progress < 0.8f) { 
                    float t = (progress - 0.5f) / 0.3f;
                    float wobble = (float) Math.sin(t * Math.PI * 6) * 0.02f;
                    poseStack.translate(0.0, -0.2 + wobble, -0.3);
                    poseStack.mulPose(Axis.XP.rotationDegrees(70f + wobble * 100f));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-10f));
                } else { 
                    float t = easeInQuad((progress - 0.8f) / 0.2f);
                    poseStack.translate(0.0, -0.2 - (t * 0.8), -0.3 - (t * 0.2));
                    poseStack.mulPose(Axis.XP.rotationDegrees(70f - (t * 40f)));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-10f + (t * 10f)));
                }

                ItemStack bottleStack = new ItemStack(ZRRegistry.ANIM_BOTTLE);
                mc.getItemRenderer().renderStatic(
                    bottleStack, 
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, 
                    light, 
                    OverlayTexture.NO_OVERLAY, 
                    poseStack, 
                    buffer, 
                    mc.level, 
                    0
                );

                poseStack.popPose();
            }
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