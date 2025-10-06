package net.mcreator.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.mcreator.zombierool.block.entity.TraitorBlockEntity;

public class TraitorBlockRenderer implements BlockEntityRenderer<TraitorBlockEntity> {

    public TraitorBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
	public void render(TraitorBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
	    Block block = entity.getCopiedBlock();
	    if (block == null) return;
	
	    BlockState copiedState = block.defaultBlockState();
	    BlockPos pos = entity.getBlockPos();
	    BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
	
	    poseStack.pushPose();
	
	    // Translate vers la position "monde" pour que renderBatched fonctionne
	    poseStack.translate(0, 0, 0); // ← pas besoin de décalage ici car pos est déjà la position du BE
	
	    dispatcher.renderBatched(
	        copiedState,
	        pos,
	        Minecraft.getInstance().level,
	        poseStack,
	        buffer.getBuffer(RenderType.solid()),
	        false,
	        Minecraft.getInstance().level.random
	    );
	
	    poseStack.popPose();
	}
}

