package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Layer de rendu pour ajouter un effet émissif (yeux lumineux) à la BLACK_PUMPKIN
 * uniquement lorsqu'elle est portée par un zombie.
 * Affiche la texture émissive uniquement sur la face avant (nord) du casque.
 */
@OnlyIn(Dist.CLIENT)
public class EmissivePumpkinHeadLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    
    // Texture émissive des yeux de la citrouille
    private static final ResourceLocation EMISSIVE_TEXTURE = 
        new ResourceLocation("zombierool", "textures/block/black_pumpkin_e.png");
    
    public EmissivePumpkinHeadLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }
    
    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, 
                      T entity, float limbSwing, float limbSwingAmount, float partialTicks,
                      float ageInTicks, float netHeadYaw, float headPitch) {
        
        // Vérifier si l'entité porte un casque
        ItemStack headItem = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.isEmpty()) {
            return;
        }
        
        // Vérifier si le casque a le tag NBT indiquant qu'il doit être émissif
        if (!headItem.hasTag() || !headItem.getTag().getBoolean("zombierool:emissive_pumpkin")) {
            return;
        }
        
        M model = this.getParentModel();
        
        poseStack.pushPose();
        
        // Se positionner sur la tête du zombie
        model.head.translateAndRotate(poseStack);
        
        // Ajustements pour correspondre à la position d'un bloc casque
        // Les casques blocs sont rendus à une échelle de 0.625 et décalés
        poseStack.translate(0.0D, -0.25D, 0.0D);
        poseStack.scale(0.625F, -0.625F, -0.625F);
        
        // Créer le RenderType émissif
        RenderType renderType = RenderType.eyes(EMISSIVE_TEXTURE);
        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
        
        PoseStack.Pose pose = poseStack.last();
        int fullBright = 15728880; // Lumière maximale
        
        // Taille du bloc (légèrement plus grande pour éviter z-fighting)
        float size = 0.501F;
        
        // Face Z+ (face AVANT du zombie - celle avec les yeux)
        addQuad(pose, vertexConsumer, 
            -size, -size, size,    // Bottom left
            size, -size, size,     // Bottom right
            size, size, size,      // Top right
            -size, size, size,     // Top left
            fullBright);
        
        poseStack.popPose();
    }
    
    /**
     * Ajoute un quad (face carrée) au buffer de rendu avec couleur personnalisée (pour debug)
     */
    private void addQuadColored(PoseStack.Pose pose, VertexConsumer consumer,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        float x3, float y3, float z3,
                        float x4, float y4, float z4,
                        int light, int r, int g, int b) {
        
        consumer.vertex(pose.pose(), x1, y1, z1)
                .color(r, g, b, 255)
                .uv(0, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
        
        consumer.vertex(pose.pose(), x2, y2, z2)
                .color(r, g, b, 255)
                .uv(1, 1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
        
        consumer.vertex(pose.pose(), x3, y3, z3)
                .color(r, g, b, 255)
                .uv(1, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
        
        consumer.vertex(pose.pose(), x4, y4, z4)
                .color(r, g, b, 255)
                .uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
    }
    
    /**
     * Ajoute un quad (face carrée) au buffer de rendu
     */
    private void addQuad(PoseStack.Pose pose, VertexConsumer consumer,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        float x3, float y3, float z3,
                        float x4, float y4, float z4,
                        int light) {
        
        // Vertex 1 (bottom-left)
        consumer.vertex(pose.pose(), x1, y1, z1)
                .color(255, 255, 255, 255)
                .uv(0, 1)  // Coordonnées UV : coin bas-gauche
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
        
        // Vertex 2 (bottom-right)
        consumer.vertex(pose.pose(), x2, y2, z2)
                .color(255, 255, 255, 255)
                .uv(1, 1)  // Coordonnées UV : coin bas-droit
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
        
        // Vertex 3 (top-right)
        consumer.vertex(pose.pose(), x3, y3, z3)
                .color(255, 255, 255, 255)
                .uv(1, 0)  // Coordonnées UV : coin haut-droit
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
        
        // Vertex 4 (top-left)
        consumer.vertex(pose.pose(), x4, y4, z4)
                .color(255, 255, 255, 255)
                .uv(0, 0)  // Coordonnées UV : coin haut-gauche
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0, 0, 1)
                .endVertex();
    }
}