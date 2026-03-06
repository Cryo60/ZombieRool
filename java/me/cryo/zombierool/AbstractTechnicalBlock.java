package me.cryo.zombierool.block;

import me.cryo.zombierool.client.LinkRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractTechnicalBlock extends Block {

    public AbstractTechnicalBlock(Properties properties) {
        super(properties);
    }

    /**
     * Détermine si le bloc doit être visuellement caché.
     * Vrai si : On est en survie OU (On est en créatif ET la vue survie est activée).
     */
    protected boolean shouldBeInvisible() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Player player = Minecraft.getInstance().player;
            // Si le joueur existe et (n'est pas créatif OU a activé la vue survie)
            return player != null && (!player.isCreative() || LinkRenderer.isSurvivalViewEnabled);
        }
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return shouldBeInvisible() ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shouldBeInvisible() ? Shapes.empty() : Shapes.block();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return shouldBeInvisible() ? Shapes.empty() : Shapes.block();
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        // Empêche le rendu des faces internes si deux blocs identiques sont côte à côte
        return adjacentBlockState.getBlock() == this ? true : super.skipRendering(state, adjacentBlockState, side);
    }
    
    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    // Gestion par défaut des collisions pour les blocs techniques : 
    // Pas de collision pour les joueurs en créatif
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            if (entityContext.getEntity() instanceof Player player && player.isCreative()) {
                return Shapes.empty();
            }
        }
        return getTechnicalCollisionShape(state, world, pos, context);
    }

    /**
     * Méthode à surcharger pour définir la collision réelle du bloc (pour les mobs/joueurs survie).
     * Par défaut : Bloc solide.
     */
    protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    // Utilitaires de traduction pour les enfants
    protected static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) return false;
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    protected static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        addTechnicalTooltip(tooltip);
    }

    // Méthode abstraite forçant les enfants à définir leur tooltip
    protected abstract void addTechnicalTooltip(List<Component> tooltip);
}