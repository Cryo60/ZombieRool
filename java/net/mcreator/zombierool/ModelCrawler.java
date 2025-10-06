package net.mcreator.zombierool.client.model;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;

public class ModelCrawler<T extends net.minecraft.world.entity.LivingEntity> extends SpiderModel<T> {
    public final ModelPart head;

    public ModelCrawler(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
    }
}
