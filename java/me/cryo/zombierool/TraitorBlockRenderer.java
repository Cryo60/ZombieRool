package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import me.cryo.zombierool.block.entity.TraitorBlockEntity;
import me.cryo.zombierool.block.system.MimicSystem;
import net.minecraft.world.level.block.state.BlockState;

public class TraitorBlockRenderer implements BlockEntityRenderer<TraitorBlockEntity> {
	
	public TraitorBlockRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(TraitorBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
	    BlockState mimicState = entity.getMimic();
	    if (mimicState != null) {
	        poseStack.pushPose();
	        MimicSystem.renderMimic(mimicState, entity.getBlockPos(), entity.getLevel(), poseStack, buffer, combinedLight, combinedOverlay);
	        poseStack.popPose();
	    }
	}
}