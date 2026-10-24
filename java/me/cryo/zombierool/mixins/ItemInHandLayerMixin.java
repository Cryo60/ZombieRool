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
        if(me.cryo.zombierool.gameplay.PortableEquipment.inHands(player)){ci.cancel();return;}
        String animName = ThirdPersonAnimHandler.currentAnim(player.getUUID());
        if (animName == null || !ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID())) return;

        ci.cancel();
        // The physical prop leaves the hand at the same tick as its server spawn.
        if(animName.endsWith("_throw")&&ThirdPersonAnimHandler.animationTick(player.getUUID())>=5)return;
        if(animName.endsWith("_place")&&ThirdPersonAnimHandler.animationTick(player.getUUID())>=8)return;
        if(animName.equals("equipment_cancel")||animName.equals("knuckle_crack"))return;
        if(me.cryo.zombierool.client.LunarEquipmentClient.helmetAnimation(player)){
            if(!me.cryo.zombierool.client.LunarEquipmentClient.showProp(player))return;
            PlayerModel<?> helmetPlayerModel=(PlayerModel<?>)((ItemInHandLayer<?,?>)(Object)this).getParentModel();
            poseStack.pushPose();helmetPlayerModel.translateToHand(HumanoidArm.RIGHT,poseStack);
            poseStack.translate(-.16,.55,-.05);poseStack.mulPose(Axis.XP.rotationDegrees(-95));poseStack.scale(.85f,.85f,.85f);
            if(me.cryo.zombierool.gameplay.LunarEquipment.style(player)!=me.cryo.zombierool.gameplay.LunarEquipment.SuitStyle.LUNAR)poseStack.mulPose(Axis.YP.rotationDegrees(180));
            me.cryo.zombierool.client.LunarEquipmentClient.renderHelmet(poseStack,buffer,packedLight,player);poseStack.popPose();return;
        }
        ItemStack itemToRender = propFor(player, animName);
        if (itemToRender.isEmpty()) return;

        PlayerModel<?> model = (PlayerModel<?>) ((ItemInHandLayer<?, ?>) (Object) this).getParentModel();
        boolean leftKnife=false;
        poseStack.pushPose();
        model.translateToHand(leftKnife?HumanoidArm.LEFT:HumanoidArm.RIGHT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate((leftKnife?-1.0F:1.0F) / 16.0F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, itemToRender, leftKnife?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, leftKnife, poseStack, buffer, player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
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
        if(animName.equals("c4_detonate"))return new ItemStack(me.cryo.zombierool.core.registry.ZRThrowableRegistry.C4_DETONATOR.get());
        for(var kind:me.cryo.zombierool.gameplay.EquipmentSystem.Kind.values())
            if(animName.startsWith(kind.id+"_"))return new ItemStack(me.cryo.zombierool.core.registry.ZRThrowableRegistry.loadoutItem(kind));
        if ("knife_sweep".equals(animName)||"bowie_sweep".equals(animName)) {
            boolean hasBowie = animName.equals("bowie_sweep")||player.getPersistentData().getBoolean("zr_has_bowie_knife");
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
