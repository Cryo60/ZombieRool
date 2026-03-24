package me.cryo.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.client.model.Modelwolf;
import me.cryo.zombierool.core.manager.DynamicResourceManager;

public class HellhoundRenderer extends MobRenderer<HellhoundEntity,Modelwolf<HellhoundEntity>> {
    public HellhoundRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Modelwolf<>(ctx.bakeLayer(Modelwolf.LAYER_LOCATION)), 0.75f);
        this.addLayer(new EyesLayer<HellhoundEntity,Modelwolf<HellhoundEntity>>(this) {
            @Override
            public void render(PoseStack ms, MultiBufferSource buffers, int packedLight,
                               HellhoundEntity entity, float limbSwing, float limbSwingAmt,
                               float partialTicks, float ageInTicks,
                               float netHeadYaw, float headPitch) {
                if (!entity.isRevealedClient()) {
                    return;
                }
                ResourceLocation customEye = DynamicResourceManager.getClientSkin("hellhound_eyes", entity.getCustomSkin());
                RenderType renderType = customEye != null ? RenderType.eyes(customEye) : RenderType.eyes(new ResourceLocation("zombierool:textures/entities/dark_hellhound_eyes.png"));
                com.mojang.blaze3d.vertex.VertexConsumer vertexconsumer = buffers.getBuffer(renderType);
                this.getParentModel().renderToBuffer(ms, vertexconsumer, 15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }

            @Override
            public RenderType renderType() {
                return RenderType.eyes(new ResourceLocation("zombierool:textures/entities/dark_hellhound_eyes.png"));
            }
        });
    }

    @Override
    public void render(HellhoundEntity entity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        if (!entity.isRevealedClient()) return;
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HellhoundEntity entity) {
        String customSkin = entity.getCustomSkin();
        if (customSkin != null && !customSkin.isEmpty()) {
            ResourceLocation dyn = DynamicResourceManager.getClientSkin("hellhound", customSkin);
            if (dyn != null) return dyn;
        }
        return new ResourceLocation("zombierool:textures/entities/carrie-the-dark-puppy.png");
    }

    @Override
    protected void scale(HellhoundEntity e, PoseStack ms, float pt) {
        float s = e.getScale();
        ms.scale(1.5F * s, 1.2F * s, 1.5F * s);
    }
}