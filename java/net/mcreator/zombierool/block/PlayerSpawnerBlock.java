package net.mcreator.zombierool.block;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraft.client.Minecraft;

import net.mcreator.zombierool.WorldConfig; // Importez votre classe WorldConfig

import java.util.List;
import java.util.Collections;
import net.minecraft.util.RandomSource; // NEW: Import RandomSource

import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag


public class PlayerSpawnerBlock extends Block {
    public PlayerSpawnerBlock() {
        super(BlockBehaviour.Properties.of()
                .sound(SoundType.EMPTY)
                .strength(-1, 3600000)
                .noCollission()   // Pour le joueur, pas de collision physique
                .noOcclusion()    // La lumière traverse le bloc
                .randomTicks()    // Active les ticks aléatoires
        );
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
        list.add(Component.literal(getTranslatedMessage("§9Point d'Apparition du Joueur", "§9Player Spawn Point")));
        list.add(Component.literal(getTranslatedMessage("§7Définit l'endroit où les joueurs apparaîtront.", "§7Defines where players will spawn.")));
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        return adjacentBlockState.getBlock() == this ? true : super.skipRendering(state, adjacentBlockState, side);
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
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext) {
            Player player = ((EntityCollisionContext) context).getEntity() instanceof Player p ? p : null;
            if (player != null && !player.isCreative()) {
                return Shapes.empty();
            }
        }
        return super.getCollisionShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Player player = Minecraft.getInstance().player;
            if (player != null && !player.isCreative()) {
                return Shapes.empty();
            }
        }
        return super.getVisualShape(state, world, pos, context);
    }
    
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Player player = Minecraft.getInstance().player;
            if (player != null && !player.isCreative()) {
                return Shapes.empty();
            }
        }
        return super.getShape(state, world, pos, context);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.player.isCreative()) {
                return RenderShape.INVISIBLE;
            }
        }
        return RenderShape.MODEL;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }

    /**
     * Called by the block's random tick.
     */
    @Override
    public void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) { // CHANGED: Random to RandomSource
        world.sendBlockUpdated(pos, state, state, 3);
        // The original `tick` method had a scheduled tick here.
        // For `randomTick`, it's not typically scheduled, it's called randomly by the game.
        // If you intended a scheduled tick, it should be in `onPlace` or other event handlers.
        // Given `randomTicks()` is enabled, this method will be called randomly.
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, world, pos, oldState, isMoving);
        world.scheduleTick(pos, this, 20); // Schedule a tick if needed for custom logic
        
        // Only run on the server side to save data
        if (!world.isClientSide() && world instanceof ServerLevel serverWorld) {
            WorldConfig config = WorldConfig.get(serverWorld);
            config.addPlayerSpawnerPosition(pos); // Add the position to the saved data
        }
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        // Only run on the server side and if the block is actually being removed (not just state changing)
        if (!worldIn.isClientSide() && worldIn instanceof ServerLevel serverWorld && newState.getBlock() != state.getBlock()) {
            WorldConfig config = WorldConfig.get(serverWorld);
            config.removePlayerSpawnerPosition(pos); // Remove the position from the saved data
        }
        super.onRemove(state, worldIn, pos, newState, isMoving); // Call super after our logic
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        // Toujours navigable pour les entités terrestres
        return type == PathComputationType.LAND;
    }
}
