package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.cryo.zombierool.client.animation.ZRAnimationManager;
import me.cryo.zombierool.client.animation.ZRAnimationManager.ZRAnimationState;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ThirdPersonAnimHandler {
    public static final Map<UUID, ZRAnimationState> activeAnims = new ConcurrentHashMap<>();
    public static float debugX = 0.1f, debugY = -0.4f, debugZ = -0.1f;

    public static void startAnim(UUID uuid, String type, int ticks) {
        String animName = type.equals("melee") ? "knife_sweep" : type;
        ZRAnimationManager.ZRAnimation anim = ZRAnimationManager.getAnimation(animName);
        if (anim != null) {
            ZRAnimationState state = new ZRAnimationState(anim);
            state.start(null);
            activeAnims.put(uuid, state);
        }
    }

    public static ZRAnimationState getAnim(UUID uuid) {
        return activeAnims.get(uuid);
    }

    public static boolean isAnimationPlaying(UUID uuid) {
        ZRAnimationState state = activeAnims.get(uuid);
        return state != null && state.isPlaying();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        activeAnims.values().removeIf(state -> {
            state.tick();
            return !state.isPlaying();
        });
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        Player player = Minecraft.getInstance().player;
        if (player != null && isAnimationPlaying(player.getUUID())) {
            ZRAnimationState state = activeAnims.get(player.getUUID());
            if (state.hasBone("head")) {
                Vector3f headRot = state.getRot("head");
                event.setPitch(event.getPitch() + headRot.x());
                event.setYaw(event.getYaw() + headRot.y());
                event.setRoll(event.getRoll() - headRot.z());
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && isAnimationPlaying(player.getUUID())) {
            if (mc.options.keyAttack.matches(event.getKey(), event.getScanCode()) ||
                mc.options.keyUse.matches(event.getKey(), event.getScanCode()) ||
                mc.options.keyDrop.matches(event.getKey(), event.getScanCode()) ||
                mc.options.keySwapOffhand.matches(event.getKey(), event.getScanCode())) {
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && isAnimationPlaying(player.getUUID())) {
            if (mc.options.keyAttack.matchesMouse(event.getButton()) ||
                mc.options.keyUse.matchesMouse(event.getButton())) {
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && isAnimationPlaying(player.getUUID())) {
            if (event.isCancelable()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        ZRAnimationState state = activeAnims.get(player.getUUID());
        if (state != null && state.isPlaying()) {
            event.setCanceled(true);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                renderFirstPerson(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player, state);
            }
        }
    }

    private static void renderFirstPerson(PoseStack poseStack, MultiBufferSource buffer, int light,
                                           Player player, ZRAnimationState state) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer clientPlayer = (AbstractClientPlayer) player;
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(clientPlayer);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, 0.60F, 0.0F);
        poseStack.translate(debugX, debugY, debugZ);

        Vector3f pPos = state.hasBone("player") ? state.getPos("player") : new Vector3f();

        Vector3f armPos = state.hasBone("right_arm") ? state.getPos("right_arm") : new Vector3f();
        Vector3f armRot = state.hasBone("right_arm") ? state.getRot("right_arm") : new Vector3f();
        model.rightArm.xRot = (float) Math.toRadians(armRot.x());
        model.rightArm.yRot = (float) Math.toRadians(armRot.y()); 
        model.rightArm.zRot = (float) Math.toRadians(armRot.z());
        model.rightArm.x = -5.0F - armPos.x() - pPos.x();
        model.rightArm.y = 2.0F - armPos.y() - pPos.y();
        model.rightArm.z = 0.0F + armPos.z() + pPos.z();
        model.rightSleeve.copyFrom(model.rightArm);

        Vector3f armLPos = state.hasBone("left_arm") ? state.getPos("left_arm") : new Vector3f();
        Vector3f armLRot = state.hasBone("left_arm") ? state.getRot("left_arm") : new Vector3f();
        model.leftArm.xRot = (float) Math.toRadians(armLRot.x());
        model.leftArm.yRot = (float) Math.toRadians(armLRot.y()); 
        model.leftArm.zRot = (float) Math.toRadians(armLRot.z());
        model.leftArm.x = 5.0F - armLPos.x() - pPos.x();
        model.leftArm.y = 2.0F - armLPos.y() - pPos.y();
        model.leftArm.z = 0.0F + armLPos.z() + pPos.z();
        model.leftSleeve.copyFrom(model.leftArm);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(clientPlayer.getSkinTextureLocation()));

        model.rightArm.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
        model.rightSleeve.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);

        if (state.hasBone("left_arm")) {
            model.leftArm.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
            model.leftSleeve.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);
        }

        poseStack.pushPose();
        model.rightArm.translateAndRotate(poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(1.0F / 16.0F, 0.125F, -0.625F);

        if (state.hasBone("Objet")) {
            Vector3f objPos = state.getPos("Objet");
            Vector3f objRot = state.getRot("Objet");
            poseStack.translate(-objPos.x() / 16f, -objPos.y() / 16f, objPos.z() / 16f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(objRot.z()));
            poseStack.mulPose(Axis.YP.rotationDegrees(objRot.y()));
            poseStack.mulPose(Axis.XP.rotationDegrees(objRot.x()));
        }

        ItemStack itemToRender = player.getMainHandItem();
        String animName = state.getAnimation().name;
        if (animName.equals("knife_sweep")) {
            boolean hasBowie = player.getPersistentData().getBoolean("zr_has_bowie_knife");
            itemToRender = new ItemStack(hasBowie ? ZRRegistry.BOWIE_KNIFE : ZRRegistry.ANIM_KNIFE);
        } else if (animName.equals("drink") || animName.equals("drink_perk")) {
            itemToRender = new ItemStack(ZRRegistry.ANIM_BOTTLE);
        } else if (animName.startsWith("stielhandgranate")) {
            itemToRender = new ItemStack(ZRRegistry.ANIM_STIELHANDGRANATE);
        } else if (animName.startsWith("monkey_bomb")) {
            itemToRender = new ItemStack(ZRRegistry.ANIM_MONKEY_BOMB);
        } else if (animName.startsWith("grenade")) {
            itemToRender = new ItemStack(ZRRegistry.ANIM_GRENADE);
        } else if (animName.startsWith("molotov")) {
            itemToRender = new ItemStack(ZRRegistry.ANIM_MOLOTOV);
        }

        if (!itemToRender.isEmpty()) {
            Minecraft.getInstance().getItemRenderer().renderStatic(
                itemToRender,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                player.level(),
                0
            );
        }

        poseStack.popPose();

        if (animName.equals("molotov_light") && state.hasBone("left_arm")) {
            poseStack.pushPose();
            model.leftArm.translateAndRotate(poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate(-0.0625F, 0.125F, -0.625F); 
            Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(ZRRegistry.ANIM_LIGHTER), 
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                light, 
                OverlayTexture.NO_OVERLAY, 
                poseStack, 
                buffer, 
                player.level(), 
                0
            );
            poseStack.popPose();
        }

        poseStack.popPose(); 
    }
}