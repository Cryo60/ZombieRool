package me.cryo.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import me.cryo.zombierool.entity.DummyEntity;
import me.cryo.zombierool.client.model.Modelmannequin;

public class DummyRenderer extends MobRenderer<DummyEntity, Modelmannequin<DummyEntity>> {
    public DummyRenderer(EntityRendererProvider.Context context) {
        super(context, new Modelmannequin(context.bakeLayer(Modelmannequin.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(DummyEntity entity) {
        return new ResourceLocation("zombierool:textures/entities/armor_stand_small.png");
    }
}