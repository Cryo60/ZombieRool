package me.cryo.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.client.model.ModelZombieArmsFront;
import me.cryo.zombierool.client.model.ZombieLimbModel;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.core.manager.DynamicResourceManager;

public class ZombieRenderer extends HumanoidMobRenderer<ZombieEntity, ModelZombieArmsFront<ZombieEntity>> {

    private static final ResourceLocation[] ZOMBIE_SKINS = {
        new ResourceLocation("zombierool", "textures/entities/zombie_32_0.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_1.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_2.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_3.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_4.png")
    };

    private static final ResourceLocation EYE_TEXTURE_DEFAULT = new ResourceLocation("zombierool", "textures/entities/zombie_e_32.png");
    private static ResourceLocation currentEyeTexture = EYE_TEXTURE_DEFAULT; 

    public ZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new ModelZombieArmsFront<>(context.bakeLayer(ZombieLimbModel.LAYER)), 0.5f);
        
        this.addLayer(new HumanoidArmorLayer<>(
            this,
            new ModelZombieArmsFront<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
            new ModelZombieArmsFront<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
            context.getModelManager()
        ));

        this.addLayer(new EyesLayer<ZombieEntity, ModelZombieArmsFront<ZombieEntity>>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(currentEyeTexture);
            }
            
            @Override
            public void render(PoseStack pMatrixStack, MultiBufferSource pBuffer, int pPackedLight, ZombieEntity pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTicks, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
                if (!pLivingEntity.isAlive() || pLivingEntity.deathTime > 0) return;
                ResourceLocation customEye = DynamicResourceManager.getClientSkin("zombie_eyes", pLivingEntity.getEyeSkinId());
                RenderType renderType = customEye != null ? RenderType.eyes(customEye) : RenderType.eyes(currentEyeTexture);
                com.mojang.blaze3d.vertex.VertexConsumer vertexconsumer = pBuffer.getBuffer(renderType);
                this.getParentModel().renderToBuffer(pMatrixStack, vertexconsumer, 15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
        
        this.addLayer(new EmissivePumpkinHeadLayer<>(this));
        this.addLayer(new GoreWoundLayer(this));
        this.addLayer(new CharredLayer(this));
    }

    public static ResourceLocation skin(int index) {
        return ZOMBIE_SKINS[Math.floorMod(index, ZOMBIE_SKINS.length)];
    }

    @Override
    protected void scale(ZombieEntity entity, PoseStack poseStack, float partialTickTime) {
        float s = entity.getScale();
        if (s != 1.0f) {
            poseStack.scale(s, s, s);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieEntity entity) {
        String customSkin = entity.getCustomSkin();
        if (customSkin != null && !customSkin.isEmpty()) {
            ResourceLocation dyn = DynamicResourceManager.getClientSkin("zombie", customSkin);
            if (dyn != null) return dyn;
        }
        return skin(entity.getId());
    }

    @Override
    public void render(ZombieEntity entity, float yaw, float partialTicks, PoseStack matrixStack,
                       MultiBufferSource buffer, int packedLight) {
        this.shadowRadius = entity.deathTime > 0 || !entity.isAlive() ? 0.0F : 0.5F;
        if (entity.isDeadOrDying() && entity.isHeadshotDeath() && !ZRClientConfig.isGoreReduced()) {
            boolean headVisible = this.model.head.visible;
            boolean hatVisible = this.model.hat.visible;
            this.model.head.visible = false;
            this.model.hat.visible = false;
            super.render(entity, yaw, partialTicks, matrixStack, buffer, packedLight);
            this.model.head.visible = headVisible;
            this.model.hat.visible = hatVisible;
        } else {
            super.render(entity, yaw, partialTicks, matrixStack, buffer, packedLight);
        }
    }

    @Override
    protected void setupRotations(ZombieEntity entity, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        if (entity.deathTime > 0 && !ZRClientConfig.isGoreReduced()) {
            me.cryo.zombierool.client.ZombieRagdoll.Pose ragdoll = me.cryo.zombierool.client.ZombieRagdoll.get(entity);
            float t = ragdoll == null ? 1.0F : ragdoll.settle;
            float twist = ragdoll == null ? 0.0F : ragdoll.yawTwist();
            float flip = ragdoll == null ? 90.0F : ragdoll.flipDegrees();
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw + twist * t));
            pose.mulPose(Axis.ZP.rotationDegrees(flip * t));
            return;
        }
        super.setupRotations(entity, pose, ageInTicks, rotationYaw, partialTick);
    }

    public static void setEyeTexture(String preset) {
        switch (preset.toLowerCase()) {
            case "red":
                currentEyeTexture = new ResourceLocation("zombierool", "textures/entities/zombie_e_32_red.png");
                break;
            case "blue":
                currentEyeTexture = new ResourceLocation("zombierool", "textures/entities/zombie_e_32_blue.png");
                break;
            case "green":
                currentEyeTexture = new ResourceLocation("zombierool", "textures/entities/zombie_e_32_green.png");
                break;
            case "default":
            default:
                currentEyeTexture = EYE_TEXTURE_DEFAULT;
                break;
        }
    }

    private static final class CharredLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<ZombieEntity, ModelZombieArmsFront<ZombieEntity>> {
        private final ZombieRenderer parent;

        private CharredLayer(ZombieRenderer parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, ZombieEntity entity, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            if (!entity.isCharred()) return;
            var consumer = buffer.getBuffer(RenderType.entityTranslucent(this.parent.getTextureLocation(entity)));
            this.getParentModel().renderToBuffer(pose, consumer, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0.05F, 0.04F, 0.04F, 0.94F);
        }
    }

    private static final class GoreWoundLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<ZombieEntity, ModelZombieArmsFront<ZombieEntity>> {
        private static final ResourceLocation FLESH = new ResourceLocation("zombierool", "textures/entities/gore_flesh.png");

        private GoreWoundLayer(ZombieRenderer parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int light, ZombieEntity entity,
                           float limbSwing, float limbSwingAmount, float partial, float age, float netHeadYaw, float headPitch) {
            ModelZombieArmsFront<ZombieEntity> model = this.getParentModel();
            var consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(FLESH));
            int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            if (model.showElbowStump(entity, true) && model.rightElbowStump != null) {
                drawStump(pose, model.rightArm, null, model.rightElbowStump, consumer, light, overlay);
            }
            if (model.showElbowStump(entity, false) && model.leftElbowStump != null) {
                drawStump(pose, model.leftArm, null, model.leftElbowStump, consumer, light, overlay);
            }
            if (model.showWristStump(entity, true) && model.rightWristStump != null && model.rightForearm != null) {
                drawStump(pose, model.rightArm, model.rightForearm, model.rightWristStump, consumer, light, overlay);
            }
            if (model.showWristStump(entity, false) && model.leftWristStump != null && model.leftForearm != null) {
                drawStump(pose, model.leftArm, model.leftForearm, model.leftWristStump, consumer, light, overlay);
            }
            if (model.showGutStump(entity) && model.gutStump != null) {
                drawStump(pose, model.body, null, model.gutStump, consumer, light, overlay);
            }
            if (model.showSkullStump(entity) && model.skullStump != null) {
                drawStump(pose, model.head, null, model.skullStump, consumer, light, overlay);
            }
        }

        private static void drawStump(PoseStack pose, net.minecraft.client.model.geom.ModelPart parent,
                                      net.minecraft.client.model.geom.ModelPart mid,
                                      net.minecraft.client.model.geom.ModelPart stump,
                                      com.mojang.blaze3d.vertex.VertexConsumer consumer, int light, int overlay) {
            boolean was = stump.visible;
            stump.visible = true;
            pose.pushPose();
            parent.translateAndRotate(pose);
            if (mid != null) mid.translateAndRotate(pose);
            stump.render(pose, consumer, light, overlay);
            pose.popPose();
            stump.visible = was;
        }
    }
}
