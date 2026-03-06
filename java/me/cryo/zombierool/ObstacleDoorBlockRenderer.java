package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.system.MimicSystem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

public class ObstacleDoorBlockRenderer implements BlockEntityRenderer<ObstacleDoorBlockEntity> {
	
	public ObstacleDoorBlockRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	public void render(ObstacleDoorBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
	    BlockState mimicState = entity.getMimic();
	    if (mimicState != null) {
	        poseStack.pushPose();
	        BlockPos pos = entity.getBlockPos();
	        int light = LevelRenderer.getLightColor(entity.getLevel(), pos);
	        MimicSystem.renderMimic(mimicState, pos, entity.getLevel(), poseStack, buffer, light, combinedOverlay);
	        poseStack.popPose();
	    }
	}
}