package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;

public class BlueFireRenderer {
    public static final Material SOUL_FIRE_0 = new Material(InventoryMenu.BLOCK_ATLAS, new ResourceLocation("block/soul_fire_0"));
    public static final Material SOUL_FIRE_1 = new Material(InventoryMenu.BLOCK_ATLAS, new ResourceLocation("block/soul_fire_1"));

    public static void renderFlame(PoseStack pPoseStack, MultiBufferSource pBuffer, Entity pEntity, Quaternionf pQuaternion) {
        TextureAtlasSprite sprite0 = SOUL_FIRE_0.sprite();
        TextureAtlasSprite sprite1 = SOUL_FIRE_1.sprite();

        pPoseStack.pushPose();
        float f = pEntity.getBbWidth() * 1.4F;
        pPoseStack.scale(f, f, f);
        float f1 = 0.5F;
        float f2 = 0.0F;
        float f3 = pEntity.getBbHeight() / f;
        float f4 = 0.0F;
        pPoseStack.mulPose(pQuaternion);
        pPoseStack.translate(0.0F, 0.0F, -0.3F + (float)((int)f3) * 0.02F);
        float f5 = 0.0F;
        int i = 0;

        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));

        for (PoseStack.Pose pose = pPoseStack.last(); f3 > 0.0F; ++i) {
            TextureAtlasSprite sprite = i % 2 == 0 ? sprite0 : sprite1;
            float f6 = sprite.getU0();
            float f7 = sprite.getV0();
            float f8 = sprite.getU1();
            float f9 = sprite.getV1();
            if (i / 2 % 2 == 0) {
                float f10 = f8;
                f8 = f6;
                f6 = f10;
            }

            vertex(vertexconsumer, pose, f1 - 0.0F, 0.0F - f4, f5, f8, f9);
            vertex(vertexconsumer, pose, -f1 - 0.0F, 0.0F - f4, f5, f6, f9);
            vertex(vertexconsumer, pose, -f1 - 0.0F, 1.4F - f4, f5, f6, f7);
            vertex(vertexconsumer, pose, f1 - 0.0F, 1.4F - f4, f5, f8, f7);
            f3 -= 0.45F;
            f4 -= 0.45F;
            f1 *= 0.9F;
            f5 += 0.03F;
        }

        pPoseStack.popPose();
    }

    private static void vertex(VertexConsumer pConsumer, PoseStack.Pose pPose, float pX, float pY, float pZ, float pU, float pV) {
        pConsumer.vertex(pPose.pose(), pX, pY, pZ)
                 .color(255, 255, 255, 255)
                 .uv(pU, pV)
                 .overlayCoords(OverlayTexture.NO_OVERLAY)
                 .uv2(240, 240) // Rendu très brillant
                 .normal(pPose.normal(), 0.0F, 1.0F, 0.0F)
                 .endVertex();
    }
}