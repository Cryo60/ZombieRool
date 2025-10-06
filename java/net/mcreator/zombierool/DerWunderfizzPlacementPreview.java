package net.mcreator.zombierool.client.renderer;

import net.mcreator.zombierool.block.DerWunderfizzBlock; // Import du bloc de base
import net.mcreator.zombierool.block.DerWunderfizzUpperBlock; // Import du bloc supérieur
import net.mcreator.zombierool.block.WunderfizzAntenneBlock; // Import du bloc d'antenne
import net.mcreator.zombierool.init.ZombieroolModBlocks; // Pour accéder aux instances de vos blocs

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DerWunderfizzPlacementPreview {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // We want to render AFTER the level rendering, but BEFORE the final UI/debug passes.
        // RenderLevelStageEvent.Stage.AFTER_PARTICLES is a good insertion point for effects.
        // If this does not work as expected, you can try RenderLevelStageEvent.Stage.AFTER_WEATHER or RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_TERRAIN.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        // Check if player or level are null
        if (player == null || level == null) {
            return;
        }

        // Check if the player is holding a DerWunderfizzBlock item
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() != ZombieroolModBlocks.DER_WUNDERFIZZ.get().asItem()) {
            return;
        }

        // Get the hit result (where the player is looking)
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos clickedPos = blockHitResult.getBlockPos();
        Direction clickedFace = blockHitResult.getDirection();

        // Calculate the main block position (where the base DerWunderfizzBlock will be placed)
        // This is the block adjacent to the clicked face.
        BlockPos mainPos = clickedPos.relative(clickedFace);

        // Determine the player's facing direction for correct block orientation
        // The block's facing is the opposite of the player's horizontal direction.
        Direction facing = player.getDirection().getOpposite();

        // Calculate the positions for the upper and antenna parts
        BlockPos upperPos = mainPos.above();
        BlockPos antennaPos = mainPos.above(2);

        // Check if all three positions are replaceable (i.e., not occupied by a solid block)
        if (!level.getBlockState(mainPos).canBeReplaced() || !level.getBlockState(upperPos).canBeReplaced() || !level.getBlockState(antennaPos).canBeReplaced()) {
            return;
        }

        // Prepare the render pose stack
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose(); // Save the current pose stack state

        // Translate the render relative to the player's camera
        double camX = mc.gameRenderer.getMainCamera().getPosition().x;
        double camY = mc.gameRenderer.getMainCamera().getPosition().y;
        double camZ = mc.gameRenderer.getMainCamera().getPosition().z;
        poseStack.translate(-camX, -camY, -camZ);

        // Render the ghost block for the base DerWunderfizzBlock
        renderGhostBlock(poseStack, level, mainPos,
                ZombieroolModBlocks.DER_WUNDERFIZZ.get().defaultBlockState()
                        .setValue(DerWunderfizzBlock.FACING, facing), // Set facing for the base
                0.5f); // Alpha for transparency

        // Render the ghost block for the upper DerWunderfizzUpperBlock
        renderGhostBlock(poseStack, level, upperPos,
                ZombieroolModBlocks.DER_WUNDERFIZZ_UPPER.get().defaultBlockState()
                        .setValue(DerWunderfizzUpperBlock.FACING, facing), // Set facing for the upper part
                0.5f);

        // Render the ghost block for the WunderfizzAntenneBlock
        renderGhostBlock(poseStack, level, antennaPos,
                ZombieroolModBlocks.WUNDERFIZZ_ANTENNE.get().defaultBlockState(), // Antenna block has no FACING property
                0.5f);

        poseStack.popPose(); // Restore the previous pose stack state
    }

    /**
     * Renders a transparent ghost block at a given position.
     * @param poseStack The PoseStack for render transformations.
     * @param level The current level.
     * @param pos The position of the block to render.
     * @param state The block state (to get shape and orientation).
     * @param alpha The transparency level (0.0f to 1.0f).
     */
    private static void renderGhostBlock(PoseStack poseStack, Level level, BlockPos pos, BlockState state, float alpha) {
        // Configure the render system for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // Default blending function (SrcAlpha, OneMinusSrcAlpha)
        RenderSystem.disableCull(); // Disable face culling so all faces are rendered (including those normally hidden)
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha); // Set global shader color, including alpha

        // Get the RenderType for translucent blocks.
        // This is crucial as it defines the necessary render state (shaders, textures, blending, etc.).
        RenderType renderType = RenderType.translucent();
        // Apply the render state defined by the RenderType.
        // This configures OpenGL with the correct parameters for translucent rendering.
        renderType.setupRenderState();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        // Start building the buffer with the POSITION_COLOR vertex format
        // Use VertexFormat.Mode.QUADS to draw quads (faces)
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Use the provided block position and state
        BlockPos actualPos = pos;
        BlockState renderState = state;

        // Get the collision shape (VoxelShape) of the block for rendering
        VoxelShape shape = renderState.getShape(level, actualPos);
        if (shape.isEmpty()) {
            shape = renderState.getCollisionShape(level, actualPos);
        }
        if (shape.isEmpty()) {
            shape = Shapes.block(); // If no shape is defined, use a full block shape
        }

        // Get the current transformation matrix from the PoseStack
        Matrix4f matrix = poseStack.last().pose();

        // Define color components for rendering (white with specified alpha)
        int r = 255;
        int g = 255;
        int b = 255;
        int a = (int) (alpha * 255); // Convert float alpha to int (0-255)

        // Iterate over all boxes that make up the block shape (a VoxelShape can be composed of multiple boxes)
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            // Calculate absolute coordinates of the box corners
            double dx1 = x1 + actualPos.getX();
            double dy1 = y1 + actualPos.getY();
            double dz1 = z1 + actualPos.getZ();
            double dx2 = x2 + actualPos.getX();
            double dy2 = y2 + actualPos.getY();
            double dz2 = z2 + actualPos.getZ();

            // Add vertices for each face of the box to the BufferBuilder
            // Each vertex has a position (x, y, z) and a color (r, g, b, a)
            // Front face (positive Z)
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();

            // Back face (negative Z)
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();

            // Left face (negative X)
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();

            // Right face (positive X)
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();

            // Top face (positive Y)
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy2, (float) dz1).color(r, g, b, a).endVertex();

            // Bottom face (negative Y)
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz1).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx2, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
            bufferBuilder.vertex(matrix, (float) dx1, (float) dy1, (float) dz2).color(r, g, b, a).endVertex();
        });

        // End buffer building and send vertices to the graphics card for rendering
        tesselator.end();

        // Reset render state after RenderType has been used.
        // This is crucial to avoid interfering with the rendering of subsequent elements in the game.
        renderType.clearRenderState();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // Reset shader color to opaque
        RenderSystem.enableCull(); // Re-enable face culling
        RenderSystem.disableBlend(); // Disable blending
    }
}
