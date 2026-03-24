package me.cryo.zombierool.client.model;

import me.cryo.zombierool.core.manager.GoreManager;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public class ModelCrawler<T extends LivingEntity> extends SpiderModel<T> {

    public final ModelPart head;
    public final ModelPart leftFrontLeg;
    public final ModelPart rightFrontLeg;

    public ModelCrawler(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.leftFrontLeg = root.getChild("left_front_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD)) {
            this.head.visible = false;
        } else {
            this.head.visible = true;
        }

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.LEFT_ARM)) {
            this.leftFrontLeg.visible = false;
        } else {
            this.leftFrontLeg.visible = true;
        }

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.RIGHT_ARM)) {
            this.rightFrontLeg.visible = false;
        } else {
            this.rightFrontLeg.visible = true;
        }
    }
}