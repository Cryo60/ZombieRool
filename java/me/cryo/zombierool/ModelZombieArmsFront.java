package me.cryo.zombierool.client.model;

import me.cryo.zombierool.core.manager.GoreManager;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.HalloweenManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

public class ModelZombieArmsFront<T extends Mob> extends HumanoidModel<T> {

    public ModelZombieArmsFront(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        boolean isHalloween = HalloweenManager.isHalloweenPeriod();

        if (!isHalloween) {
            this.rightArm.xRot = (float) -Math.PI / 2F;
            this.leftArm.xRot = (float) -Math.PI / 2F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
        }

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD)) {
            this.head.visible = false;
            this.hat.visible = false;
        } else {
            this.head.visible = true;
            this.hat.visible = true;
        }

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_ARM)) {
            this.leftArm.visible = false;
        } else {
            this.leftArm.visible = true;
        }

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_ARM)) {
            this.rightArm.visible = false;
        } else {
            this.rightArm.visible = true;
        }

        boolean noLeftLeg = GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_LEG);
        boolean noRightLeg = GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_LEG);
        boolean isCrawler = noLeftLeg && noRightLeg;

        this.leftLeg.visible = !noLeftLeg;
        this.rightLeg.visible = !noRightLeg;

        float yOffset = isCrawler ? 10.0F : 0.0F;
        this.head.y = yOffset;
        this.hat.y = yOffset;
        this.body.y = yOffset;
        this.leftArm.y = 2.0F + yOffset;
        this.rightArm.y = 2.0F + yOffset;

        if (isCrawler) {
            this.body.xRot = 0.4f;
            
            if (this.rightArm.visible) {
                this.rightArm.xRot = -1.0F + Mth.cos(limbSwing * 0.6662F) * 0.5F * limbSwingAmount;
            }
            if (this.leftArm.visible) {
                this.leftArm.xRot = -1.0F + Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.5F * limbSwingAmount;
            }
            
            this.rightLeg.z = 0.0F;
            this.leftLeg.z = 0.0F;
        } else if (entity instanceof ZombieEntity zombie && zombie.isSuperSprinter()) {
            float bodyTilt = (float) Math.toRadians(20); 
            this.body.xRot = bodyTilt;
            
            if (this.head.visible) {
                this.head.xRot = headPitch * ((float) Math.PI / 180F) + (float) Math.PI / 16F;
            }

            if (!isHalloween) {
                if (this.rightArm.visible) this.rightArm.xRot += (float) Math.PI / 10F;
                if (this.leftArm.visible) this.leftArm.xRot += (float) Math.PI / 10F;
            }

            float baseLegBend = (float) Math.PI / 10F;
            float legXRotCompensation = bodyTilt * 0.8F;

            if (this.rightLeg.visible) {
                this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 0.7F * limbSwingAmount + baseLegBend + legXRotCompensation;
            }
            if (this.leftLeg.visible) {
                this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.7F * limbSwingAmount + baseLegBend + legXRotCompensation;
            }

            float legZOffset = 2.5F;
            this.rightLeg.z = legZOffset;
            this.leftLeg.z = legZOffset;
        } else {
            this.body.xRot = 0.0f;
            this.rightLeg.z = 0.0F;
            this.leftLeg.z = 0.0F;
        }
    }
}