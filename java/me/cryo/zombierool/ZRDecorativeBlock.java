package me.cryo.zombierool.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.List;

public class ZRDecorativeBlock extends Block {
	private final ShapeType shapeType;
	private final boolean isTransparent;
	private final boolean isPassable;
	public enum ShapeType {
	    FULL,
	    CROSS,
	    CARPET
	}
	
	private static final VoxelShape SHAPE_CROSS = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
	private static final VoxelShape SHAPE_CARPET = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
	
	public ZRDecorativeBlock(SoundType sound, ShapeType shape, boolean transparent, boolean passable, int lightLevel) {
	    super(calculateProperties(sound, transparent, passable, lightLevel));
	    this.shapeType = shape;
	    this.isTransparent = transparent;
	    this.isPassable = passable;
	}
	
	public ZRDecorativeBlock(SoundType sound, ShapeType shape, boolean transparent) {
	    this(sound, shape, transparent, false, 0);
	}
	
	private static Properties calculateProperties(SoundType sound, boolean transparent, boolean passable, int lightLevel) {
	    Properties props = Properties.of()
	            .sound(sound)
	            .strength(-1.0f, 3600000.0f)
	            .noLootTable()
	            .lightLevel(state -> lightLevel);
	
	    if (transparent) {
	        props = props.noOcclusion()
	                     .isViewBlocking((state, world, pos) -> false)
	                     .isSuffocating((state, world, pos) -> false);
	    }
	    if (passable) {
	        props = props.noCollission();
	    }
	    return props;
	}
	
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
	    switch (shapeType) {
	        case CROSS:
	            return SHAPE_CROSS;
	        case CARPET:
	            return SHAPE_CARPET;
	        case FULL:
	        default:
	            return Shapes.block();
	    }
	}
	
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
	    if (this.isPassable) {
	        return Shapes.empty();
	    }
	    return super.getCollisionShape(state, level, pos, context);
	}
	
	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
	    if (isTransparent || shapeType != ShapeType.FULL) {
	        return Shapes.empty();
	    }
	    return super.getOcclusionShape(state, level, pos);
	}
	
	@Override
	public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
	    // Si c'est transparent ou pas un bloc complet (comme les bougies), la lumière passe au travers (0 blocage)
	    if (isTransparent || shapeType != ShapeType.FULL) {
	        return 0;
	    }
	    return super.getLightBlock(state, worldIn, pos);
	}
	
	@Override
	public RenderShape getRenderShape(BlockState state) {
	    return RenderShape.MODEL;
	}
	
	@Override
	public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
	    if (isTransparent) {
	        return adjacentBlockState.getBlock() == this ? true : super.skipRendering(state, adjacentBlockState, side);
	    }
	    return super.skipRendering(state, adjacentBlockState, side);
	}
	
	@Override
	public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
	    return isTransparent ? 1.0F : super.getShadeBrightness(state, level, pos);
	}
	
	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
	    return isTransparent || shapeType != ShapeType.FULL;
	}
	
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
	    return Collections.singletonList(new ItemStack(this, 1));
	}
}