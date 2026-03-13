package me.cryo.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.client.model.ModelZombieArmsFront;
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
        super(context, new ModelZombieArmsFront<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
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
        });
        this.addLayer(new EmissivePumpkinHeadLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieEntity entity) {
        String customSkin = entity.getCustomSkin();
        if (customSkin != null && !customSkin.isEmpty()) {
            ResourceLocation dyn = DynamicResourceManager.getClientSkin("zombie", customSkin);
            if (dyn != null) return dyn;
        }
        int skinIndex = Math.abs(entity.getId() % ZOMBIE_SKINS.length);
        return ZOMBIE_SKINS[skinIndex];
    }

    @Override
    public void render(ZombieEntity entity, float yaw, float partialTicks, PoseStack matrixStack,
                       MultiBufferSource buffer, int packedLight) {
       if (entity.isDeadOrDying() && entity.isHeadshotDeath()) {
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
}