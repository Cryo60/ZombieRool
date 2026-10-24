package me.cryo.zombierool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import me.cryo.zombierool.config.GlobalSwitchState;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.util.PlayerVoiceManager;
import me.cryo.zombierool.scripting.LuaScriptManager;

import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

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

    @Override
    public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.translatable("block.zombierool.powerswitch.tooltip.1"));
        list.add(Component.translatable("block.zombierool.powerswitch.tooltip.2"));
        list.add(Component.translatable("block.zombierool.powerswitch.tooltip.3"));
        list.add(Component.translatable("block.zombierool.powerswitch.tooltip.4"));
        list.add(Component.translatable("block.zombierool.powerswitch.tooltip.5"));
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
        // The model is a panel against its supporting face, not a collection of thin rods.
        return switch (state.getValue(FACE)) {
            case FLOOR -> box(0, 0, 0, 16, 4.5, 16);
            case CEILING -> box(0, 11.5, 0, 16, 16, 16);
            case WALL -> switch (state.getValue(FACING)) {
                case SOUTH -> box(0, 0, 0, 16, 16, 4.5);
                case EAST -> box(0, 0, 0, 4.5, 16, 16);
                case WEST -> box(11.5, 0, 0, 16, 16, 16);
                default -> box(0, 0, 11.5, 16, 16, 16);
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
                .setValue(FACE, context.getClickedFace() == Direction.UP ? AttachFace.FLOOR : context.getClickedFace() == Direction.DOWN ? AttachFace.CEILING : AttachFace.WALL)
                .setValue(FACING, context.getClickedFace().getAxis().isHorizontal() ? context.getClickedFace() : context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, false);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
            WorldConfig.get(serverLevel).addPowerSwitchPosition(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && level instanceof ServerLevel server) {
            WorldConfig.get(server).removePowerSwitchPosition(pos);
            if (state.getValue(POWERED)) GlobalSwitchState.setActivated(level, false);
            me.cryo.zombierool.gameplay.MapPower.refreshSoon(server);
        }
        super.onRemove(state, level, pos, next, moving);
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

    @Override public int getDirectSignal(BlockState s, BlockGetter l, BlockPos p, Direction d) { return getSignal(s,l,p,d); }
    @Override public boolean canConnectRedstone(BlockState s, BlockGetter l, BlockPos p, Direction d) { return true; }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS; // Désactive le clic droit
    }

    public static void togglePower(Level world, BlockPos pos, Player player) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof PowerSwitchBlock)) return;

        boolean newState = !state.getValue(POWERED);
        world.setBlock(pos, state.setValue(POWERED, newState), 3);
        GlobalSwitchState.setActivated(world, newState);

        if (newState) {
            world.playSound(
                    null, pos,
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "power")),
                    SoundSource.BLOCKS, 1f, 1f
            );
            PlayerVoiceManager.playPowerOnSound(player, world);
            LuaScriptManager.callEvent("OnPowerActivated");
        }

        for (BlockPos activatorPos : GlobalSwitchState.getActivatorPositions(world)) {
            if (world.hasChunkAt(activatorPos)) {
                world.updateNeighborsAt(activatorPos, world.getBlockState(pos).getBlock());
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = activatorPos.relative(dir);
                    world.updateNeighborsAt(neighbor, world.getBlockState(activatorPos).getBlock());
                }
            }
        }
        if (world instanceof ServerLevel server) me.cryo.zombierool.gameplay.MapPower.refreshSoon(server);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (!world.isClientSide && world instanceof ServerLevel serverLevel) {
            if (state.getValue(POWERED)) {
                GlobalSwitchState.setActivated(world, false);
            }
            me.cryo.zombierool.gameplay.MapPower.refreshSoon(serverLevel);
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
