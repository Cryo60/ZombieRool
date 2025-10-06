package net.mcreator.zombierool.block;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.pathfinder.PathComputationType;

import java.util.List;
import java.util.Collections;

import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.init.ZombieroolModBlocks;

import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag
import net.minecraft.client.Minecraft; // Import for Minecraft client


public class DerWunderfizzBlock extends Block {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public DerWunderfizzBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noOcclusion()
            .isRedstoneConductor((state, world, pos) -> false)
            .isViewBlocking((state, world, pos) -> false)
            .isSuffocating((state, world, pos) -> false)
            .pushReaction(PushReaction.BLOCK)
            .lightLevel(state -> 10) // <-- ADD THIS LINE to make it emit light
        );
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(POWERED, false)
            .setValue(FACING, Direction.NORTH)
        );
    }

    /**
     * Helper method to check if the client's language is English.
     * This is crucial for dynamic translation of item names and tooltips.
     * @return true if the client's language code starts with "en", false otherwise.
     */
    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    /**
     * Helper method for dynamic translation based on the client's language.
     * @param frenchMessage The message to display if the client's language is French or not English.
     * @param englishMessage The message to display if the client's language is English.
     * @return The appropriate translated message.
     */
    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.literal(getTranslatedMessage("§9Machine Der Wunderfizz", "§9Der Wunderfizz Machine")));
        list.add(Component.literal(getTranslatedMessage("§7Obtenez un atout aléatoire en échange de points.", "§7Get a random perk in exchange for points.")));
        list.add(Component.literal(getTranslatedMessage("§7Nécessite un courant de Redstone pour être active.", "§7Requires a Redstone signal to be active.")));
        list.add(Component.literal(getTranslatedMessage("§7Composée de plusieurs blocs (bas, haut, antenne).", "§7Composed of multiple blocks (lower, upper, antenna).")));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        return !dropsOriginal.isEmpty() ? dropsOriginal : Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return Shapes.empty();
    }

    @Override
    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        return false;
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
    public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(blockstate, world, pos, oldState, isMoving);
        if (!world.isClientSide) {
            if (world.getBlockState(pos.below()).getBlock() != this) {
                BlockPos upperPos = pos.above();
                BlockPos antennaPos = pos.above(2);

                if (world.getBlockState(upperPos).isAir()) {
                    world.setBlock(upperPos, ZombieroolModBlocks.DER_WUNDERFIZZ_UPPER.get().defaultBlockState().setValue(DerWunderfizzUpperBlock.FACING, blockstate.getValue(FACING)), 3);
                }
                if (world.getBlockState(antennaPos).isAir()) {
                    world.setBlock(antennaPos, ZombieroolModBlocks.WUNDERFIZZ_ANTENNE.get().defaultBlockState(), 3);
                }

                if (world instanceof ServerLevel serverLevel) {
                    WorldConfig.get(serverLevel).addDerWunderfizzPosition(pos);
                }
            }
            this.neighborChanged(blockstate, world, pos, this, pos, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() || isMoving) {
            if (!worldIn.isClientSide) {
                BlockPos basePos = null;
                if (worldIn.getBlockState(pos.below()).getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ_UPPER.get()) {
                    basePos = pos.below();
                } else if (worldIn.getBlockState(pos.below(2)).getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ.get()) { // Corrected
                    basePos = pos.below(2);
                } else if (state.getBlock() == this) {
                    basePos = pos;
                }

                if (basePos != null) {
                    BlockPos currentUpperPos = basePos.above();
                    BlockPos currentAntennaPos = basePos.above(2);

                    if (worldIn.getBlockState(currentUpperPos).getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ_UPPER.get()) {
                        worldIn.destroyBlock(currentUpperPos, false);
                    }
                    if (worldIn.getBlockState(currentAntennaPos).getBlock() == ZombieroolModBlocks.WUNDERFIZZ_ANTENNE.get()) {
                        worldIn.destroyBlock(currentAntennaPos, false);
                    }
                    if (worldIn.getBlockState(basePos).getBlock() == this && !pos.equals(basePos)) {
                         worldIn.destroyBlock(basePos, false);
                    }

                    if (worldIn instanceof ServerLevel serverLevel) {
                        WorldConfig.get(serverLevel).removeDerWunderfizzPosition(basePos);
                    }
                }
            }
            super.onRemove(state, worldIn, pos, newState, isMoving);
        }
    }


    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, block, fromPos, isMoving);
        if (!world.isClientSide) {
            BlockPos lowerBlockPos = null;
            if (world.getBlockState(pos.above()).getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ_UPPER.get() ||
                world.getBlockState(pos.above(2)).getBlock() == ZombieroolModBlocks.WUNDERFIZZ_ANTENNE.get()) {
                lowerBlockPos = pos;
            } else if (world.getBlockState(pos.below()).getBlock() == this) {
                lowerBlockPos = pos.below();
            } else if (world.getBlockState(pos.below(2)).getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ.get()) { // Corrected
                 lowerBlockPos = pos.below(2);
            }

            if (lowerBlockPos != null) {
                boolean newPoweredState = world.hasNeighborSignal(lowerBlockPos) ||
                                          world.hasNeighborSignal(lowerBlockPos.above()) ||
                                          world.hasNeighborSignal(lowerBlockPos.above(2));

                BlockState baseState = world.getBlockState(lowerBlockPos);
                BlockState upperState = world.getBlockState(lowerBlockPos.above());
                BlockState antennaState = world.getBlockState(lowerBlockPos.above(2));

                if (baseState.getBlock() == this && baseState.getValue(POWERED) != newPoweredState) {
                    world.setBlock(lowerBlockPos, baseState.setValue(POWERED, newPoweredState), 3);
                }
                if (upperState.getBlock() == ZombieroolModBlocks.DER_WUNDERFIZZ_UPPER.get() && upperState.getValue(DerWunderfizzUpperBlock.POWERED) != newPoweredState) {
                    world.setBlock(lowerBlockPos.above(), upperState.setValue(DerWunderfizzUpperBlock.POWERED, newPoweredState), 3);
                }
                if (antennaState.getBlock() == ZombieroolModBlocks.WUNDERFIZZ_ANTENNE.get() && antennaState.getValue(WunderfizzAntenneBlock.POWERED) != newPoweredState) {
                    world.setBlock(lowerBlockPos.above(2), antennaState.setValue(WunderfizzAntenneBlock.POWERED, newPoweredState), 3);
                }
            }
        }
    }
}
