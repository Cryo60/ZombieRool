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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.storage.loot.LootParams;
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
	        boolean wallAbove = isWallOrMimicWall(level, pos.above());
	        boolean n = connectsTo(state, level, pos, Direction.NORTH);
	        boolean e = connectsTo(state, level, pos, Direction.EAST);
	        boolean s = connectsTo(state, level, pos, Direction.SOUTH);
	        boolean w = connectsTo(state, level, pos, Direction.WEST);
	        BlockState newState = state;
	        newState = newState.setValue(WallBlock.UP, false);
	        newState = newState.setValue(WallBlock.NORTH_WALL, n ? (wallAbove ? WallSide.TALL : WallSide.LOW) : WallSide.NONE);
	        newState = newState.setValue(WallBlock.EAST_WALL, e ? (wallAbove ? WallSide.TALL : WallSide.LOW) : WallSide.NONE);
	        newState = newState.setValue(WallBlock.SOUTH_WALL, s ? (wallAbove ? WallSide.TALL : WallSide.LOW) : WallSide.NONE);
	        newState = newState.setValue(WallBlock.WEST_WALL, w ? (wallAbove ? WallSide.TALL : WallSide.LOW) : WallSide.NONE);
	        return newState;
	    } else if (block instanceof CrossCollisionBlock) {
	        boolean n = connectsTo(state, level, pos, Direction.NORTH);
	        boolean e = connectsTo(state, level, pos, Direction.EAST);
	        boolean s = connectsTo(state, level, pos, Direction.SOUTH);
	        boolean w = connectsTo(state, level, pos, Direction.WEST);
	        BlockState newState = state;
	        if (newState.hasProperty(PipeBlock.NORTH)) newState = newState.setValue(PipeBlock.NORTH, n);
	        if (newState.hasProperty(PipeBlock.EAST)) newState = newState.setValue(PipeBlock.EAST, e);
	        if (newState.hasProperty(PipeBlock.SOUTH)) newState = newState.setValue(PipeBlock.SOUTH, s);
	        if (newState.hasProperty(PipeBlock.WEST)) newState = newState.setValue(PipeBlock.WEST, w);
	        return newState;
	    }
	    return state;
	}

	private static boolean isWallOrMimicWall(BlockGetter level, BlockPos pos) {
	    BlockState state = level.getBlockState(pos);
	    if (state.is(BlockTags.WALLS)) return true;
	    BlockEntity be = level.getBlockEntity(pos);
	    if (be instanceof IMimicContainer container) {
	        BlockState mimic = container.getMimic();
	        return mimic != null && mimic.is(BlockTags.WALLS);
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
	                (neighborState.getBlock() instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(neighborState, dir.getOpposite())) ||
	                (neighborState.is(BlockTags.WOODEN_FENCES) && mimicState.is(BlockTags.WOODEN_FENCES));
	    }
	    if (mimicBlock instanceof IronBarsBlock) {
	        return neighborState.is(mimicBlock) ||
	                neighborState.is(BlockTags.WALLS) ||
	                neighborState.is(BlockTags.FENCES) ||
	                neighborState.isFaceSturdy(level, neighborPos, dir.getOpposite());
	    }
	    return false;
	}
}