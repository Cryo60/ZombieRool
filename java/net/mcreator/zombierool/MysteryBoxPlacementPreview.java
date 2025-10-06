package net.mcreator.zombierool.client.renderer;

import net.mcreator.zombierool.block.MysteryBoxBlock;
import net.mcreator.zombierool.MysteryBoxManager;
import net.mcreator.zombierool.init.ZombieroolModBlocks;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer; // Non utilisé mais gardé si besoin futur
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
public class MysteryBoxPlacementPreview {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Nous voulons rendre APRÈS le rendu du niveau, mais AVANT les dernières passes UI/debug.
        // RenderLevelStageEvent.Stage.AFTER_PARTICLES est un bon point d'insertion pour les effets.
        // Si cela ne fonctionne pas comme prévu, vous pouvez essayer RenderLevelStageEvent.Stage.AFTER_WEATHER ou RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_TERRAIN.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;

        // Vérifie si le joueur ou le monde sont nuls
        if (player == null || level == null) {
            return;
        }

        // Vérifie si le joueur tient un bloc Mystery Box
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() != ZombieroolModBlocks.MYSTERY_BOX.get().asItem()) {
            return;
        }

        // Récupère le résultat du hit (où le joueur vise)
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHitResult = (BlockHitResult) hitResult;
        BlockPos clickedPos = blockHitResult.getBlockPos();
        Direction clickedFace = blockHitResult.getDirection();

        // Calcule la position principale du bloc (celle où la prévisualisation sera rendue)
        // C'est le bloc adjacent à la face cliquée.
        BlockPos mainPos = clickedPos.relative(clickedFace);

        // Détermine la direction du joueur pour placer correctement le bloc
        // La Mystery Box est un bloc multi-parties qui s'oriente par rapport au joueur.
        Direction facing = player.getDirection().getOpposite(); // La direction de la box est l'opposé de celle du joueur

        // Calcule la position de l'autre partie du bloc Mystery Box
        BlockPos otherPartPos = MysteryBoxManager.getOtherPartPos(mainPos, facing);

        // Vérifie si les positions sont remplaçables (c'est-à-dire, pas occupées par un autre bloc solide)
        if (!level.getBlockState(mainPos).canBeReplaced() || !level.getBlockState(otherPartPos).canBeReplaced()) {
            return;
        }

        // Prépare la pile de matrices de rendu
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose(); // Sauvegarde l'état actuel de la pose stack

        // Translate le rendu pour qu'il soit relatif à la caméra du joueur
        double camX = mc.gameRenderer.getMainCamera().getPosition().x;
        double camY = mc.gameRenderer.getMainCamera().getPosition().y;
        double camZ = mc.gameRenderer.getMainCamera().getPosition().z;
        poseStack.translate(-camX, -camY, -camZ);

        // Rend le bloc fantôme pour la partie principale de la Mystery Box
        renderGhostBlock(poseStack, level, mainPos,
                ZombieroolModBlocks.MYSTERY_BOX.get().defaultBlockState()
                        .setValue(MysteryBoxBlock.FACING, facing)
                        .setValue(MysteryBoxBlock.PART, false), // false pour la partie principale
                0.5f); // Alpha pour la transparence

        // Rend le bloc fantôme pour l'autre partie de la Mystery Box
        renderGhostBlock(poseStack, level, otherPartPos,
                ZombieroolModBlocks.MYSTERY_BOX.get().defaultBlockState()
                        .setValue(MysteryBoxBlock.FACING, facing)
                        .setValue(MysteryBoxBlock.PART, true), // true pour l'autre partie
                0.5f);

        poseStack.popPose(); // Restaure l'état précédent de la pose stack
    }

    /**
     * Rend un bloc fantôme transparent à une position donnée.
     * @param poseStack La PoseStack pour les transformations de rendu.
     * @param level Le niveau actuel.
     * @param pos La position du bloc à rendre.
     * @param state L'état du bloc (pour obtenir la forme et l'orientation).
     * @param alpha Le niveau de transparence (0.0f à 1.0f).
     */
    private static void renderGhostBlock(PoseStack poseStack, Level level, BlockPos pos, BlockState state, float alpha) {
        // Configure le système de rendu pour la transparence
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // Fonction de blending par défaut (SrcAlpha, OneMinusSrcAlpha)
        RenderSystem.disableCull(); // Désactive le culling des faces pour que toutes les faces soient rendues (y compris celles qui seraient normalement cachées)
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha); // Définit la couleur du shader, y compris l'alpha global

        // Obtient le RenderType pour les blocs translucides.
        // C'est crucial car il définit l'état de rendu nécessaire (shaders, textures, blending, etc.).
        RenderType renderType = RenderType.translucent();
        // Applique l'état de rendu défini par le RenderType.
        // Cela configure OpenGL avec les bons paramètres pour le rendu translucide.
        renderType.setupRenderState();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        // Démarre la construction du buffer avec le format de vertex POSITION_COLOR
        // Utilise VertexFormat.Mode.QUADS pour dessiner des quads (faces)
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Utilise la position et l'état du bloc fournis
        BlockPos actualPos = pos;
        BlockState renderState = state;

        // Obtient la forme de collision (VoxelShape) du bloc pour le rendu
        VoxelShape shape = renderState.getShape(level, actualPos);
        if (shape.isEmpty()) {
            shape = renderState.getCollisionShape(level, actualPos);
        }
        if (shape.isEmpty()) {
            shape = Shapes.block(); // Si aucune forme n'est définie, utilise une forme de bloc pleine
        }

        // Récupère la matrice de transformation actuelle de la PoseStack
        Matrix4f matrix = poseStack.last().pose();

        // Définit les composants de couleur pour le rendu (blanc avec l'alpha spécifié)
        int r = 255;
        int g = 255;
        int b = 255;
        int a = (int) (alpha * 255); // Convertit l'alpha float en int (0-255)

        // Itère sur toutes les boîtes constituant la forme du bloc (un VoxelShape peut être composé de plusieurs boîtes)
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            // Calcule les coordonnées absolues des coins de la boîte
            double dx1 = x1 + actualPos.getX();
            double dy1 = y1 + actualPos.getY();
            double dz1 = z1 + actualPos.getZ();
            double dx2 = x2 + actualPos.getX();
            double dy2 = y2 + actualPos.getY();
            double dz2 = z2 + actualPos.getZ();

            // Ajoute les vertices pour chaque face de la boîte au BufferBuilder
            // Chaque vertex a une position (x, y, z) et une couleur (r, g, b, a)
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

        // Termine la construction du buffer et envoie les vertices à la carte graphique pour le rendu
        tesselator.end();

        // Réinitialise l'état de rendu après que le RenderType a été utilisé.
        // C'est crucial pour ne pas interférer avec le rendu des éléments suivants dans le jeu.
        renderType.clearRenderState();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // Réinitialise la couleur du shader à l'opaque
        RenderSystem.enableCull(); // Ré-active le culling des faces
        RenderSystem.disableBlend(); // Désactive le blending
    }
}
