package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.client.ThirdPersonAnimHandler;
import me.cryo.zombierool.client.animation.ZRAnimationManager.ZRAnimationState;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    public void onRender(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        ZRAnimationState state = ThirdPersonAnimHandler.getAnim(player.getUUID());
        if (state == null || !state.isPlaying()) return;

        ci.cancel();

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

        if (itemToRender.isEmpty()) return;

        PlayerModel<?> model = (PlayerModel<?>) ((ItemInHandLayer<?, ?>) (Object) this).getParentModel();
        
        // --- Main Hand Rendering ---
        poseStack.pushPose();
        model.translateToHand(HumanoidArm.RIGHT, poseStack);
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

        Minecraft.getInstance().getItemRenderer().renderStatic(
                player,
                itemToRender,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false,
                poseStack,
                buffer,
                player.level(),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                player.getId()
        );
        poseStack.popPose();

        // --- Left Hand (Lighter for Molotov) ---
        if (animName.equals("molotov_light") && state.hasBone("left_arm")) {
            poseStack.pushPose();
            model.translateToHand(HumanoidArm.LEFT, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate(-0.0625F, 0.125F, -0.625F); 
            
            Minecraft.getInstance().getItemRenderer().renderStatic(
                player, 
                new ItemStack(ZRRegistry.ANIM_LIGHTER), 
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                false, 
                poseStack, 
                buffer, 
                player.level(), 
                packedLight, 
                OverlayTexture.NO_OVERLAY, 
                player.getId()
            );
            poseStack.popPose();
        }
    }
}