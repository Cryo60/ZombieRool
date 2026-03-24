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

    protected boolean shouldBeInvisible() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Player player = Minecraft.getInstance().player;
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

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            if (entityContext.getEntity() instanceof Player player && player.isCreative()) {
                return Shapes.empty();
            }
        }
        return getTechnicalCollisionShape(state, world, pos, context);
    }

    protected VoxelShape getTechnicalCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        addTechnicalTooltip(tooltip);
    }

    protected abstract void addTechnicalTooltip(List<Component> tooltip);
}