package net.mcreator.zombierool.handlers;

import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.block.ObstacleDoorBlock;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;
import net.mcreator.zombierool.init.ZombieroolModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.mcreator.zombierool.item.IngotSaleItem;
import net.minecraft.world.item.ItemStack;

import net.mcreator.zombierool.WaveManager;
import net.mcreator.zombierool.spawner.SpawnerRegistry;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;

import java.util.*;

public class ObstaclePurchaseHandler {

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(Player player) {
        // For server-side, we can't directly access client language settings.
        // This is a placeholder. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For this example, we'll assume English if a player context is available.
        return true; 
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void tryPurchase(Player player, BlockPos pos) {
        // Ne rien faire côté client ou en créatif
        if (player.level().isClientSide() || player.isCreative()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
    
        // Vérifier la distance
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 2.0D) {
            player.sendSystemMessage(getTranslatedComponent(player, "Approchez-vous à 1 bloc !", "Get closer (within 1 block)!"));
            return;
        }
    
        // Cible bien une ObstacleDoor
        BlockEntity te = player.level().getBlockEntity(pos);
        if (!(te instanceof ObstacleDoorBlockEntity be)) return;
    
        int prix = be.getPrix();
        int finalCanal = be.getCanalAsInt(); // récupère directement l'int
    
        if (prix <= 0) {
            player.sendSystemMessage(getTranslatedComponent(player, "Ce bloc n'est pas à vendre !", "This block is not for sale!"));
            return;
        }
    
        // Vérifier points / IngotSaleItem
        int playerPoints = PointManager.getScore(player);
        boolean hasFireSale = player.getInventory().items.stream()
            .anyMatch(stack -> stack.getItem() instanceof IngotSaleItem);
        if (!hasFireSale && playerPoints < prix) {
            player.sendSystemMessage(getTranslatedComponent(player, "Points insuffisants ! Nécessaire: " + prix, "Insufficient points! Required: " + prix));
            return;
        }
        if (hasFireSale) {
            // Consommer 1 ingot
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof IngotSaleItem) {
                    stack.shrink(1);
                    break;
                }
            }
        } else {
            PointManager.modifyScore(player, -prix);
        }
    
        // Transformer les blocs d'obstacle
        Set<BlockPos> connected = new HashSet<>();
        findAllConnectedBlocks(player.level(), pos, connected);
        transformBlocks(player.level(), connected);
    
        // 1) On prévient le WaveManager du canal débloqué
        WaveManager.setCurrentCanal(0);
    
        // DEBUG — afficher le canal de l'obstacle
        //System.out.println("[Purchase] finalCanal=" + finalCanal);
    
        // 2) On récupère tous les spawners (actifs ou non) de ce canal
        List<BlockEntity> spawners = SpawnerRegistry.getAllSpawnersByCanal(player.level(), finalCanal); // NOUVEAU : utiliser player.level()
    
        // DEBUG — nombre de spawners trouvés
        //System.out.println("[Purchase] spawners trouvés pour canal " + finalCanal + " : " + spawners.size());
    
        if (spawners.isEmpty()) {
            //System.err.println("[Purchase] Aucun spawner pour le canal " + finalCanal);
        }
    
        // 3) On passe chacun sur le canal 0 = activation
        for (BlockEntity sp : spawners) {
            if (sp instanceof SpawnerZombieBlockEntity z) {
                z.setCanal(0);
            } else if (sp instanceof SpawnerDogBlockEntity d) {
                d.setCanal(0);
            } else if (sp instanceof SpawnerCrawlerBlockEntity c) {
                c.setCanal(0);
            }
        }
    
        // 4) Jouer le son d'achat
        serverPlayer.playNotifySound(
            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")),
            SoundSource.BLOCKS,
            1.0F,
            1.0F
        );
    }


    private static void findAllConnectedBlocks(Level world, BlockPos startPos, Set<BlockPos> result) {
        if (result.contains(startPos)) return;

        if (isValidBlock(world, startPos)) {
            result.add(startPos);
            for (Direction dir : Direction.values()) {
                findAllConnectedBlocks(world, startPos.relative(dir), result);
            }
        }
    }

    private static boolean isValidBlock(Level world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof ObstacleDoorBlock;
    }

    private static void transformBlocks(Level world, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            world.setBlock(pos, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
        }
    }
}
