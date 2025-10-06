package net.mcreator.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;
import net.mcreator.zombierool.handlers.ObstaclePurchaseHandler;

import net.mcreator.zombierool.block.ObstacleDoorBlock;

import java.util.function.Supplier;

public class PurchaseObstacleMessage {
    private final BlockPos blockPos;

    public PurchaseObstacleMessage(BlockPos pos) {
        this.blockPos = pos;
    }

    public PurchaseObstacleMessage(FriendlyByteBuf buffer) {
        this.blockPos = buffer.readBlockPos();
    }

    public static void encode(PurchaseObstacleMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.blockPos);
    }

    public static void handler(PurchaseObstacleMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && !player.level().isClientSide()) {
                BlockPos pos = message.getBlockPos();
                
                if (isValidTarget(player, pos)) {
                    ObstaclePurchaseHandler.tryPurchase(player, pos);
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean isValidTarget(ServerPlayer player, BlockPos pos) {
        return player != null 
            && player.level().getBlockState(pos).getBlock() instanceof ObstacleDoorBlock
            && player.level().getBlockEntity(pos) instanceof ObstacleDoorBlockEntity
            && player.distanceToSqr(pos.getCenter()) <= 64.0D; // Meilleure vérification de distance
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }
}