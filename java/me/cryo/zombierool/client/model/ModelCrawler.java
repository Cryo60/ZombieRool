package me.cryo.zombierool.client.model;

import me.cryo.zombierool.client.ZombieRagdoll;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.core.manager.GoreManager;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class ModelCrawler<T extends LivingEntity> extends SpiderModel<T> {

    public final ModelPart head;
    public final ModelPart leftFrontLeg;
    public final ModelPart rightFrontLeg;
    private final ModelPart abdomen;
    private final ModelPart[] legs;

    public ModelCrawler(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.abdomen = child(root, "body1");
        this.legs = new ModelPart[] {
                child(root, "right_hind_leg"),
                child(root, "left_hind_leg"),
                child(root, "right_middle_hind_leg"),
                child(root, "left_middle_hind_leg"),
                child(root, "right_middle_front_leg"),
                child(root, "left_middle_front_leg"),
                this.rightFrontLeg,
                this.leftFrontLeg
        };
    }

    private static ModelPart child(ModelPart parent, String name) {
        return parent.hasChild(name) ? parent.getChild(name) : null;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (!me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod() && GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD)) {
            this.head.visible = false;
        } else {
            this.head.visible = true;
        }

        if (!me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod() && GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_ARM)) {
            this.leftFrontLeg.visible = false;
        } else {
            this.leftFrontLeg.visible = true;
        }

        if (!me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod() && GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_ARM)) {
            this.rightFrontLeg.visible = false;
        } else {
            this.rightFrontLeg.visible = true;
        }

        boolean corpse = (entity.deathTime > 0 || !entity.isAlive()) && !ZRClientConfig.isGoreReduced();
        if (!corpse) {
            this.head.zRot = 0.0F;
            if (this.abdomen != null) this.abdomen.xRot = 0.0F;
            return;
        }
        ZombieRagdoll.Pose ragdoll = ZombieRagdoll.get(entity);
        if (ragdoll != null) this.collapse(ragdoll);
    }

    private void collapse(ZombieRagdoll.Pose ragdoll) {
        float t = ragdoll.settle;
        int style = ragdoll.style;
        float side = ragdoll.side;
        if (this.head.visible) {
            this.head.xRot = Mth.lerp(t, this.head.xRot, ragdoll.headXRest);
            this.head.yRot = Mth.lerp(t, this.head.yRot, ragdoll.headY);
            this.head.zRot = Mth.lerp(t, this.head.zRot, ragdoll.headZRest);
        }
        if (this.abdomen != null) {
            float bend = switch (style) {
                case 1 -> 0.5F;
                case 2 -> -0.35F;
                case 4 -> 0.75F;
                case 3 -> -0.2F;
                default -> 0.18F * side;
            };
            this.abdomen.xRot = Mth.lerp(t, this.abdomen.xRot, bend);
        }
        for (int i = 0; i < this.legs.length; i++) {
            ModelPart leg = this.legs[i];
            if (leg == null || !leg.visible) continue;
            boolean left = (i % 2) == 1;
            float sign = left ? 1.0F : -1.0F;
            float splay = switch (style) {
                case 1 -> 1.35F;
                case 2 -> 0.45F;
                case 3 -> 1.05F;
                case 4 -> 0.25F;
                case 5 -> 1.55F;
                default -> 0.7F;
            };
            float curl = switch (style) {
                case 1 -> left ? 0.35F : -0.2F;
                case 2 -> 1.35F;
                case 3 -> i < 4 ? -0.9F : 0.55F;
                case 4 -> 1.6F;
                case 5 -> left ? -0.7F : 0.85F;
                default -> i < 4 ? 0.15F : -0.45F;
            };
            leg.zRot = Mth.lerp(t, leg.zRot, sign * splay);
            leg.xRot = Mth.lerp(t, leg.xRot, curl);
            if (style == 0 || style == 5) {
                leg.yRot = Mth.lerp(t, leg.yRot, sign * (style == 5 ? 0.55F : 0.25F));
            }
        }
    }
}
