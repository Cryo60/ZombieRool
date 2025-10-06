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
import net.minecraft.client.Minecraft; // Import for Minecraft client
import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.world.item.TooltipFlag; // Import for TooltipFlag

import java.util.List;
import java.util.Collections;
import java.util.Random;

public class PathBlock extends Block {
    public PathBlock() {
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
        list.add(Component.literal(getTranslatedMessage("§9Chemin des Zombies", "§9Zombie Path")));
        list.add(Component.literal(getTranslatedMessage("§7Définit les zones où les zombies peuvent se déplacer.", "§7Defines areas where zombies can move.")));
        list.add(Component.literal(getTranslatedMessage("§7Si un joueur peut atteindre un endroit, les zombies le peuvent aussi.", "§7If a player can reach a location, zombies can follow.")));
        list.add(Component.literal(getTranslatedMessage("§7Agit comme une 'zone jouable' pour les entités hostiles.", "§7Acts as a 'playable zone' for hostile entities.")));
        list.add(Component.literal(getTranslatedMessage("§7Invisible et non-collidable pour les joueurs en mode Survie.", "§7Invisible and non-collidable for players in Survival mode.")));
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

    public void tick(BlockState state, ServerLevel world, BlockPos pos, Random random) {
        world.sendBlockUpdated(pos, state, state, 3);
        world.scheduleTick(pos, this, 20);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        world.scheduleTick(pos, this, 20);
        super.onPlace(state, world, pos, oldState, isMoving);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        // Toujours navigable pour les entités terrestres
        return type == PathComputationType.LAND;
    }
}
