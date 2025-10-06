package net.mcreator.zombierool.block;

import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Containers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.mcreator.zombierool.world.inventory.PerksInterfaceMenu;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;
import net.mcreator.zombierool.init.ZombieroolModBlocks;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext; // Ensure this import is present and correct
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.Minecraft;
// Added imports for PerksUpperBlock and PerksAntenneBlock to resolve POWERED symbol
import net.mcreator.zombierool.block.PerksUpperBlock;
import net.mcreator.zombierool.block.PerksAntenneBlock;


public class PerksLowerBlock extends Block implements EntityBlock {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final Map<Player, Boolean> isTouching = new HashMap<>();

    public PerksLowerBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL) // Assign a map color
            .instrument(NoteBlockInstrument.IRON_XYLOPHONE) // Assign an instrument
            .sound(SoundType.METAL)
            .strength(-1, 3600000)
            .noOcclusion() // Tells the game it's not a full, opaque block
            .isRedstoneConductor((state, world, pos) -> false) // **Crucial: Prevents redstone dust connections**
            .isViewBlocking((state, world, pos) -> false) // Indicates it doesn't fully block vision
            .isSuffocating((state, world, pos) -> false)  // Indicates it doesn't fully suffocate
            .pushReaction(PushReaction.BLOCK) // Prevents pistons from pushing it easily
            // .forceSolidOn() // This might sometimes make it act "too" solid. Let's try without it first.
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(FACING, Direction.NORTH));
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
        list.add(Component.literal(getTranslatedMessage("§9Machine à Atouts", "§9Perk Machine")));
        list.add(Component.literal(getTranslatedMessage("§7Définissez l'atout et son prix en mode Créatif.", "§7Define the perk and its price in Creative mode.")));
        list.add(Component.literal(getTranslatedMessage("§7Nécessite un courant de Redstone pour être active.", "§7Requires a Redstone signal to be active.")));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        // If it's not visually full, it should not block light like a full block.
        // Returning 0 for a non-full block is common, or a value based on your texture.
        return 0; // Changed from 15 to 0 for non-occluding block
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        // This method should align with isRedstoneConductor in properties.
        // If you don't want redstone dust connecting, return false.
        return false; // Changed from true to false
    }

    // --- Explicitly define shapes to prevent connection logic ---
    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        // Return a full block shape for interaction/collision, even if visually it's not.
        // This is often key to preventing "fence-like" connection rendering.
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        // Return an empty shape for occlusion if you used .noOcclusion()
        return Shapes.empty(); // Consistent with .noOcclusion()
    }

    public boolean isRedstoneConductor(BlockState state, BlockGetter level, BlockPos pos) {
        // This method is called by redstone dust to check if it can connect.
        // It should match the .isRedstoneConductor property set in the constructor.
        return false;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        // This means the block can emit redstone signal. Keep as true if it should.
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
        // Prevents pathfinding entities from treating it as a full, solid block for navigation.
        return false;
    }


    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        return !dropsOriginal.isEmpty() ? dropsOriginal : Collections.singletonList(new ItemStack(this, 1));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        super.use(state, world, pos, player, hand, hit);
        if (world.isClientSide) return InteractionResult.SUCCESS;

        if (player.isCreative()) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.literal("Perks Lower");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                        return new PerksInterfaceMenu(id, inventory, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
                    }
                }, pos);
            }
            return InteractionResult.CONSUME;
        } else {
            CompoundTag tag = player.getPersistentData();
            long lastUsed = tag.getLong("perk_cd");
            long now = world.getGameTime();
            if (now - lastUsed < 40) return InteractionResult.CONSUME;
            tag.putLong("perk_cd", now);
            world.playSound(null, pos,
                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:perks_deny")),
                SoundSource.BLOCKS, 1f, 1f);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
        BlockEntity tileEntity = worldIn.getBlockEntity(pos);
        return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PerksLowerBlockEntity(pos, state);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level world, BlockPos pos, int eventID, int eventParam) {
        super.triggerEvent(state, world, pos, eventID, eventParam);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(eventID, eventParam);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() || isMoving) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof PerksLowerBlockEntity be) {
                Containers.dropContents(world, pos, be);
                world.updateNeighbourForOutputSignal(pos, this);
            }

            BlockPos upperPos = pos.above();
            if (world.getBlockState(upperPos).getBlock() == ZombieroolModBlocks.PERKS_UPPER.get()) {
                world.destroyBlock(upperPos, false);
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
        return tileentity instanceof PerksLowerBlockEntity be
            ? AbstractContainerMenu.getRedstoneSignalFromContainer(be)
            : 0;
    }

    @Override
    public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(blockstate, world, pos, oldState, isMoving);
        if (!world.isClientSide) {
            BlockPos upperPos = pos.above();
            Direction facing = blockstate.getValue(FACING);
            BlockState upperBlockState = ZombieroolModBlocks.PERKS_UPPER.get().defaultBlockState().setValue(PerksUpperBlock.FACING, facing);
            world.setBlock(upperPos, upperBlockState, 3);
            this.neighborChanged(blockstate, world, pos, this, pos, false);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, block, fromPos, isMoving);
        if (!world.isClientSide) {
            boolean powered = world.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                world.setBlock(pos, state.setValue(POWERED, powered), 3);

                BlockPos upperPos = pos.above();
                BlockState upperState = world.getBlockState(upperPos);
                if (upperState.getBlock() == ZombieroolModBlocks.PERKS_UPPER.get()) {
                    world.sendBlockUpdated(upperPos, upperState, upperState, 3);

                    BlockPos antennePos = upperPos.above();
                    BlockState antenneState = world.getBlockState(antennePos);
                    if (antenneState.getBlock() == ZombieroolModBlocks.PERKS_ANTENNE.get()) {
                        if (antenneState.getValue(PerksAntenneBlock.POWERED) != powered) {
                            world.setBlock(antennePos, antenneState.setValue(PerksAntenneBlock.POWERED, powered), 3);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getDirectSignal(BlockState blockstate, BlockGetter world, BlockPos pos, Direction direction) {
        return blockstate.getValue(POWERED) ? 15 : 0;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof PerksLowerBlockEntity blockEntity) {
                PerksLowerBlockEntity.clientTick(lvl, pos, st, blockEntity);
            }
        };
    }
}
