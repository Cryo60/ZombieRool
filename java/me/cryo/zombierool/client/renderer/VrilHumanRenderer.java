package me.cryo.zombierool.client.renderer;

import me.cryo.zombierool.entity.VrilHumanEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class VrilHumanRenderer extends HumanoidMobRenderer<VrilHumanEntity,HumanoidModel<VrilHumanEntity>> {
    public VrilHumanRenderer(EntityRendererProvider.Context context){super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),.5f);}
    @Override public ResourceLocation getTextureLocation(VrilHumanEntity entity){return new ResourceLocation("minecraft","textures/entity/player/wide/steve.png");}
}
