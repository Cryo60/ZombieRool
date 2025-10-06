// src/main/java/net/mcreator/zombierool/client/renderer/BuyWallWeaponRenderer.java
package net.mcreator.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;

public class BuyWallWeaponRenderer implements BlockEntityRenderer<BuyWallWeaponBlockEntity> {
    public BuyWallWeaponRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BuyWallWeaponBlockEntity entity, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        // 1) Render du bloc capturé
        ResourceLocation rl = entity.getCapturedBlock();
        if (rl != null) {
            var block = ForgeRegistries.BLOCKS.getValue(rl);
            if (block != null) {
                var state = block.defaultBlockState();
                BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
                poseStack.pushPose();
                // Ajustez translation/scale si besoin
                poseStack.translate(0.0, 0.0, 0.0);
                dispatcher.renderSingleBlock(state, poseStack, buffer, combinedLight, combinedOverlay);
                poseStack.popPose();
            }
        }

        // 2) Render de l’item à vendre sur les 4 faces, comme un item frame
		if (entity.getItemToSell() != null) {
		    var item = ForgeRegistries.ITEMS.getValue(entity.getItemToSell());
		    if (item != null) {
		        var stack = new ItemStack(item);
		        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		
		        // On parcourt les 4 faces
		        for (Direction face : Direction.Plane.HORIZONTAL) {
		            poseStack.pushPose();
		            // Centré sur la face, légèrement au‑dessus du milieu du bloc (y = 0.5)
		            double x = 0.5 + face.getStepX() * 0.501;
		            double y = 0.5;
		            double z = 0.5 + face.getStepZ() * 0.501;
		            poseStack.translate(x, y, z);
		
		            // Tourne l’item pour qu’il regarde vers l’extérieur
		            float angle = switch(face) {
		                case NORTH -> 180f;
		                case SOUTH -> 0f;
		                case WEST  -> 90f;
		                case EAST  -> -90f;
		                default    -> 0f;
		            };
		            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
		
		            // Scale un peu plus grand
		            float s = 0.6f;
		            poseStack.scale(s, s, s);
		
		            itemRenderer.renderStatic(
		                stack,
		                ItemDisplayContext.FIXED,
		                combinedLight,
		                combinedOverlay,
		                poseStack,
		                buffer,
		                entity.getLevel(),
		                0
		            );
		            poseStack.popPose();
		        }
		    }
		}
    }
}
