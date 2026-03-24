package me.cryo.zombierool.block.system;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class MimicSystem {

	public interface IMimicBlock {
	}

	public interface IMimicContainer {
	    @Nullable
	    BlockState getMimic();
	    void setMimic(@Nullable BlockState state);
	}

    public static BlockState getStateForMimic(Player player, InteractionHand hand, ItemStack held, BlockHitResult hit, Block blockToCopy) {
        BlockPlaceContext ctx = new BlockPlaceContext(player, hand, held, hit);
        BlockState placementState = blockToCopy.getStateForPlacement(ctx);

        if (placementState == null) {
            placementState = blockToCopy.defaultBlockState();
        }

        try {
            Direction playerLookDir = player.getDirection();
            Direction overrideDir = playerLookDir.getOpposite(); 
            
            if (blockToCopy instanceof me.cryo.zombierool.core.block.ZRSandbagBlock) {
                overrideDir = playerLookDir.getClockWise(); 
            }

            for (net.minecraft.world.level.block.state.properties.Property<?> prop : placementState.getProperties()) {
                if (prop.getName().equals("facing") && prop.getValueClass() == Direction.class) {
                    net.minecraft.world.level.block.state.properties.DirectionProperty dirProp = 
                        (net.minecraft.world.level.block.state.properties.DirectionProperty) prop;
                    
                    Direction finalDir = overrideDir;
                    
                    if (!dirProp.getPossibleValues().contains(finalDir)) {
                        if (finalDir.getAxis() == Direction.Axis.Y) {
                            finalDir = playerLookDir.getOpposite();
                        }
                    }

                    if (dirProp.getPossibleValues().contains(finalDir)) {
                        placementState = placementState.setValue(dirProp, finalDir);
                    }
                } else if (prop.getName().equals("axis") && prop.getValueClass() == Direction.Axis.class) {
                    net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis> axisProp = 
                        (net.minecraft.world.level.block.state.properties.EnumProperty<Direction.Axis>) prop;
                    if (axisProp.getPossibleValues().contains(hit.getDirection().getAxis())) {
                        placementState = placementState.setValue(axisProp, hit.getDirection().getAxis());
                    }
                }
            }
        } catch (Exception ignored) {}

        return placementState;
    }

	public abstract static class AbstractMimicBlock extends Block implements EntityBlock, IMimicBlock {

	    public AbstractMimicBlock(Properties properties) {
	        super(properties
	                .isSuffocating(AbstractMimicBlock::isSuffocatingPredicate)
	                .isViewBlocking(AbstractMimicBlock::isViewBlockingPredicate));
	    }

	    private static boolean isSuffocatingPredicate(BlockState state, BlockGetter world, BlockPos pos) {
	        if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer container) {
	            BlockState mimic = container.getMimic();
	            if (mimic != null && !(mimic.getBlock() instanceof IMimicBlock)) {
	                return mimic.isSuffocating(world, pos);
	            }
	        }
	        return true;
	    }

	    private static boolean isViewBlockingPredicate(BlockState state, BlockGetter world, BlockPos pos) {
	        if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer container) {
	            BlockState mimic = container.getMimic();
	            if (mimic != null && !(mimic.getBlock() instanceof IMimicBlock)) {
	                return mimic.isViewBlocking(world, pos);
	            }
	        }
	        return true;
	    }

	    @Override
	    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
	        return 0;
	    }

	    @Override
	    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
	        return 1.0F;
	    }

	    @Override
	    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
	        if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer container) {
	            BlockState mimic = container.getMimic();
	            if (mimic != null && !(mimic.getBlock() instanceof IMimicBlock)) {
	                return mimic.propagatesSkylightDown(world, pos);
	            }
	        }
	        return true;
	    }

	    @Override
	    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	        if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer container) {
	            BlockState mimic = container.getMimic();
	            if (mimic != null && !(mimic.getBlock() instanceof IMimicBlock)) {
	                VoxelShape shape = MimicSystem.getMimicShape(mimic, world, pos, context);
	                if (shape != null) return shape;
	            }
	        }
	        return Shapes.block();
	    }

	    @Override
	    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	        if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer container) {
	            BlockState mimic = container.getMimic();
	            if (mimic != null && !(mimic.getBlock() instanceof IMimicBlock)) {
	                VoxelShape shape = MimicSystem.getMimicCollisionShape(mimic, world, pos, context);
	                if (shape != null) return shape;
	            }
	        }
	        return Shapes.block();
	    }

	    @Override
	    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	        if (world.getBlockEntity(pos) instanceof MimicSystem.IMimicContainer container) {
	            BlockState mimic = container.getMimic();
	            if (mimic != null && !(mimic.getBlock() instanceof IMimicBlock)) {
	                VoxelShape shape = MimicSystem.getMimicVisualShape(mimic, world, pos, context);
	                if (shape != null) return shape;
	            }
	        }
	        return super.getVisualShape(state, world, pos, context);
	    }

	    @Override
	    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
	        return adjacentBlockState.getBlock() == this || super.skipRendering(state, adjacentBlockState, side);
	    }

	    @Override
	    public RenderShape getRenderShape(BlockState state) {
	        return RenderShape.ENTITYBLOCK_ANIMATED;
	    }

	    @Override
	    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
	        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
	        if (!dropsOriginal.isEmpty())
	            return dropsOriginal;
	        return Collections.singletonList(new ItemStack(this));
	    }

	    @Override
	    public MenuProvider getMenuProvider(BlockState state, Level worldIn, BlockPos pos) {
	        BlockEntity tileEntity = worldIn.getBlockEntity(pos);
	        return tileEntity instanceof MenuProvider menuProvider ? menuProvider : null;
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
	            if (blockEntity instanceof Container container) {
	                Containers.dropContents(world, pos, container);
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
	        if (tileentity instanceof Container container) {
	            return AbstractContainerMenu.getRedstoneSignalFromContainer(container);
	        }
	        return 0;
	    }
	}

	public static void saveMimic(CompoundTag tag, @Nullable BlockState state) {
	    if (state != null) {
	        tag.put("MimicBlock", NbtUtils.writeBlockState(state));
	    }
	}

	@Nullable
	public static BlockState loadMimic(CompoundTag tag, Level level, String legacyKey, boolean isResourceLocation) {
	    if (tag.contains("MimicBlock")) {
	        return NbtUtils.readBlockState(level != null ? level.holderLookup(net.minecraft.core.registries.Registries.BLOCK) : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("MimicBlock"));
	    }
	    if (legacyKey != null && tag.contains(legacyKey)) {
	        if (isResourceLocation) {
	            String rlStr = tag.getString(legacyKey);
	            ResourceLocation rl = ResourceLocation.tryParse(rlStr);
	            if (rl != null && ForgeRegistries.BLOCKS.containsKey(rl)) {
	                Block block = ForgeRegistries.BLOCKS.getValue(rl);
	                if (block != null) return block.defaultBlockState();
	            }
	        }
	    }
	    return null;
	}

	public static void renderMimic(BlockState state, BlockPos pos, Level level, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
	    if (state == null) return;
	    if (state.getBlock() instanceof IMimicBlock) return;

	    BlockState renderState = getConnectedState(state, level, pos);
	    poseStack.pushPose();

	    BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
	    for (RenderType type : ItemBlockRenderTypes.getRenderLayers(renderState)) {
	        VertexConsumer vertexConsumer = buffer.getBuffer(type);
	        dispatcher.renderBatched(
	                renderState,
	                pos,
	                level,
	                poseStack,
	                vertexConsumer,
	                true,
	                level.random
	        );
	    }
	    poseStack.popPose();
	}

	public static VoxelShape getMimicShape(BlockState mimicState, BlockGetter level, BlockPos pos, CollisionContext context) {
	    if (mimicState == null || mimicState.getBlock() instanceof IMimicBlock) return null;
	    BlockState connectedState = getConnectedState(mimicState, level, pos);
	    return connectedState.getShape(level, pos, context);
	}

	public static VoxelShape getMimicCollisionShape(BlockState mimicState, BlockGetter level, BlockPos pos, CollisionContext context) {
	    if (mimicState == null || mimicState.getBlock() instanceof IMimicBlock) return null;
	    BlockState connectedState = getConnectedState(mimicState, level, pos);
	    return connectedState.getCollisionShape(level, pos, context);
	}

	public static VoxelShape getMimicVisualShape(BlockState mimicState, BlockGetter level, BlockPos pos, CollisionContext context) {
	    if (mimicState == null || mimicState.getBlock() instanceof IMimicBlock) return null;
	    BlockState connectedState = getConnectedState(mimicState, level, pos);
	    return connectedState.getVisualShape(level, pos, context);
	}

	public static BlockState getConnectedState(BlockState state, BlockGetter level, BlockPos pos) {
	    Block block = state.getBlock();
	    if (block instanceof WallBlock) {
	        boolean n = connectsTo(state, level, pos, Direction.NORTH);
	        boolean e = connectsTo(state, level, pos, Direction.EAST);
	        boolean s = connectsTo(state, level, pos, Direction.SOUTH);
	        boolean w = connectsTo(state, level, pos, Direction.WEST);

            boolean isStraightX = e && w && !n && !s;
            boolean isStraightZ = n && s && !e && !w;
            boolean up = !isStraightX && !isStraightZ;

	        BlockState newState = state;
	        newState = newState.setValue(WallBlock.UP, up);
	        newState = newState.setValue(WallBlock.NORTH_WALL, n ? WallSide.TALL : WallSide.NONE);
	        newState = newState.setValue(WallBlock.EAST_WALL, e ? WallSide.TALL : WallSide.NONE);
	        newState = newState.setValue(WallBlock.SOUTH_WALL, s ? WallSide.TALL : WallSide.NONE);
	        newState = newState.setValue(WallBlock.WEST_WALL, w ? WallSide.TALL : WallSide.NONE);

	        return newState;
	    } else if (block instanceof CrossCollisionBlock) {
	        boolean n = connectsTo(state, level, pos, Direction.NORTH);
	        boolean e = connectsTo(state, level, pos, Direction.EAST);
	        boolean s = connectsTo(state, level, pos, Direction.SOUTH);
	        boolean w = connectsTo(state, level, pos, Direction.WEST);

	        BlockState newState = state;
	        if (newState.hasProperty(BlockStateProperties.NORTH)) newState = newState.setValue(BlockStateProperties.NORTH, n);
	        if (newState.hasProperty(BlockStateProperties.EAST))  newState = newState.setValue(BlockStateProperties.EAST, e);
	        if (newState.hasProperty(BlockStateProperties.SOUTH)) newState = newState.setValue(BlockStateProperties.SOUTH, s);
	        if (newState.hasProperty(BlockStateProperties.WEST))  newState = newState.setValue(BlockStateProperties.WEST, w);

	        return newState;
	    } else if (block instanceof me.cryo.zombierool.core.block.ZRSandbagBlock) {
	        boolean n = connectsToSandbag(level, pos.relative(Direction.NORTH));
	        boolean s = connectsToSandbag(level, pos.relative(Direction.SOUTH));
	        boolean e = connectsToSandbag(level, pos.relative(Direction.EAST));
	        boolean w = connectsToSandbag(level, pos.relative(Direction.WEST));
	        int count = (n ? 1 : 0) + (s ? 1 : 0) + (e ? 1 : 0) + (w ? 1 : 0);

	        if (count == 4) return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.CROSS);
	        if (count == 3) {
	            Direction open = !n ? Direction.NORTH 
	                           : !s ? Direction.SOUTH
	                           : !e ? Direction.EAST
	                           :      Direction.WEST;
	            return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.T_JUNCTION).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, open);
	        }
	        if ((n && s) || (!e && !w && (n || s)))
	            return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.STRAIGHT).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, Direction.NORTH);
	        if ((e && w) || (!n && !s && (e || w)))
	            return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.STRAIGHT).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, Direction.EAST);
	        if (s && e) return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.CORNER).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, Direction.WEST);  
	        if (s && w) return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.CORNER).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, Direction.NORTH); 
	        if (n && w) return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.CORNER).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, Direction.EAST);  
	        if (n && e) return state.setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.WALL_SHAPE, me.cryo.zombierool.core.block.ZRSandbagBlock.WallShape.CORNER).setValue(me.cryo.zombierool.core.block.ZRSandbagBlock.FACING, Direction.SOUTH); 
	    }

	    return state;
	}

	private static boolean connectsToSandbag(BlockGetter level, BlockPos pos) {
	    BlockState state = level.getBlockState(pos);
	    if (state.getBlock() instanceof me.cryo.zombierool.core.block.ZRSandbagBlock) return true;
	    if (level.getBlockEntity(pos) instanceof IMimicContainer container) {
	        BlockState mimic = container.getMimic();
	        return mimic != null && mimic.getBlock() instanceof me.cryo.zombierool.core.block.ZRSandbagBlock;
	    }
	    return false;
	}

	private static boolean connectsTo(BlockState mimicState, BlockGetter level, BlockPos pos, Direction dir) {
	    BlockPos neighborPos = pos.relative(dir);
	    BlockState neighborState = level.getBlockState(neighborPos);

	    if (level.getBlockEntity(neighborPos) instanceof IMimicContainer container) {
	        BlockState mimic = container.getMimic();
	        if (mimic != null) {
	            neighborState = mimic;
	        }
	    }

	    Block mimicBlock = mimicState.getBlock();

	    if (mimicBlock instanceof WallBlock) {
	        return neighborState.is(BlockTags.WALLS) ||
	                (neighborState.getBlock() instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(neighborState, dir.getOpposite())) ||
	                neighborState.isFaceSturdy(level, neighborPos, dir.getOpposite());
	    }
	    if (mimicBlock instanceof FenceBlock) {
	        return neighborState.is(BlockTags.FENCES) ||
	                neighborState.getBlock() instanceof FenceBlock ||
	                (neighborState.getBlock() instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(neighborState, dir.getOpposite())) ||
	                neighborState.isFaceSturdy(level, neighborPos, dir.getOpposite());
	    }
	    if (mimicBlock instanceof IronBarsBlock) {
	        return neighborState.getBlock() instanceof IronBarsBlock ||
	                neighborState.getBlock() instanceof GlassBlock ||
	                neighborState.isFaceSturdy(level, neighborPos, dir.getOpposite());
	    }

	    return false;
	}
}