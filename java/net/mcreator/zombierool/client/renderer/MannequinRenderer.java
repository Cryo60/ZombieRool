
package net.mcreator.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import net.mcreator.zombierool.entity.MannequinEntity;
import net.mcreator.zombierool.client.model.Modelmannequin;

public class MannequinRenderer extends MobRenderer<MannequinEntity, Modelmannequin<MannequinEntity>> {
	public MannequinRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelmannequin(context.bakeLayer(Modelmannequin.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(MannequinEntity entity) {
		return new ResourceLocation("zombierool:textures/entities/armor_stand_small.png");
	}
}
