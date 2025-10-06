package net.mcreator.zombierool.block;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.RenderShape;

import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;

import net.minecraft.network.chat.Component; // Added import for Component
import net.minecraft.world.item.TooltipFlag; // Added import for TooltipFlag
import net.minecraft.client.Minecraft; // Added import for Minecraft client


public class ObstacleDoorBlock extends FenceBlock implements EntityBlock {

    public static final BooleanProperty CONN_UP = BooleanProperty.create("conn_up");
    public static final BooleanProperty CONN_DOWN = BooleanProperty.create("conn_down");
    // NOUVEAU: Propriété pour indiquer si un bloc a été copié
    public static final BooleanProperty HAS_COPIED_BLOCK = BooleanProperty.create("has_copied_block");

    public List<BlockPos> findConnectedBlocks(Level level, BlockPos startPos) {
        List<BlockPos> connected = new ArrayList<>();
        BlockState startState = level.getBlockState(startPos);
        
        for (Direction dir : Direction.values()) {
            BlockPos checkPos = startPos.relative(dir);
            if (isSameBlockType(level, checkPos)) {
                connected.add(checkPos);
            }
        }
        return connected;
    }

    private void findAllConnectedBlocks(Level level, BlockPos pos, Set<BlockPos> results) {
        if (results.contains(pos)) return;
        results.add(pos);
        
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (isSameBlockType(level, neighbor)) {
                findAllConnectedBlocks(level, neighbor, results);
            }
        }
    }

    private boolean isSameBlockType(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ObstacleDoorBlock;
    }

    public void updateGroupConnections(Level level, BlockPos origin) {
        Set<BlockPos> checked = new HashSet<>();
        findAllConnectedBlocks(level, origin, checked); 
        
        for (BlockPos pos : checked) { 
            BlockState state = level.getBlockState(pos);
            BlockState updatedState = updateVerticalConnections(state, level, pos)
                                        .setValue(NORTH, isSameBlockType(level, pos.north()))
                                        .setValue(SOUTH, isSameBlockType(level, pos.south()))
                                        .setValue(EAST, isSameBlockType(level, pos.east()))
                                        .setValue(WEST, isSameBlockType(level, pos.west()));

            if (!state.equals(updatedState)) {
                level.setBlock(pos, updatedState, Block.UPDATE_ALL);
            }
        }
    }

    public ObstacleDoorBlock() {
        super(BlockBehaviour.Properties.of()
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.METAL)
                .strength(-1, 3600000)
                .noOcclusion()
                .isRedstoneConductor((bs, br, bp) -> false)
                .dynamicShape()
                .forceSolidOn()
                .randomTicks()
                .isViewBlocking((state, world, pos) -> false)
                .isSuffocating((state, world, pos) -> false));

        // NOUVEAU: Enregistrement de la nouvelle propriété dans l'état par défaut
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONN_UP, false)
                .setValue(CONN_DOWN, false)
                .setValue(HAS_COPIED_BLOCK, false)); // Par défaut, pas de bloc copié
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
        list.add(Component.literal(getTranslatedMessage("§9Obstacle Achetable", "§9Purchasable Obstacle")));
        list.add(Component.literal(getTranslatedMessage("§7Définissez un prix en mode Créatif.", "§7Define a price in Creative mode.")));
        list.add(Component.literal(getTranslatedMessage("§7Le prix sera affiché en mode Survie.", "§7The price will be displayed in Survival mode.")));
        list.add(Component.literal(getTranslatedMessage("§7Les joueurs peuvent acheter l'obstacle pour progresser.", "§7Players can purchase the obstacle to progress.")));
        list.add(Component.literal(getTranslatedMessage("§7Active un canal spécifié à l'achat pour les spawners d'ennemis.", "§7Activates a specified channel upon purchase for enemy spawners.")));
    }

    // MODIFIÉ: Rend le modèle standard ou le BlockEntityRenderer selon HAS_COPIED_BLOCK
    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (state.getValue(HAS_COPIED_BLOCK)) {
            return RenderShape.ENTITYBLOCK_ANIMATED; 
        }
        return RenderShape.MODEL; // Rend le modèle JSON par défaut (votre modèle de clôture)
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.getBlock() == this;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        // NOUVEAU: Ajout de la propriété à la définition de l'état
        builder.add(CONN_UP, CONN_DOWN, HAS_COPIED_BLOCK); 
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ObstacleDoorBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof Player player && player.isCreative()) {
                return Shapes.empty();
            }
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return super.getVisualShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return super.getOcclusionShape(state, world, pos);
    }

    private BlockState updateVerticalConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean up = canConnectTo(level, pos.above());
        boolean down = canConnectTo(level, pos.below());
        return state.setValue(CONN_UP, up).setValue(CONN_DOWN, down);
    }

    private boolean canConnectTo(LevelAccessor level, BlockPos pos) {
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.getBlock() instanceof ObstacleDoorBlock;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        state = updateVerticalConnections(state, world, pos);
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return updateVerticalConnections(state, context.getLevel(), context.getClickedPos())
            .setValue(HAS_COPIED_BLOCK, false); // NOUVEAU: S'assure qu'il est false lors du placement
    }

    // MODIFIÉ: La logique de sneak-clic droit est désormais entièrement gérée par un paquet client-serveur (ClientSetupEvents)
    // Cette méthode gère seulement l'ouverture du GUI en mode créatif sans sneak.
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!world.isClientSide) {
            // Si le joueur est en mode créatif ET qu'il ne sneake PAS
            if (player.isCreative() && !player.isSecondaryUseActive()) { 
                System.out.println("DEBUG SERVER: ObstacleDoorBlock.use() - Ouvrir le menu pour joueur créatif non-sneak."); 
                MenuProvider menuProvider = getMenuProvider(state, world, pos);
                if (menuProvider != null) {
                    NetworkHooks.openScreen((ServerPlayer) player, menuProvider, pos);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS; 
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!world.isClientSide) {
            BlockState updatedState = updateVerticalConnections(state, world, pos);
            if (!updatedState.equals(state)) {
                world.setBlock(pos, updatedState, Block.UPDATE_ALL);
            }
            world.scheduleTick(pos, this, 20);
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        return !dropsOriginal.isEmpty() ? dropsOriginal : Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ObstacleDoorBlockEntity be) {
                Containers.dropContents(world, pos, be);
                world.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, world, pos, newState, isMoving);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);
        return (tileentity instanceof ObstacleDoorBlockEntity be)
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(be)
                : 0;
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int eventID, int eventParam) {
        super.triggerEvent(state, world, pos, eventID, eventParam);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(eventID, eventParam);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
        BlockEntity tileEntity = worldIn.getBlockEntity(pos);
        return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, Mob mob) {
        return BlockPathTypes.BLOCKED;
    }
}
