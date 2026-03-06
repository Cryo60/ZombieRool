package me.cryo.zombierool.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.EntityBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.server.level.ServerLevel;
import me.cryo.zombierool.block.entity.MysteryBoxBlockEntity;
import me.cryo.zombierool.init.ZombieroolModBlockEntities;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.MysteryBoxManager;
import me.cryo.zombierool.WorldConfig;
import java.util.List;
import java.util.Collections;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.Minecraft;

public class MysteryBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty PART = BooleanProperty.create("part");
	public MysteryBoxBlock() {
	    super(BlockBehaviour.Properties.of()
	            .mapColor(MapColor.COLOR_BROWN)
	            .ignitedByLava()
	            .instrument(NoteBlockInstrument.BASS)
	            .sound(SoundType.WOOD)
	            .strength(-1, 3600000)
	            .isRedstoneConductor((bs, br, bp) -> false)
	            .pushReaction(PushReaction.BLOCK)
	            .lightLevel(state -> 10) 
	    );
	    this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, false));
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
	public void appendHoverText(ItemStack itemstack, BlockGetter world, List<Component> list, TooltipFlag flag) {
	    super.appendHoverText(itemstack, world, list, flag);
	    list.add(Component.literal(getTranslatedMessage("§9Boîte Mystère", "§9Mystery Box")));
	    list.add(Component.literal(getTranslatedMessage("§7Obtenez une arme aléatoire en échange de points.", "§7Get a random weapon in exchange for points.")));
	    list.add(Component.literal(getTranslatedMessage("§7Peut se déplacer aléatoirement sur la carte.", "§7Can move randomly across the map.")));
	    list.add(Component.literal(getTranslatedMessage("§7Si un seul emplacement valide est disponible, elle ne se déplacera jamais.", "§7If only one valid spawn location is available, it will never move.")));
	}
	
	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
	    return false;
	}
	
	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
	    return 15;
	}
	
	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	    return Shapes.block();
	}
	
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
	    return Shapes.block();
	}
	
	@Override
	public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
	    return Shapes.block();
	}
	
	@Override
	public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
	    return Shapes.block();
	}
	
	public boolean isRedstoneConductor(BlockState state, BlockGetter level, BlockPos pos) {
	    return false;
	}
	
	@Override
	public boolean isPathfindable(BlockState pState, BlockGetter pLevel, BlockPos pPos, PathComputationType pType) {
	    return false;
	}
	
	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
	    builder.add(FACING, PART);
	}
	
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
	    Level world = context.getLevel();
	    BlockPos mainPos = context.getClickedPos();
	    Direction facing = context.getHorizontalDirection().getOpposite();
	    BlockPos otherPartPos = MysteryBoxManager.getOtherPartPos(mainPos, facing);
	
	    if (!world.getBlockState(otherPartPos).canBeReplaced() || !world.getBlockState(mainPos).canBeReplaced()) {
	        return null; 
	    }
	
	    if (!world.isClientSide()) {
	        world.setBlock(mainPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, false), 3);
	        world.setBlock(otherPartPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, true), 3);
	
	        if (world instanceof ServerLevel serverWorld) {
	            WorldConfig config = WorldConfig.get(serverWorld); 
	            config.addMysteryBoxPosition(mainPos.immutable());
	            config.addMysteryBoxPosition(otherPartPos.immutable());
	        }
	        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, false);
	    }
	    return this.defaultBlockState().setValue(FACING, facing).setValue(PART, false);
	}
	
	@Override
	public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
	    super.onPlace(blockstate, world, pos, oldState, isMoving);
	}
	
	@Override
	public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
	    if (newState.getBlock() == ZombieroolModBlocks.EMPTYMYSTERYBOX.get()) {
	        super.onRemove(state, worldIn, pos, newState, isMoving); 
	        return; 
	    }
	
	    if (state.getBlock() != newState.getBlock() && !isMoving) {
	        Direction facing = state.getValue(FACING);
	        BlockPos otherPartPos;
	
	        if (state.getValue(PART)) { 
	            otherPartPos = MysteryBoxManager.getOppositeOtherPartPos(pos, facing);
	        } else { 
	            otherPartPos = MysteryBoxManager.getOtherPartPos(pos, facing);
	        }
	
	        if (!worldIn.isClientSide() && worldIn instanceof ServerLevel serverWorld) {
	            WorldConfig config = WorldConfig.get(serverWorld);
	            config.removeMysteryBoxPosition(pos.immutable());
	            if (worldIn.getBlockState(otherPartPos).is(this)) { 
	                config.removeMysteryBoxPosition(otherPartPos.immutable());
	            }
	        }
	
	        if (worldIn.getBlockState(otherPartPos).is(this) && !worldIn.isClientSide()) {
	            worldIn.setBlockAndUpdate(otherPartPos, Blocks.AIR.defaultBlockState());
	        }
	    }
	    super.onRemove(state, worldIn, pos, newState, isMoving);
	}
	
	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
	    return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}
	
	@Override
	public BlockState mirror(BlockState state, Mirror mirrorIn) {
	    Direction facing = state.getValue(FACING);
	    Boolean part = state.getValue(PART);
	    Direction newFacing = mirrorIn.mirror(facing); 
	    Boolean newPart = part; 
	    return state.setValue(FACING, newFacing).setValue(PART, newPart);
	}
	
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
	    if (state.getValue(PART)) {
	        return Collections.emptyList();
	    }
	    return Collections.singletonList(new ItemStack(ZombieroolModBlocks.MYSTERY_BOX.get().asItem(), 1));
	}
	
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
	    if (!state.getValue(PART)) {
	        return new MysteryBoxBlockEntity(pos, state);
	    }
	    return null;
	}
	
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
	    if (pLevel.isClientSide() && !pState.getValue(PART)) {
	        if (pBlockEntityType == ZombieroolModBlockEntities.MYSTERY_BOX.get()) {
	            return (BlockEntityTicker<T>) (BlockEntityTicker<MysteryBoxBlockEntity>) MysteryBoxBlockEntity::clientTick;
	        }
	    }
	    return null;
	}
}