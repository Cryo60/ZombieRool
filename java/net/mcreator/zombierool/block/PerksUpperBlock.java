package net.mcreator.zombierool.block;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

import net.mcreator.zombierool.init.ZombieroolModBlocks;

import java.util.List;
import java.util.Collections;

public class PerksUpperBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public PerksUpperBlock() {
        // Optionnel : Changer strength pour tester si c'est la cause.
        // super(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(2.0f, 10.0f));
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noOcclusion()
            .isRedstoneConductor((state, world, pos) -> true)
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 15;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
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
        if (!world.isClientSide) {
            // Place PerksAntenneBlock directement au-dessus
            BlockPos antennePos = pos.above();
            BlockState antenneBlockState = ZombieroolModBlocks.PERKS_ANTENNE.get().defaultBlockState();
            world.setBlock(antennePos, antenneBlockState, 3);
            // Nous n'avons pas besoin de notifier ici, le PerksLowerBlock gérera la mise à jour de l'état de l'antenne
            // via son neighborChanged.
        }
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
                // Détruire le bloc inférieur si le bloc supérieur est détruit
                BlockPos lowerPos = pos.below();
                if (world.getBlockState(lowerPos).getBlock() == ZombieroolModBlocks.PERKS_LOWER.get()) {
                    world.destroyBlock(lowerPos, false); // false pour ne pas dropper d'items, car il sera dropé par le PerksLowerBlock lui-même
                }
                // Détruire le bloc antenne si le bloc supérieur est détruit
                BlockPos antennePos = pos.above();
                if (world.getBlockState(antennePos).getBlock() == ZombieroolModBlocks.PERKS_ANTENNE.get()) {
                    world.destroyBlock(antennePos, false); // false pour ne pas dropper d'items
                }
            }
        }
    }
}