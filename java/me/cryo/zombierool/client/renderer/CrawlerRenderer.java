package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.client.ZombieRagdoll;
import me.cryo.zombierool.client.model.ModelCrawler;
import me.cryo.zombierool.configuration.ZRClientConfig;
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
        super(context, new ModelCrawler<>(context.bakeLayer(ModelLayers.SPIDER)), 0.7f);
        this.addLayer(new EyesLayer<CrawlerEntity, ModelCrawler<CrawlerEntity>>(this) {
            @Override
            public void render(PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, CrawlerEntity pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
                if (!pLivingEntity.isAlive() || pLivingEntity.deathTime > 0) return;
                ResourceLocation customEye = DynamicResourceManager.getClientSkin("crawler_eyes", pLivingEntity.getEyeSkinId());
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
        this.addLayer(new CharredLayer(this));
    }

    private static final class CharredLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<CrawlerEntity, ModelCrawler<CrawlerEntity>> {
        private final CrawlerRenderer parent;

        private CharredLayer(CrawlerRenderer parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, CrawlerEntity entity, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            if (!entity.isCharred()) return;
            var consumer = buffer.getBuffer(RenderType.entityTranslucent(this.parent.getTextureLocation(entity)));
            this.getParentModel().renderToBuffer(pose, consumer, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0.05F, 0.04F, 0.04F, 0.94F);
        }
    }

    @Override
    protected float getFlipDegrees(CrawlerEntity entity) {
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
        if (entity.deathTime > 0) {
            ZombieRagdoll.Pose ragdoll = ZombieRagdoll.get(entity);
            float t = ragdoll == null ? 1.0F : ragdoll.settle;
            float twist = ragdoll == null ? 0.0F : ragdoll.yawTwist();
            float flip = Math.floorMod(entity.getUUID().hashCode(),5) == 0 ? 75.0F : 0.0F;
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw + twist * t));
            poseStack.mulPose(Axis.ZP.rotationDegrees(flip * t));
            return;
        }
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
        this.shadowRadius = entity.deathTime > 0 || !entity.isAlive() ? 0.0F : 0.7F;
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