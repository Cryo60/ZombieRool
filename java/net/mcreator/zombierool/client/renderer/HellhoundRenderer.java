
package net.mcreator.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.client.model.Modelwolf;

public class HellhoundRenderer extends MobRenderer<HellhoundEntity,Modelwolf<HellhoundEntity>> {
    public HellhoundRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Modelwolf<>(ctx.bakeLayer(Modelwolf.LAYER_LOCATION)), 0.75f);

        // couche EYES modifiée pour tenir compte de la sync data
        this.addLayer(new EyesLayer<HellhoundEntity,Modelwolf<HellhoundEntity>>(this) {
            @Override
            public void render(PoseStack ms, MultiBufferSource buffers, int packedLight,
                               HellhoundEntity entity, float limbSwing, float limbSwingAmt,
                               float partialTicks, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                // SI PAS RÉVÉLÉ → on skip le rendu des yeux
                if (!entity.isRevealedClient()) {
                    return;
                }
                super.render(ms, buffers, packedLight,
                             entity, limbSwing, limbSwingAmt,
                             partialTicks, ageInTicks,
                             netHeadYaw, headPitch);
            }
            @Override
            public RenderType renderType() {
                return RenderType.eyes(new ResourceLocation("zombierool:textures/entities/dark_hellhound_eyes.png"));
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(HellhoundEntity e) {
        return new ResourceLocation("zombierool:textures/entities/carrie-the-dark-puppy.png");
    }

    @Override
    protected void scale(HellhoundEntity e, PoseStack ms, float pt) {
        ms.scale(1.5F,1.2F,1.5F);
    }
}
