package net.mcreator.zombierool.block;

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
import net.minecraft.server.level.ServerLevel; // Import ServerLevel

import net.mcreator.zombierool.block.entity.MysteryBoxBlockEntity;
import net.mcreator.zombierool.init.ZombieroolModBlockEntities;
import net.mcreator.zombierool.init.ZombieroolModBlocks;
import net.mcreator.zombierool.MysteryBoxManager;
import net.mcreator.zombierool.WorldConfig; // Importez le WorldConfig

import java.util.List;
import java.util.Collections;

import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag
import net.minecraft.client.Minecraft; // Import for Minecraft client


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
                .lightLevel(state -> 10) // <-- ADD THIS LINE to make it emit light
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, false));
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

        // Vérifie si les deux positions sont valides et si l'espace est libre
        if (!world.getBlockState(otherPartPos).canBeReplaced() || !world.getBlockState(mainPos).canBeReplaced()) {
            System.out.println("[DEBUG][MysteryBoxBlock] Placement prevented: Space not clear at " + mainPos.toShortString() + " or " + otherPartPos.toShortString());
            return null; // Empêche le placement si l'espace n'est pas libre
        }

        // Si le placement est valide, place les deux blocs sur le serveur.
        // C'est ici que le "vrai" placement des deux parties se fait lors d'une pose manuelle.
        if (!world.isClientSide()) {
            System.out.println("[DEBUG][MysteryBoxBlock] Server-side placement initiated for main: " + mainPos.toShortString() + ", other: " + otherPartPos.toShortString());
            // Place la partie principale (avec BlockEntity)
            world.setBlock(mainPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, false), 3);
            // Place la partie secondaire (sans BlockEntity)
            world.setBlock(otherPartPos, this.defaultBlockState().setValue(FACING, facing).setValue(PART, true), 3);

            // NEW: Register both positions in WorldConfig
            if (world instanceof ServerLevel serverWorld) {
                System.out.println("[DEBUG][MysteryBoxBlock] Getting WorldConfig in getStateForPlacement...");
                WorldConfig config = WorldConfig.get(serverWorld); // Gets the WorldConfig instance
                System.out.println("[DEBUG][MysteryBoxBlock] WorldConfig.get result: MysteryBoxPositions count: " + config.getMysteryBoxPositions().size());

                config.addMysteryBoxPosition(mainPos.immutable());
                config.addMysteryBoxPosition(otherPartPos.immutable());
                System.out.println("[DEBUG][MysteryBoxBlock] Added positions to WorldConfig. Current count: " + config.getMysteryBoxPositions().size());
            }

            // Retourne l'état de la partie principale que le système veut "poser" (pour que l'item soit consommé)
            return this.defaultBlockState().setValue(FACING, facing).setValue(PART, false);
        }
        System.out.println("[DEBUG][MysteryBoxBlock] Client-side placement preview for main: " + mainPos.toShortString());
        // Côté client, retourne simplement l'état du bloc principal pour la prévisualisation
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, false);
    }

    @Override
    public void onPlace(BlockState blockstate, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(blockstate, world, pos, oldState, isMoving);
        // La logique de placement de la seconde partie est désormais gérée par
        // getStateForPlacement (pour le joueur) ou MysteryBoxManager (pour le système).
        // Cette méthode est désormais un simple appel au super.
        // Les positions sont enregistrées dans getStateForPlacement.
        System.out.println("[DEBUG][MysteryBoxBlock] onPlace called for " + pos.toShortString() + ". Part: " + blockstate.getValue(PART));
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        System.out.println("[DEBUG][MysteryBoxBlock] onRemove called for " + pos.toShortString() + ". Part: " + state.getValue(PART) + ". IsMoving: " + isMoving + ". Old Block: " + state.getBlock().getName().getString() + ", New Block: " + newState.getBlock().getName().getString());
        
        // --- DEBUT DU FIX ---
        // Si le bloc est remplacé par un EmptyMysteryBoxBlock, c'est une transformation système,
        // nous ne voulons PAS supprimer les positions de WorldConfig.
        if (newState.getBlock() == ZombieroolModBlocks.EMPTYMYSTERYBOX.get()) {
            System.out.println("[DEBUG][MysteryBoxBlock] onRemove: Block is being transformed to EmptyMysteryBox, NOT unregistering from WorldConfig.");
            // Corrected super.onRemove call to match the required signature
            super.onRemove(state, worldIn, pos, newState, isMoving); 
            return; // Sortir sans exécuter la logique de suppression des positions
        }
        // --- FIN DU FIX ---

        // S'assure que c'est une vraie suppression de bloc et non juste un changement d'état ou un déplacement
        if (state.getBlock() != newState.getBlock() && !isMoving) {
            Direction facing = state.getValue(FACING);
            BlockPos otherPartPos;

            // Détermine la position de l'autre partie
            if (state.getValue(PART)) { // Si c'est la partie "PART" (gauche) qui est brisée
                otherPartPos = MysteryBoxManager.getOppositeOtherPartPos(pos, facing);
                System.out.println("[DEBUG][MysteryBoxBlock] Detected as 'other part'. Calculated main part at: " + otherPartPos.toShortString());
            } else { // Si c'est la partie principale (droite) qui est brisée
                otherPartPos = MysteryBoxManager.getOtherPartPos(pos, facing);
                System.out.println("[DEBUG][MysteryBoxBlock] Detected as 'main part'. Calculated other part at: " + otherPartPos.toShortString());
            }

            // Unregister both positions from WorldConfig
            if (!worldIn.isClientSide() && worldIn instanceof ServerLevel serverWorld) {
                System.out.println("[DEBUG][MysteryBoxBlock] Getting WorldConfig in onRemove for true destruction...");
                WorldConfig config = WorldConfig.get(serverWorld);
                System.out.println("[DEBUG][MysteryBoxBlock] WorldConfig.get result: MysteryBoxPositions count: " + config.getMysteryBoxPositions().size());

                config.removeMysteryBoxPosition(pos.immutable());
                // Seulement si l'autre partie existe et est du même type, la retirer aussi du saveddata.
                // Cela gère le cas où l'autre partie aurait été détruite indépendamment.
                if (worldIn.getBlockState(otherPartPos).is(this)) { // Vérifie que l'autre bloc est toujours une MysteryBox (pas déjà détruite)
                    System.out.println("[DEBUG][MysteryBoxBlock] Found other part to remove from config: " + otherPartPos.toShortString());
                    config.removeMysteryBoxPosition(otherPartPos.immutable());
                } else {
                    System.out.println("[DEBUG][MysteryBoxBlock] Other part at " + otherPartPos.toShortString() + " is not the same block type or already removed from config.");
                }
                System.out.println("[DEBUG][MysteryBoxBlock] Removed positions from WorldConfig. Current count: " + config.getMysteryBoxPositions().size());
            }

            // Si l'autre partie existe et est du même type de bloc, la détruire sans drop
            if (worldIn.getBlockState(otherPartPos).is(this) && !worldIn.isClientSide()) {
                System.out.println("[DEBUG][MysteryBoxBlock] Destroying linked part at " + otherPartPos.toShortString() + " without drops.");
                worldIn.setBlockAndUpdate(otherPartPos, Blocks.AIR.defaultBlockState());
            }
        } else if (isMoving) {
            System.out.println("[DEBUG][MysteryBoxBlock] onRemove called for state change/moving. Not removing from config.");
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
