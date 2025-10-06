package net.mcreator.zombierool.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.HalloweenManager;

public class ModelZombieArmsFront<T extends Mob> extends HumanoidModel<T> {
    public ModelZombieArmsFront(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Vérifie si c'est la période Halloween
        boolean isHalloween = HalloweenManager.isHalloweenPeriod();

        // Bras en avant de base (zombie arms front) - SEULEMENT si ce n'est PAS Halloween
        if (!isHalloween) {
            this.rightArm.xRot = (float) -Math.PI / 2F;
            this.leftArm.xRot = (float) -Math.PI / 2F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
        }

        if (entity instanceof ZombieEntity zombie && zombie.isSuperSprinter()) {
            // Incline le torse
            float bodyTilt = (float) Math.toRadians(20); // ≈ 20° vers l'avant
            this.body.xRot = bodyTilt;

            // Ajuste la tête pour qu'elle reste tournée vers l'avant malgré l'inclinaison
            this.head.xRot = headPitch * ((float) Math.PI / 180F) + (float) Math.PI / 16F;

            // Ajuste les bras pour aller un peu plus loin - SEULEMENT si ce n'est PAS Halloween
            if (!isHalloween) {
                this.rightArm.xRot += (float) Math.PI / 10F;
                this.leftArm.xRot += (float) Math.PI / 10F;
            }

            // Jambes fléchies + mouvement de course
            float baseLegBend = (float) Math.PI / 10F;
            // Adjust leg rotation to compensate for body tilt
            float legXRotCompensation = bodyTilt * 0.8F;

            this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 0.7F * limbSwingAmount + baseLegBend + legXRotCompensation;
            this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.7F * limbSwingAmount + baseLegBend + legXRotCompensation;

            // Recule les jambes sur l'axe Z
            float legZOffset = 2.5F;
            this.rightLeg.z = legZOffset;
            this.leftLeg.z = legZOffset;
        } else {
            // Reset leg Z position when not in super sprinter mode
            this.rightLeg.z = 0.0F;
            this.leftLeg.z = 0.0F;
        }
    }
}