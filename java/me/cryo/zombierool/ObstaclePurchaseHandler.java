package me.cryo.zombierool.handlers;

import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.util.PlayerVoiceManager;
import me.cryo.zombierool.career.CareerManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CUpdateOverlayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class ObstaclePurchaseHandler {

    public static void tryPurchase(Player player, BlockPos pos) {
        if (player.level().isClientSide() || player.isCreative()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 9.0D) {
            player.sendSystemMessage(Component.translatable("message.zombierool.obstacle.get_closer"));
            return;
        }

        BlockEntity te = player.level().getBlockEntity(pos);
        if (!(te instanceof ObstacleDoorBlockEntity be)) return;

        int prix = be.getPrix();
        int finalCanal = be.getCanalAsInt(); 

        if (prix <= 0) {
            player.sendSystemMessage(Component.translatable("message.zombierool.obstacle.not_for_sale"));
            return;
        }

        int playerPoints = PointManager.getScore(player);
        boolean hasFireSale = player.getInventory().items.stream()
                .anyMatch(stack -> stack.getItem() instanceof IngotSaleItem);

        if (!hasFireSale && playerPoints < prix) {
            player.sendSystemMessage(Component.translatable("message.zombierool.obstacle.insufficient_points", prix));
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

        if (!WaveManager.areCheatsUsed()) {
            CareerManager.progressChallenge(serverPlayer, CareerManager.ChallengeType.OBSTACLE_BOUGHT, 1);
        }
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
        WorldConfig config = world instanceof ServerLevel sl ? WorldConfig.get(sl) : null;
        for (BlockPos pos : positions) {
            world.setBlock(pos, ZombieroolModBlocks.PATH.get().defaultBlockState(), 3);
            if (config != null) {
                for (Direction dir : Direction.values()) {
                    String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + dir.getName();
                    if (config.getMapOverlays().containsKey(key)) {
                        config.removeMapOverlay(key);
                        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(pos)),
                            new S2CUpdateOverlayPacket(pos, dir, "", 0, false));
                    }
                }
            }
        }
    }
}