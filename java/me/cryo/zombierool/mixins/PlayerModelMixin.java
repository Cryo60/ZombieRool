package me.cryo.zombierool.mixins;

import me.cryo.zombierool.client.ThirdPersonAnimHandler;
import me.cryo.zombierool.client.animation.ZRAnimationManager.ZRAnimationState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void zr_setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        
        ZRAnimationState state = ThirdPersonAnimHandler.getAnim(player.getUUID());
        if (state == null || !state.isPlaying()) return;

        org.joml.Vector3f pPos = state.hasBone("player")
                ? state.getPos("player")
                : new org.joml.Vector3f();

        applyTransform(model.head,       state, "head",      0.0F,  0.0F, 0.0F, pPos);
        copyTransform(model.hat,         model.head);
        
        applyTransform(model.body,       state, "body",      0.0F,  0.0F, 0.0F, pPos);
        copyTransform(model.jacket,      model.body);
        
        applyTransform(model.rightArm,   state, "right_arm", -5.0F, 2.0F, 0.0F, pPos);
        copyTransform(model.rightSleeve, model.rightArm);
        
        applyTransform(model.leftArm,    state, "left_arm",   5.0F, 2.0F, 0.0F, pPos);
        copyTransform(model.leftSleeve,  model.leftArm);
        
        applyTransform(model.rightLeg,   state, "right_leg", -1.9F, 12.0F, 0.0F, pPos);
        copyTransform(model.rightPants,  model.rightLeg);
        
        applyTransform(model.leftLeg,    state, "left_leg",   1.9F, 12.0F, 0.0F, pPos);
        copyTransform(model.leftPants,   model.leftLeg);
    }

    private void applyTransform(ModelPart part, ZRAnimationState state, String boneName,
                                 float defX, float defY, float defZ,
                                 org.joml.Vector3f pPos) {
        if (!state.hasBone(boneName)) {
            if (state.hasBone("player")) {
                part.x = defX - pPos.x();
                part.y = defY - pPos.y();
                part.z = defZ + pPos.z();
            }
            return;
        }

        org.joml.Vector3f pos = state.getPos(boneName); 
        org.joml.Vector3f rot = state.getRot(boneName); 

        // CORRIGÉ - On applique strictement les valeurs du JSON sans négation artificielle
        part.xRot = (float) Math.toRadians(rot.x());
        part.yRot = (float) Math.toRadians(rot.y());
        part.zRot = (float) Math.toRadians(rot.z());

        float dx = pos.x() + pPos.x();
        float dy = pos.y() + pPos.y();
        float dz = pos.z() + pPos.z();

        part.x = defX - dx;
        part.y = defY - dy;
        part.z = defZ + dz;
    }

    private void copyTransform(ModelPart layer, ModelPart parent) {
        layer.xRot = parent.xRot;
        layer.yRot = parent.yRot;
        layer.zRot = parent.zRot;
        layer.x    = parent.x;
        layer.y    = parent.y;
        layer.z    = parent.z;
    }
}