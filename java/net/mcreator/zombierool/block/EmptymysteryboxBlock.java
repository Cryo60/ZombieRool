package net.mcreator.zombierool.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;

// Imports nécessaires pour onRemove
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
// import net.minecraft.server.level.ServerLevel; // Non utilisé après la suppression
// import net.mcreator.zombierool.MysteryBoxManager; // Non utilisé après la suppression
// import net.mcreator.zombierool.WorldConfig; // Non utilisé après la suppression

public class EmptymysteryboxBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty PART = BooleanProperty.create("part"); // Gardez la propriété PART

    public EmptymysteryboxBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN) // Couleur de la carte pour la boîte vide
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.WOOD)
                .strength(-1.0F, 3600000.0F)
                .isRedstoneConductor((bs, br, bp) -> false)
                .pushReaction(PushReaction.BLOCK)
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, false));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 15; // Bloque toute la lumière
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block(); // Bloc plein
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block(); // Bloc plein
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return Shapes.block(); // Forme de collision d'un bloc plein
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return Shapes.block(); // Pour un bloc plein
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

    // REMOVED: @Override public BlockState getStateForPlacement(BlockPlaceContext context) { ... }
    // REMOVED: @Override public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) { ... }
    
    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        System.out.println("[DEBUG][EmptymysteryboxBlock] onRemove called for " + pos.toShortString() + ". Part: " + state.getValue(PART) + ". IsMoving: " + isMoving + ". Old Block: " + state.getBlock().getName().getString() + ", New Block: " + newState.getBlock().getName().getString());
        
        // S'assure que c'est une vraie suppression de bloc et non juste un changement d'état ou un déplacement
        // La logique de désenregistrement et de destruction de l'autre partie a été supprimée.
        if (state.getBlock() != newState.getBlock() && !isMoving) {
            // Ancienne logique de gestion du spawner et de la seconde partie ici, maintenant supprimée.
            // Le bloc se comportera désormais comme un bloc ordinaire lors de la destruction.
        } else if (isMoving) {
            System.out.println("[DEBUG][EmptymysteryboxBlock] onRemove called for state change/moving. Not removing from config.");
        }
        super.onRemove(state, worldIn, pos, newState, isMoving);
    }
    
    // Garde les méthodes de rotation/miroir au cas où, mais elles n'ont pas de logique complexe ici.
    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        Direction facing = state.getValue(FACING);
        Boolean part = state.getValue(PART);
        // La logique pour mirror sur la propriété PART est tricky car elle dépend de l'orientation et de la partie.
        // Pour les blocs à deux parties comme ça, il est souvent préférable de redéfinir mirror pour des comportements spécifiques
        // si la "partie" a un impact visuel sur la symétrie. Pour l'instant, je garde une version simple qui inverse la direction.
        Direction newFacing = mirrorIn.mirror(facing);
        return state.setValue(FACING, newFacing).setValue(PART, part); // Ne change pas la PART ici par défaut, car mirror n'inverse pas les propriétés booléennes simples comme ça.
    }
}
