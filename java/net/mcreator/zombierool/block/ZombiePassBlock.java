package net.mcreator.zombierool.block;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

// Imports nécessaires pour gérer les collisions et les entités
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;

// Imports pour le rendu conditionnel
import net.minecraft.world.level.block.RenderShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// Nouveaux imports pour le tooltip
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.client.Minecraft; // Import for Minecraft client
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Collections;
import javax.annotation.Nullable;

public class ZombiePassBlock extends Block {
    public ZombiePassBlock() {
        super(BlockBehaviour.Properties.of()
            .instrument(NoteBlockInstrument.BASEDRUM)
            .sound(SoundType.EMPTY)
            .strength(-1, 3600000)
            .noOcclusion()
            .isSuffocating((state, world, pos) -> false)
            .isViewBlocking((state, world, pos) -> false)
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
    public int getLightBlock(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 0;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            return RenderShape.MODEL;
        }
        return RenderShape.INVISIBLE;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack itemstack, @Nullable BlockGetter world, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, world, list, flag);
        list.add(Component.literal(getTranslatedMessage("§9Bloc de Passage Zombie", "§9Zombie Pass Block")));
        list.add(Component.literal(getTranslatedMessage("§7Bloque les joueurs en mode Survie.", "§7Blocks players in Survival mode.")));
        list.add(Component.literal(getTranslatedMessage("§7Laisse passer les entités hostiles (zombies, etc.).", "§7Allows hostile entities (zombies, etc.) to pass through.")));
        list.add(Component.literal(getTranslatedMessage("§7Invisible en mode Survie.", "§7Invisible in Survival mode.")));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Entity entity = null;
        if (context instanceof EntityCollisionContext ec) {
            entity = ec.getEntity();
        }

        // Si l'entité est un joueur...
        if (entity instanceof Player player) { // Utilise "player" comme variable locale pour le cast
            // ... et que le joueur est en mode créatif, le bloc le laisse passer.
            if (player.isCreative()) {
                return Shapes.empty(); // Laisse passer les joueurs en créatif
            } else {
                // ... sinon (joueur en survie/aventure), le bloc le bloque.
                return Shapes.block(); // Bloque les joueurs en survie/aventure
            }
        }
        // Pour toutes les autres entités (mobs, projectiles, etc.), le bloc n'a pas de collision.
        return Shapes.empty(); // Laisse passer les autres entités
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> dropsOriginal = super.getDrops(state, builder);
        if (!dropsOriginal.isEmpty())
            return dropsOriginal;
        return Collections.singletonList(new ItemStack(this, 1));
    }
}
