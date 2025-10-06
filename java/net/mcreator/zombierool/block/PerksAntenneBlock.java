package net.mcreator.zombierool.block;

import org.checkerframework.checker.units.qual.s;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import net.mcreator.zombierool.init.ZombieroolModBlocks;

import java.util.List;
import java.util.Collections;

public class PerksAntenneBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public PerksAntenneBlock() {
        super(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(-1, 3600000).lightLevel(state -> state.getValue(POWERED) ? 5 : 0).noOcclusion().isRedstoneConductor((bs, br, bp) -> false));
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false).setValue(POWERED, false));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return state.getFluidState().isEmpty();
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean flag = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        BlockPos lowerBlockPos = context.getClickedPos().below().below(); // Antenne -> Upper -> Lower
        boolean isLowerBlockPowered = false;
        if (context.getLevel().getBlockState(lowerBlockPos).getBlock() == ZombieroolModBlocks.PERKS_LOWER.get()) {
            isLowerBlockPowered = context.getLevel().getBlockState(lowerBlockPos).getValue(PerksLowerBlock.POWERED);
        }
        return this.defaultBlockState().setValue(WATERLOGGED, flag).setValue(POWERED, isLowerBlockPowered);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor world, BlockPos currentPos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, facing, facingState, world, currentPos, facingPos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, world, pos, newState, isMoving);
        // Cette condition est CRUCIALE pour éviter les boucles de destruction.
        // Elle ne doit se déclencher que si le type de bloc change (le bloc est détruit et remplacé par un autre type)
        // ou si isMoving est vrai (piston, etc.).
        // Si le bloc est juste "mis à jour" (changement de propriété), cette logique ne doit pas s'exécuter.
        if (state.getBlock() != newState.getBlock() || isMoving) {
            if (!world.isClientSide) {
                // Détruire le bloc supérieur si l'antenne est détruite (l'upper est en dessous)
                BlockPos upperPos = pos.below();
                if (world.getBlockState(upperPos).getBlock() == ZombieroolModBlocks.PERKS_UPPER.get()) {
                    world.destroyBlock(upperPos, false); // false pour ne pas dropper d'items, car il sera dropé par le PerksUpperBlock lui-même
                }
            }
        }
    }
}