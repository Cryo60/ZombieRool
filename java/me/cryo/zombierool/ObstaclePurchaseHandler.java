package me.cryo.zombierool.handlers;

import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.block.ObstacleDoorBlock;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class ObstaclePurchaseHandler {

    private static boolean isEnglishClient(Player player) {
        return true; 
    }

    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void tryPurchase(Player player, BlockPos pos) {
        if (player.level().isClientSide() || player.isCreative()) return;

        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 4.0D) {
            player.sendSystemMessage(getTranslatedComponent(player, "Approchez-vous !", "Get closer!"));
            return;
        }

        BlockEntity te = player.level().getBlockEntity(pos);
        if (!(te instanceof ObstacleDoorBlockEntity be)) return;

        int prix = be.getPrix();
        int finalCanal = be.getCanalAsInt(); 

        if (prix <= 0) {
            player.sendSystemMessage(getTranslatedComponent(player, "Ce bloc n'est pas à vendre !", "This block is not for sale!"));
            return;
        }

        int playerPoints = PointManager.getScore(player);

        boolean hasFireSale = player.getInventory().items.stream()
            .anyMatch(stack -> stack.getItem() instanceof IngotSaleItem);

        if (!hasFireSale && playerPoints < prix) {
            player.sendSystemMessage(getTranslatedComponent(player, "Points insuffisants ! Nécessaire: " + prix, "Insufficient points! Required: " + prix));
            PlayerVoiceManager.playNoMoneySound(player, player.level());
            return;
        }

        if (hasFireSale) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof IngotSaleItem) {
                    stack.shrink(1);
                    break;
                }
            }
        } else {
            PointManager.modifyScore(player, -prix);
        }

        Set<BlockPos> connected = new HashSet<>();
        findAllConnectedBlocks(player.level(), pos, connected);
        transformBlocks(player.level(), connected);

        WaveManager.unlockChannel(finalCanal);

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