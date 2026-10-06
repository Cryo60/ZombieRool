package me.cryo.zombierool.client.model;

import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.core.manager.GoreManager;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.client.HalloweenManager;
import me.cryo.zombierool.client.ZombieRagdoll;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

public class ModelZombieArmsFront<T extends Mob> extends HumanoidModel<T> {
    public final ModelPart cranium;
    public final ModelPart jaw;
    public final ModelPart skullStump;
    public final ModelPart abdomen;
    public final ModelPart gutStump;
    public final ModelPart rightForearm;
    public final ModelPart rightHand;
    public final ModelPart rightElbowStump;
    public final ModelPart rightWristStump;
    public final ModelPart leftForearm;
    public final ModelPart leftHand;
    public final ModelPart leftElbowStump;
    public final ModelPart leftWristStump;

    public ModelZombieArmsFront(ModelPart root) {
        super(root);
        this.cranium = child(this.head, "cranium");
        this.jaw = child(this.head, "jaw");
        this.skullStump = child(this.head, "stump");
        this.abdomen = child(this.body, "abdomen");
        this.gutStump = child(this.body, "stump");
        this.rightForearm = child(this.rightArm, "forearm");
        this.rightHand = this.rightForearm == null ? null : child(this.rightForearm, "hand");
        this.rightElbowStump = child(this.rightArm, "stump");
        this.rightWristStump = this.rightForearm == null ? null : child(this.rightForearm, "stump");
        this.leftForearm = child(this.leftArm, "forearm");
        this.leftHand = this.leftForearm == null ? null : child(this.leftForearm, "hand");
        this.leftElbowStump = child(this.leftArm, "stump");
        this.leftWristStump = this.leftForearm == null ? null : child(this.leftForearm, "stump");
    }

