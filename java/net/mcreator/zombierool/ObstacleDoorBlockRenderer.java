package net.mcreator.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity; 
// import net.mcreator.zombierool.init.ZombieroolModBlocks; // Plus besoin d'importer ObstacleDoorBlock ici
// import net.minecraft.world.level.block.Blocks; // Plus besoin de Blocks.STONE ou IRON_BLOCK

public class ObstacleDoorBlockRenderer implements BlockEntityRenderer<ObstacleDoorBlockEntity> {

    public ObstacleDoorBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
	public void render(ObstacleDoorBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
	    Block blockToRender = entity.getCopiedBlock(); 

        // ATTENTION: Ce renderer NE DOIT ÊTRE APPELÉ QUE si HAS_COPIED_BLOCK est TRUE.
        // Si blockToRender est null ici, c'est une incohérence logique.
	    if (blockToRender == null) {
            // Cela ne devrait JAMAIS arriver si la logique de getRenderShape est correcte.
            // Si cela arrive, il y a un problème de synchronisation majeur ou de logique.
            // System.err.println("ERREUR GRAVE RENDERER: ObstacleDoorBlockRenderer appelé avec copiedBlock null! Le BlockState HAS_COPIED_BLOCK doit être FALSE.");
            return; // Ne pas essayer de rendre un bloc null
	    } else {
           //  System.out.println("DEBUG RENDERER: Rendu du bloc copié : " + blockToRender.getName().getString()); 
        }
	
	    BlockState stateToRender = blockToRender.defaultBlockState(); 
	    BlockPos pos = entity.getBlockPos(); 
	    BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
	
	    poseStack.pushPose();
	
	    dispatcher.renderBatched(
	        stateToRender, 
	        pos,
	        Minecraft.getInstance().level != null ? Minecraft.getInstance().level : entity.getLevel(), 
	        poseStack,
	        buffer.getBuffer(RenderType.solid()), 
	        false, 
	        Minecraft.getInstance().level != null ? Minecraft.getInstance().level.random : net.minecraft.util.RandomSource.create()
	    );
	
	    poseStack.popPose();
	}
}
