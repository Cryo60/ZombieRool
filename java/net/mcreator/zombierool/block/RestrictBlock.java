package net.mcreator.zombierool.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Collections;
import java.util.List;

import net.minecraft.network.chat.Component; // Added import for Component
import net.minecraft.world.item.TooltipFlag; // Added import for TooltipFlag
import net.minecraft.client.Minecraft; // Added import for Minecraft client

public class RestrictBlock extends Block {

    public RestrictBlock() {
        super(BlockBehaviour.Properties.of()
            // Pas de Material pour éviter les soucis en 1.20
            .sound(SoundType.EMPTY)
            .strength(-1, 3600000)
            .noOcclusion() // Empêche certains problèmes de rendu et de x-ray
            .isSuffocating((state, world, pos) -> false)
            .isViewBlocking((state, world, pos) -> false)
            .lightLevel(state -> 0)
            .noLootTable()
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
        list.add(Component.literal(getTranslatedMessage("§9Bloc de Restriction", "§9Restriction Block")));
        list.add(Component.literal(getTranslatedMessage("§7Empêche toutes les entités de passer à travers.", "§7Prevents all entities from passing through.")));
        list.add(Component.literal(getTranslatedMessage("§7Invisible et non-collidable pour les joueurs en mode Survie (sauf projectiles).", "§7Invisible and non-collidable for players in Survival mode (except projectiles).")));
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 0; // Laisse passer la lumière
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        // Évite d’afficher la face commune entre deux RestrictBlock
        return adjacent.getBlock() == this;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        // En mode créatif, le bloc s’affiche normalement ; sinon, il est invisible.
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            return RenderShape.MODEL;
        }
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // Pour l'affichage visuel de l’occlusion : on renvoie toujours la forme d’un cube plein,
        // ce qui empêche de voir à travers (même si le rendu est invisible en survie).
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.isCreative()) {
                return Shapes.block();
            }
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // Essai de récupérer l’entité via le contexte (si c’est un EntityCollisionContext)
        Entity entity = null;
        if (context instanceof EntityCollisionContext ec) {
            entity = ec.getEntity();
        }
        // Si aucune entité n'est fournie (ce qui arrive souvent pour des projectiles à faible puissance)
        // ou si l’entité est un Projectile, ou si c’est un joueur créatif, alors on laisse passer.
        if (entity == null || entity instanceof Projectile || (entity instanceof Player p && p.isCreative())) {
            return Shapes.empty();
        }
        // Sinon, collision pleine pour bloquer les entités en survie.
        return Shapes.block();
    }

    /**
     * En surchargeant getBlockSupportShape pour renvoyer une forme vide,
     * on empêche généralement que d’autres blocs (fences, murs, vitres, etc.) ne se connectent à ce bloc.
     */
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, Mob mob) {
        return BlockPathTypes.BLOCKED;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }
}
