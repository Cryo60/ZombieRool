package net.mcreator.zombierool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity; // Import for LivingEntity
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.mcreator.zombierool.GlobalSwitchState;
import net.mcreator.zombierool.WorldConfig; // Import WorldConfig

import java.util.Collections;
import java.util.List;

import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag
import net.minecraft.client.Minecraft; // Import for Minecraft client


public class PowerSwitchBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<AttachFace> FACE = FaceAttachedHorizontalDirectionalBlock.FACE;
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public PowerSwitchBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noCollission()
            .noOcclusion()
            .isRedstoneConductor((bs, br, bp) -> false));
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL)
            .setValue(POWERED, false));
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
        list.add(Component.literal(getTranslatedMessage("§9Interrupteur Principal", "§9Main Power Switch")));
        list.add(Component.literal(getTranslatedMessage("§7Une fois activé (clic droit), il envoie un signal aux blocs 'Activator'.", "§7Once activated (right-click), it sends a signal to 'Activator' blocks.")));
        list.add(Component.literal(getTranslatedMessage("§7Les blocs 'Activator' transmettent ensuite un signal de Redstone.", "§7'Activator' blocks then transmit a Redstone signal.")));
        list.add(Component.literal(getTranslatedMessage("§cNe peut pas être désactivé en mode Survie.", "§cCannot be deactivated in Survival mode.")));
        list.add(Component.literal(getTranslatedMessage("§7(Peut être désactivé en mode Créatif pour la réinitialisation du jeu).", "§7(Can be deactivated in Creative mode for game reset).")));
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
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            default -> switch (state.getValue(FACE)) {
                case FLOOR -> Shapes.or(
                        box(0.3, 0.8, 0.5, 13.5, 0.9, 9.5),
                        box(-2.2, 0, -1, 16, 0.8, 19),
                        box(12, 1, 12.2, 13, 8, 13.2),
                        box(1, 1, 12.2, 2, 8, 13.2),
                        box(7, 1, 12.2, 8, 8, 13.2),
                        box(1, 8, 12.2, 13, 8.8, 13.2)
                    );
                case WALL -> Shapes.or(
                        box(0.3, 6.5, 0.8, 13.5, 15.5, 0.9),
                        box(-2.2, -3, 0, 16, 17, 0.8),
                        box(12, 2.8, 1, 13, 3.8, 8),
                        box(1, 2.8, 1, 2, 3.8, 8),
                        box(7, 2.8, 1, 8, 3.8, 8),
                        box(1, 2.8, 8, 13, 3.8, 8.8)
                    );
                case CEILING -> Shapes.or(
                        box(2.5, 15.1, 0.5, 15.7, 15.2, 9.5),
                        box(0, 15.2, -1, 18.2, 16, 19),
                        box(3, 8, 12.2, 4, 15, 13.2),
                        box(14, 8, 12.2, 15, 15, 13.2),
                        box(8, 8, 12.2, 9, 15, 13.2),
                        box(3, 7.2, 12.2, 15, 8, 13.2)
                    );
            };
            case NORTH -> switch (state.getValue(FACE)) {
                case FLOOR -> Shapes.or(
                        box(2.5, 0.8, 6.5, 15.7, 0.9, 15.5),
                        box(0, 0, -3, 18.2, 0.8, 17),
                        box(3, 1, 2.8, 4, 8, 3.8),
                        box(14, 1, 2.8, 15, 8, 3.8),
                        box(8, 1, 2.8, 9, 8, 3.8),
                        box(3, 8, 2.8, 15, 8.8, 3.8)
                    );
                case WALL -> Shapes.or(
                        box(2.5, 6.5, 15.1, 15.7, 15.5, 15.2),
                        box(0, -3, 15.2, 18.2, 17, 16),
                        box(3, 2.8, 8, 4, 3.8, 15),
                        box(14, 2.8, 8, 15, 3.8, 15),
                        box(8, 2.8, 8, 9, 3.8, 15),
                        box(3, 2.8, 7.2, 15, 3.8, 8)
                    );
                case CEILING -> Shapes.or(
                        box(0.3, 15.1, 6.5, 13.5, 15.2, 15.5),
                        box(-2.2, 15.2, -3, 16, 16, 17),
                        box(12, 8, 2.8, 13, 15, 3.8),
                        box(1, 8, 2.8, 2, 15, 3.8),
                        box(7, 8, 2.8, 8, 15, 3.8),
                        box(1, 7.2, 2.8, 13, 8, 3.8)
                    );
            };
            case EAST -> switch (state.getValue(FACE)) {
                case FLOOR -> Shapes.or(
                        box(0.5, 0.8, 2.5, 9.5, 0.9, 15.7),
                        box(-1, 0, 0, 19, 0.8, 18.2),
                        box(12.2, 1, 3, 13.2, 8, 4),
                        box(12.2, 1, 14, 13.2, 8, 15),
                        box(12.2, 1, 8, 13.2, 8, 9),
                        box(12.2, 8, 3, 13.2, 8.8, 15)
                    );
                case WALL -> Shapes.or(
                        box(0.8, 6.5, 2.5, 0.9, 15.5, 15.7),
                        box(0, -3, 0, 0.8, 17, 18.2),
                        box(1, 2.8, 3, 8, 3.8, 4),
                        box(1, 2.8, 14, 8, 3.8, 15),
                        box(1, 2.8, 8, 8, 3.8, 9),
                        box(8, 2.8, 3, 8.8, 3.8, 15)
                    );
                case CEILING -> Shapes.or(
                        box(0.5, 15.1, 0.3, 9.5, 15.2, 13.5),
                        box(-1, 15.2, -2.2, 19, 16, 16),
                        box(12.2, 8, 12, 13.2, 15, 13),
                        box(12.2, 8, 1, 13.2, 15, 2),
                        box(12.2, 8, 7, 13.2, 15, 8),
                        box(12.2, 7.2, 1, 13.2, 8, 13)
                    );
            };
            case WEST -> switch (state.getValue(FACE)) {
                case FLOOR -> Shapes.or(
                        box(6.5, 0.8, 0.3, 15.5, 0.9, 13.5),
                        box(-3, 0, -2.2, 17, 0.8, 16),
                        box(2.8, 1, 12, 3.8, 8, 13),
                        box(2.8, 1, 1, 3.8, 8, 2),
                        box(2.8, 1, 7, 3.8, 8, 8),
                        box(2.8, 8, 1, 3.8, 8.8, 13)
                    );
                case WALL -> Shapes.or(
                        box(15.1, 6.5, 0.3, 15.2, 15.5, 13.5),
                        box(15.2, -3, -2.2, 16, 17, 16),
                        box(8, 2.8, 12, 15, 3.8, 13),
                        box(8, 2.8, 1, 15, 3.8, 2),
                        box(8, 2.8, 7, 15, 3.8, 8),
                        box(7.2, 2.8, 1, 8, 3.8, 13)
                    );
                case CEILING -> Shapes.or(
                        box(6.5, 15.1, 2.5, 15.5, 15.2, 15.7),
                        box(-3, 15.2, 0, 17, 16, 18.2),
                        box(2.8, 8, 3, 3.8, 15, 4),
                        box(2.8, 8, 14, 3.8, 15, 15),
                        box(2.8, 8, 8, 3.8, 15, 9),
                        box(2.8, 7.2, 3, 3.8, 8, 15)
                    );
            };
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACE, faceForDirection(context.getNearestLookingDirection()))
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, false);
    }

    /**
     * Called after the block is placed. This is the ideal place to register its position.
     */
    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
            WorldConfig.get(serverLevel).addPowerSwitchPosition(pos);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    private AttachFace faceForDirection(Direction direction) {
        return switch (direction.getAxis()) {
            case Y -> direction == Direction.UP ? AttachFace.CEILING : AttachFace.FLOOR;
            default -> AttachFace.WALL;
        };
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter blockAccess, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!world.isClientSide) {
            boolean newState = !state.getValue(POWERED);
            
            // Only allow switching if newState is true (activating) or if player is creative (for deactivating too)
            // This prevents regular players from deactivating it once activated unless it's a game reset
            if (newState || player.isCreative()) {
                world.setBlock(pos, state.setValue(POWERED, newState), 3);
                GlobalSwitchState.setActivated(world, newState);
                
                // Update all activators (e.g., doors linked to this switch)
                for (BlockPos activatorPos : GlobalSwitchState.getActivatorPositions(world)) {
                    if (world.hasChunkAt(activatorPos)) {
                        world.updateNeighborsAt(activatorPos, this);
                    }
                }
                return InteractionResult.CONSUME;
            }
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
    
    /**
     * Called when the block is destroyed by a player. This is the ideal place to unregister its position.
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
            // If the switch was powered when broken, deactivate the global switch state
            if (state.getValue(POWERED)) {
                GlobalSwitchState.setActivated(world, false);
            }
            // Remove the position from WorldConfig
            WorldConfig.get(serverLevel).removePowerSwitchPosition(pos);
        }
        return super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        return !dropsOriginal.isEmpty() ? dropsOriginal : Collections.singletonList(new ItemStack(this, 1));
    }
}
