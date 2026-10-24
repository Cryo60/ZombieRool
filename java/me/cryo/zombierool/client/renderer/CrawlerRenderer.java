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
        if(entity.isAlive()&&entity.getEmergenceTicks()>0)poseStack.translate(0,entity.getBbHeight()*Math.max(0,(entity.getEmergenceTicks()-partialTickTime)/(double)me.cryo.zombierool.gameplay.SpawnEmergence.DURATION),0);
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
    public net.minecraft.world.phys.Vec3 getRenderOffset(CrawlerEntity entity,float partial){return super.getRenderOffset(entity,partial).add(CerberusCorpsePose.offset(entity,partial));}
    @Override
    protected void setupRotations(CrawlerEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        if(entity.isCerberusFeeding()){
            CerberusCorpsePose.rotate(entity,poseStack,partialTicks);
            return;
        }
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
        var flight=me.cryo.zombierool.gameplay.TraversalMotion.flight(entity);
        if(flight!=null&&flight.style==2){
            // The vanilla renderer flips Y and translates by -1.501 afterwards.
            // Align the spider belly (model Y=19/16), rather than its feet, to the wall.
            poseStack.translate(0,0,-(.5+(1.501-19.0/16)*entity.getScale()-.035));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));
        }

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
            if(me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod())return new ResourceLocation("zombierool:textures/entities/halloween_crawler.png");
        String customSkin = entity.getCustomSkin();
        if (customSkin != null && !customSkin.isEmpty()) {
            ResourceLocation dyn = DynamicResourceManager.getClientSkin("crawler", customSkin);
            if (dyn != null) return dyn;
        }
        return new ResourceLocation("zombierool:textures/entities/crawler.png");
    }

    @Override
    public void render(CrawlerEntity entity, float yaw, float partialTicks, PoseStack matrixStack,
                       MultiBufferSource buffer, int packedLight) {
        buffer=CerberusCorpsePose.eatingBuffer(entity,matrixStack,partialTicks,buffer);
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
