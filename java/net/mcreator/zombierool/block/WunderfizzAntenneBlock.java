package net.mcreator.zombierool.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;
import java.util.Collections;

import net.mcreator.zombierool.init.ZombieroolModBlocks;

public class WunderfizzAntenneBlock extends Block {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public WunderfizzAntenneBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noOcclusion()
            .isRedstoneConductor((bs, br, bp) -> false)
            .isViewBlocking((state, world, pos) -> false)
            .isSuffocating((state, world, pos) -> false)
            .pushReaction(PushReaction.BLOCK)
            .lightLevel(state -> 7) // Ajouté: Fait que le bloc émette un niveau de lumière de 7
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(blockstate, world, pos, oldState, isMoving);
        // Ce bloc est principalement placé par le bloc de base.
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() || isMoving) {
            if (!world.isClientSide) {
                // Si ce bloc est cassé, localisez le bloc de base deux blocs en dessous
                BlockPos basePos = pos.below(2);
                if (world.getBlockState(basePos).getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ.get()) { // Corrected
                    // Si le bloc de base est trouvé, déclenchez sa destruction.
                    // Le onRemove du bloc de base gérera la destruction des autres parties.
                    world.destroyBlock(basePos, false);
                }
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }
    }
}