    private static ModelPart child(ModelPart parent, String name) {
        return parent != null && parent.hasChild(name) ? parent.getChild(name) : null;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        boolean isHalloween = HalloweenManager.isHalloweenPeriod();
        boolean gore = !ZRClientConfig.isGoreReduced();
        boolean corpse = gore && (entity.deathTime > 0 || !entity.isAlive());

        if (!isHalloween && !corpse) {
            this.rightArm.xRot = (float) -Math.PI / 2F;
            this.leftArm.xRot = (float) -Math.PI / 2F;
            this.rightArm.yRot = 0.0F;
            this.leftArm.yRot = 0.0F;
        }

        boolean headGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD);
        boolean skullGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.SKULL);
        this.head.visible = !headGone;
        this.hat.visible = !headGone && !skullGone;
        if (this.cranium != null) this.cranium.visible = !skullGone;
        if (this.jaw != null) this.jaw.visible = true;
        if (this.skullStump != null) this.skullStump.visible = false;
        this.head.xScale = 1.0F;
        this.head.yScale = 1.0F;
        this.head.zScale = 1.0F;

        boolean rightGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_ARM);
        boolean leftGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_ARM);
        boolean rightForeGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_FOREARM);
        boolean leftForeGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_FOREARM);
        boolean rightHandGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_HAND);
        boolean leftHandGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_HAND);
        this.rightArm.visible = !rightGone;
        this.leftArm.visible = !leftGone;
        if (this.rightForearm != null) this.rightForearm.visible = !rightForeGone;
        if (this.leftForearm != null) this.leftForearm.visible = !leftForeGone;
        if (this.rightHand != null) this.rightHand.visible = !rightHandGone && !rightForeGone;
        if (this.leftHand != null) this.leftHand.visible = !leftHandGone && !leftForeGone;
        if (this.rightElbowStump != null) this.rightElbowStump.visible = false;
        if (this.leftElbowStump != null) this.leftElbowStump.visible = false;
        if (this.rightWristStump != null) this.rightWristStump.visible = false;
        if (this.leftWristStump != null) this.leftWristStump.visible = false;
        this.rightArm.yScale = 1.0F;
        this.leftArm.yScale = 1.0F;

        boolean torsoGone = gore && GoreManager.hasLostLimb(entity, GoreManager.Limb.TORSO);
        if (this.abdomen != null) this.abdomen.visible = !torsoGone;
        if (this.gutStump != null) this.gutStump.visible = false;
        this.body.xScale = 1.0F;
        this.body.yScale = 1.0F;
        this.body.zScale = 1.0F;

        boolean noLeftLeg = GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_LEG);
        boolean noRightLeg = GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_LEG);
        boolean isCrawler = noLeftLeg && noRightLeg;

        this.leftLeg.visible = !noLeftLeg;
        this.rightLeg.visible = !noRightLeg;

        float yOffset = isCrawler ? 10.0F : 0.0F;
        if (torsoGone && !isCrawler) yOffset += 6.0F;
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
        } else if (entity instanceof ZombieEntity zombie && zombie.isSuperSprinter() && !corpse) {
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
            this.rightLeg.z = 2.5F;
            this.leftLeg.z = 2.5F;
        } else {
            this.body.xRot = 0.0f;
            this.rightLeg.z = 0.0F;
            this.leftLeg.z = 0.0F;
        }

        if (corpse && !isCrawler) {
            ZombieRagdoll.Pose ragdoll = ZombieRagdoll.get(entity);
            if (ragdoll != null) {
                float t = ragdoll.settle;
                this.rightArm.xRot = Mth.lerp(t, this.rightArm.xRot, ragdoll.rArmRest);
                this.rightArm.zRot = Mth.lerp(t, this.rightArm.zRot, ragdoll.rArmZRest);
                this.leftArm.xRot = Mth.lerp(t, this.leftArm.xRot, ragdoll.lArmRest);
                this.leftArm.zRot = Mth.lerp(t, this.leftArm.zRot, ragdoll.lArmZRest);
                if (this.rightForearm != null) this.rightForearm.xRot = Mth.lerp(t, this.rightForearm.xRot, ragdoll.foreRest);
                if (this.leftForearm != null) this.leftForearm.xRot = Mth.lerp(t, this.leftForearm.xRot, ragdoll.foreRest * 0.75F);
                if (this.head.visible) {
                    this.head.xRot = Mth.lerp(t, this.head.xRot, ragdoll.headXRest);
                    this.head.zRot = Mth.lerp(t, this.head.zRot, ragdoll.headZRest);
                }
                this.rightLeg.xRot = Mth.lerp(t, this.rightLeg.xRot, ragdoll.rLegRest);
                this.leftLeg.xRot = Mth.lerp(t, this.leftLeg.xRot, ragdoll.lLegRest);
                this.body.xRot = Mth.lerp(t, this.body.xRot, 0.0F);
            }
        } else {
            this.head.zRot = 0.0F;
            if (this.rightForearm != null) {
                this.rightForearm.xRot = 0.0F;
                this.rightForearm.yRot = 0.0F;
                this.rightForearm.zRot = 0.0F;
            }
            if (this.leftForearm != null) {
                this.leftForearm.xRot = 0.0F;
                this.leftForearm.yRot = 0.0F;
                this.leftForearm.zRot = 0.0F;
            }
            if (this.rightHand != null) this.rightHand.xRot = 0.0F;
            if (this.leftHand != null) this.leftHand.xRot = 0.0F;
        }
        this.hat.copyFrom(this.head);
    }

    public boolean showElbowStump(Mob entity, boolean right) {
        if (ZRClientConfig.isGoreReduced()) return false;
        boolean arm = GoreManager.hasLostLimb(entity, right ? GoreManager.Limb.RIGHT_ARM : GoreManager.Limb.LEFT_ARM);
        boolean fore = GoreManager.hasLostLimb(entity, right ? GoreManager.Limb.RIGHT_FOREARM : GoreManager.Limb.LEFT_FOREARM);
        return !arm && fore;
    }

    public boolean showWristStump(Mob entity, boolean right) {
        if (ZRClientConfig.isGoreReduced()) return false;
        boolean arm = GoreManager.hasLostLimb(entity, right ? GoreManager.Limb.RIGHT_ARM : GoreManager.Limb.LEFT_ARM);
        boolean fore = GoreManager.hasLostLimb(entity, right ? GoreManager.Limb.RIGHT_FOREARM : GoreManager.Limb.LEFT_FOREARM);
        boolean hand = GoreManager.hasLostLimb(entity, right ? GoreManager.Limb.RIGHT_HAND : GoreManager.Limb.LEFT_HAND);
        return !arm && !fore && hand;
    }

    public boolean showGutStump(Mob entity) {
        return !ZRClientConfig.isGoreReduced() && GoreManager.hasLostLimb(entity, GoreManager.Limb.TORSO);
    }

    public boolean showSkullStump(Mob entity) {
        return !ZRClientConfig.isGoreReduced()
                && !GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD)
                && GoreManager.hasLostLimb(entity, GoreManager.Limb.SKULL);
    }
}
