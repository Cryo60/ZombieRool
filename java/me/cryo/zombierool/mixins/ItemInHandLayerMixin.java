package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.client.ThirdPersonAnimHandler;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    public void onRender(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        String animName = ThirdPersonAnimHandler.currentAnim(player.getUUID());
        if (animName == null || !ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID())) return;

        ci.cancel();
        ItemStack itemToRender = propFor(player, animName);
        if (itemToRender.isEmpty()) return;

        PlayerModel<?> model = (PlayerModel<?>) ((ItemInHandLayer<?, ?>) (Object) this).getParentModel();
        poseStack.pushPose();
        model.translateToHand(HumanoidArm.RIGHT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(1.0F / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, itemToRender, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, buffer, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
        poseStack.popPose();

        if ("molotov_light".equals(animName)) {
            poseStack.pushPose();
            model.translateToHand(HumanoidArm.LEFT, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate(-0.0625F, 0.125F, -0.625F);
            Minecraft.getInstance().getItemRenderer().renderStatic(player, new ItemStack(ZRRegistry.ANIM_LIGHTER), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, false, poseStack, buffer, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
            poseStack.popPose();
        }
    }

    private static ItemStack propFor(Player player, String animName) {
        if ("knife_sweep".equals(animName)) {
            boolean hasBowie = player.getPersistentData().getBoolean("zr_has_bowie_knife");
            return new ItemStack(hasBowie ? ZRRegistry.BOWIE_KNIFE : ZRRegistry.ANIM_KNIFE);
        }
        if ("drink".equals(animName) || "drink_perk".equals(animName)) return new ItemStack(ZRRegistry.ANIM_BOTTLE);
        if (animName.startsWith("stielhandgranate")) return new ItemStack(ZRRegistry.ANIM_STIELHANDGRANATE);
        if (animName.startsWith("monkey_bomb")) return new ItemStack(ZRRegistry.ANIM_MONKEY_BOMB);
        if (animName.startsWith("grenade")) return new ItemStack(ZRRegistry.ANIM_GRENADE);
        if (animName.startsWith("molotov")) return new ItemStack(ZRRegistry.ANIM_MOLOTOV);
        return player.getMainHandItem();
    }
}
