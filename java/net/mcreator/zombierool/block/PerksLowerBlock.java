package net.mcreator.zombierool.block;

import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Containers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.RenderShape;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;

import net.minecraftforge.registries.ForgeRegistries;

import net.mcreator.zombierool.world.inventory.PerksInterfaceMenu;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.Unpooled;

public class PerksLowerBlock extends Block implements EntityBlock {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final Map<Player, Boolean> isTouching = new HashMap<>();
    public static final EnumProperty<PerkType> PERK_TYPE = EnumProperty.create("perk_type", PerkType.class);

    public PerksLowerBlock() {
	    super(BlockBehaviour.Properties.of()
	        .mapColor(MapColor.METAL)
	        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
	        .sound(SoundType.METAL)
	        .strength(-1, 3600000)
	        .noOcclusion()
	        .isRedstoneConductor((state, world, pos) -> false)
	        .isViewBlocking((state, world, pos) -> false)
	        .isSuffocating((state, world, pos) -> false)
	        .pushReaction(PushReaction.BLOCK)
	    );
	    this.registerDefaultState(this.stateDefinition.any()
	        .setValue(POWERED, false)
	        .setValue(FACING, Direction.NORTH)
	        .setValue(PERK_TYPE, PerkType.NONE));  // ← AJOUTEZ CETTE LIGNE
	}

    public enum PerkType implements StringRepresentable {
	    NONE("none"),
	    MASTODONTE("mastodonte"),
	    SPEED_COLA("speed_cola"),
	    DOUBLE_TAPE("double_tape"),
	    ROYAL_BEER("royal_beer"),
	    BLOOD_RAGE("blood_rage"),
	    PHD_FLOPPER("phd_flopper"),
	    CHERRY("cherry"),
	    QUICK_REVIVE("quick_revive"),
	    VULTURE("vulture");
	
	    private final String name;
	
	    PerkType(String name) {
	        this.name = name;
	    }
	
	    @Override
	    public String getSerializedName() {
	        return this.name;
	    }
	
	    public static PerkType fromString(String str) {
	        if (str == null || str.isEmpty()) return NONE;
	        for (PerkType type : values()) {
	            if (type.name.equals(str)) return type;
	        }
	        return NONE;
	    }
	}

    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @Override
	public RenderShape getRenderShape(BlockState state) {
	    return RenderShape.MODEL;
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
	    builder.add(POWERED, FACING, PERK_TYPE);  // ← AJOUTEZ PERK_TYPE
	}

	public static void updatePerkType(Level level, BlockPos pos, String perkId) {
	    BlockState state = level.getBlockState(pos);
	    if (state.getBlock() instanceof PerksLowerBlock) {
	        PerkType newType = PerkType.fromString(perkId);
	        level.setBlock(pos, state.setValue(PERK_TYPE, newType), 3);
	    }
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
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return false;
    }

    // Forme visuelle personnalisée basée sur votre modèle
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // Utilise la forme du modèle original qui simule 2 blocs de hauteur
        return switch (state.getValue(FACING)) {
            default -> Shapes.or(
                box(0, 0, 0, 16, 26.75, 16), 
                box(1.5, 26.75, 1.5, 14.5, 32, 14.5), 
                box(3.25, 13.25, 16, 12.75, 25, 17), 
                box(3, 28.25, 14.5, 13, 31, 15)
            );
            case NORTH -> Shapes.or(
                box(0, 0, 0, 16, 26.75, 16), 
                box(1.5, 26.75, 1.5, 14.5, 32, 14.5), 
                box(3.25, 13.25, -1, 12.75, 25, 0), 
                box(3, 28.25, 1, 13, 31, 1.5)
            );
            case EAST -> Shapes.or(
                box(0, 0, 0, 16, 26.75, 16), 
                box(1.5, 26.75, 1.5, 14.5, 32, 14.5), 
                box(16, 13.25, 3.25, 17, 25, 12.75), 
                box(14.5, 28.25, 3, 15, 31, 13)
            );
            case WEST -> Shapes.or(
                box(0, 0, 0, 16, 26.75, 16), 
                box(1.5, 26.75, 1.5, 14.5, 32, 14.5), 
                box(-1, 13.25, 3.25, 0, 25, 12.75), 
                box(1, 28.25, 3, 1.5, 31, 13)
            );
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return getShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    public boolean isRedstoneConductor(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
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
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof PerksLowerBlockEntity be) {
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
        return tileentity instanceof PerksLowerBlockEntity be
            ? AbstractContainerMenu.getRedstoneSignalFromContainer(be)
            : 0;
    }

    @Override
    public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(blockstate, world, pos, oldState, isMoving);
        if (!world.isClientSide) {
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