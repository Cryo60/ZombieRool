package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.client.model.ModelCrawler;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import me.cryo.zombierool.entity.CrawlerEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CrawlerRenderer extends MobRenderer<CrawlerEntity, ModelCrawler<CrawlerEntity>> {
    
    public CrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, new ModelCrawler<>(context.bakeLayer(ModelLayers.SPIDER)), 0.5f);
        this.addLayer(new EyesLayer<CrawlerEntity, ModelCrawler<CrawlerEntity>>(this) {
            @Override
            public void render(PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, CrawlerEntity pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
                ResourceLocation customEye = DynamicResourceManager.getClientSkin("crawler_eyes", pLivingEntity.getCustomSkin());
                if (customEye != null) {
                    RenderType renderType = RenderType.eyes(customEye);
                    com.mojang.blaze3d.vertex.VertexConsumer vertexconsumer = pBuffer.getBuffer(renderType);
                    this.getParentModel().renderToBuffer(pMatrixStack, vertexconsumer, 15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
            @Override
            public RenderType renderType() {
                return RenderType.eyes(new ResourceLocation("minecraft:textures/entity/spider_eyes.png"));
            }
        });
    }

    @Override
    protected float getFlipDegrees(CrawlerEntity entity) {
        if (entity.isExplodingDeath()) {
            return 0.0f; // Bloque la rotation sur le côté ("mort classique")
        }
        return super.getFlipDegrees(entity);
    }

    @Override
    protected void scale(CrawlerEntity entity, PoseStack poseStack, float partialTickTime) {
        float s = entity.getScale();
        
        if (entity.isExplodingDeath() && entity.deathTime > 0) {
            float progress = ((float)entity.deathTime + partialTickTime) / 45.0f;
            if (progress > 1.0f) progress = 1.0f;
            float swell = 1.0f + (progress * 0.35f); // Gonfle de 35% avant d'exploser
            poseStack.scale(s * swell, s * swell, s * swell);
        } else if (s != 1.0f) {
            poseStack.scale(s, s, s);
        }
    }

    @Override
    protected void setupRotations(CrawlerEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        
        if (entity.isExplodingDeath() && entity.deathTime > 0) {
            float progress = ((float)entity.deathTime + partialTicks) / 45.0f;
            if (progress > 1.0f) progress = 1.0f;
            
            float shakeIntensity = progress * 5.0f; 
            float shakeX = Mth.sin(ageInTicks * 3.0f) * shakeIntensity;
            float shakeY = Mth.cos(ageInTicks * 3.5f) * shakeIntensity;
            
            poseStack.mulPose(Axis.YP.rotationDegrees(shakeY));
            poseStack.mulPose(Axis.XP.rotationDegrees(shakeX));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CrawlerEntity entity) {
        String customSkin = entity.getCustomSkin();
        if (customSkin != null && !customSkin.isEmpty()) {
            ResourceLocation dyn = DynamicResourceManager.getClientSkin("crawler", customSkin);
            if (dyn != null) return dyn;
        }
        if (entity.hasHalloweenSkin()) {
            return new ResourceLocation("zombierool:textures/entities/halloween_crawler.png");
        }
        return new ResourceLocation("zombierool:textures/entities/crawler.png");
    }

    @Override
    public void render(CrawlerEntity entity, float yaw, float partialTicks, PoseStack matrixStack,
                       MultiBufferSource buffer, int packedLight) {
        // Si c'est un headshot (qui fait imploser la tête)
        if (entity.isDeadOrDying() && entity.isHeadshotDeath()) {
            boolean headVisible = this.model.head.visible;
            this.model.head.visible = false;
            super.render(entity, yaw, partialTicks, matrixStack, buffer, packedLight);
            this.model.head.visible = headVisible;
        } else {
            super.render(entity, yaw, partialTicks, matrixStack, buffer, packedLight);
        }
    }
}