package me.cryo.zombierool.client.model;

import me.cryo.zombierool.core.manager.GoreManager;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public class ModelCrawler<T extends LivingEntity> extends SpiderModel<T> {
    public final ModelPart head;
    // SpiderModel a des arrays pour les jambes (leg0 ... leg7)
    // C'est un peu plus complexe à mapper LEFT_LEG/RIGHT_LEG à 8 pattes.
    // On simplifie : pas de démembrement jambes pour l'instant, juste la tête.

    public ModelCrawler(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (GoreManager.hasLostLimb(entity, GoreManager.Limb.HEAD)) {
            this.head.visible = false;
        } else {
            this.head.visible = true;
        }
    }
}