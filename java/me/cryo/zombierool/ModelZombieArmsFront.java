package me.cryo.zombierool.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.HalloweenManager;
import me.cryo.zombierool.core.manager.GoreManager; // Import

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

        // --- GESTION DU GORE ---
        // Vérifie les tags NBT définis par GoreManager
        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD)) {
            this.head.visible = false;
            this.hat.visible = false;
        } else {
            this.head.visible = true;
            // On laisse le comportement par défaut pour le chapeau
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
        // -----------------------

        if (entity instanceof ZombieEntity zombie && zombie.isSuperSprinter()) {
            float bodyTilt = (float) Math.toRadians(20); 
            this.body.xRot = bodyTilt;
            
            // Si la tête est visible, on l'anime, sinon inutile
            if (this.head.visible) {
                this.head.xRot = headPitch * ((float) Math.PI / 180F) + (float) Math.PI / 16F;
            }

            if (!isHalloween) {
                // Si les bras sont visibles
                if(this.rightArm.visible) this.rightArm.xRot += (float) Math.PI / 10F;
                if(this.leftArm.visible) this.leftArm.xRot += (float) Math.PI / 10F;
            }

            float baseLegBend = (float) Math.PI / 10F;
            float legXRotCompensation = bodyTilt * 0.8F;
            this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 0.7F * limbSwingAmount + baseLegBend + legXRotCompensation;
            this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.7F * limbSwingAmount + baseLegBend + legXRotCompensation;
            
            float legZOffset = 2.5F;
            this.rightLeg.z = legZOffset;
            this.leftLeg.z = legZOffset;
        } else {
            this.rightLeg.z = 0.0F;
            this.leftLeg.z = 0.0F;
        }
    }
}