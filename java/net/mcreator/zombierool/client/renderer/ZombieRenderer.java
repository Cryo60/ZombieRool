package net.mcreator.zombierool.client.renderer;

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
import com.mojang.math.Axis;

import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.client.model.ModelZombieArmsFront;

// Plus besoin de ces imports car le gestionnaire de paquet prend le relais
// import net.minecraftforge.eventbus.api.SubscribeEvent;
// import net.minecraftforge.fml.common.Mod;
// import net.minecraftforge.api.distmarker.Dist;
// import net.minecraftforge.api.distmarker.OnlyIn;
// import net.minecraftforge.client.event.ClientChatReceivedEvent;

// Cette classe n'a plus besoin d'être un EventBusSubscriber si elle ne gère pas d'autres événements spécifiques au bus MOD/FORGE
// @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ZombieRenderer extends HumanoidMobRenderer<ZombieEntity, ModelZombieArmsFront<ZombieEntity>> {

    private static final ResourceLocation[] ZOMBIE_SKINS = {
        new ResourceLocation("zombierool", "textures/entities/zombie_32_0.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_1.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_2.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_3.png"),
        new ResourceLocation("zombierool", "textures/entities/zombie_32_4.png")
    };

    private static final ResourceLocation EYE_TEXTURE_RED = new ResourceLocation("zombierool", "textures/entities/zombie_e_32_red.png");
    private static final ResourceLocation EYE_TEXTURE_BLUE = new ResourceLocation("zombierool", "textures/entities/zombie_e_32_blue.png");
    private static final ResourceLocation EYE_TEXTURE_GREEN = new ResourceLocation("zombierool", "textures/entities/zombie_e_32_green.png");
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
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieEntity entity) {
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

    // Méthode publique statique pour définir la texture d'œil, appelée par le NetworkHandler
    public static void setEyeTexture(String preset) {
        switch (preset.toLowerCase()) {
            case "red":
                currentEyeTexture = EYE_TEXTURE_RED;
                break;
            case "blue":
                currentEyeTexture = EYE_TEXTURE_BLUE;
                break;
            case "green":
                currentEyeTexture = EYE_TEXTURE_GREEN;
                break;
            case "default":
            default:
                currentEyeTexture = EYE_TEXTURE_DEFAULT;
                break;
        }
    }
}