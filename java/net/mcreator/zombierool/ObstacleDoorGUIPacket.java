package net.mcreator.zombierool.network;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent

import net.mcreator.zombierool.init.ZombieroolModBlocks;
import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.block.ObstacleDoorBlock;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;

import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ObstacleDoorGUIPacket {
    private final int x;
    private final int y;
    private final int z;
    private final int prix;
    private final String canal;
    private final boolean isCreative;

    public ObstacleDoorGUIPacket(int x, int y, int z, int prix, String canal, boolean isCreative) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.prix = prix;
        this.canal = canal;
        this.isCreative = isCreative;
    }

    // Helper method to check if the player's client language is English
    private static boolean isEnglishClient(ServerPlayer player) {
        // This is a simplified check. In a real scenario, you might need to get the player's client language setting.
        // For server-side packets, the server doesn't directly know client language.
        // A common approach is to send the client language to the server, or use a default.
        // For this example, we'll assume English for server-side messages for now, or implement client-side language sync.
        // For a more robust solution, you would need a custom packet from client to server to sync language.
        return true; // Placeholder: Assume English for server-side messages for now.
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static void encode(ObstacleDoorGUIPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.x);
        buffer.writeInt(msg.y);
        buffer.writeInt(msg.z);
        buffer.writeInt(msg.prix);
        buffer.writeUtf(msg.canal);
        buffer.writeBoolean(msg.isCreative);
    }

    public static ObstacleDoorGUIPacket decode(FriendlyByteBuf buffer) {
        return new ObstacleDoorGUIPacket(
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readUtf(),
            buffer.readBoolean()
        );
    }

    public static void handle(ObstacleDoorGUIPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Level world = player.level();
                BlockPos pos = new BlockPos(message.x, message.y, message.z);

                if (!world.isClientSide()) {
                    if (message.isCreative) {
                        handleCreativeMode(world, pos, player, message);
                    } else {
                        handleSurvivalMode(world, pos, player, message);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleCreativeMode(Level world, BlockPos pos, ServerPlayer player, ObstacleDoorGUIPacket message) {
        if (world.getBlockEntity(pos) instanceof ObstacleDoorBlockEntity be) {
            List<BlockPos> connectedBlocks = new ArrayList<>();
            Set<BlockPos> checked = new HashSet<>();
            findAllConnectedBlocks(world, pos, connectedBlocks, checked);

            for (BlockPos blockPos : connectedBlocks) {
                if (world.getBlockEntity(blockPos) instanceof ObstacleDoorBlockEntity memberBe) {
                    memberBe.setPrix(message.prix);
                    memberBe.setCanal(message.canal);
                    memberBe.setChanged();
                    
                    // Synchronisation avec les clients
                    world.sendBlockUpdated(
                        blockPos,
                        world.getBlockState(blockPos),
                        world.getBlockState(blockPos),
                        Block.UPDATE_ALL
                    );
                }
            }
            player.sendSystemMessage(getTranslatedComponent(player, 
                "Configuration sauvegardée sur " + connectedBlocks.size() + " blocs !",
                "Configuration saved for " + connectedBlocks.size() + " blocks!"
            ));
        }
    }

    private static void handleSurvivalMode(Level world, BlockPos pos, ServerPlayer player, ObstacleDoorGUIPacket message) {
        if (message.prix <= 0) {
            player.sendSystemMessage(getTranslatedComponent(player, "Prix invalide !", "Invalid price!"));
            return;
        }

        List<BlockPos> connectedBlocks = new ArrayList<>();
        Set<BlockPos> checked = new HashSet<>();
        findAllConnectedBlocks(world, pos, connectedBlocks, checked);

        int totalCost = message.prix;
        if (PointManager.getScore(player) < totalCost) {
            player.sendSystemMessage(getTranslatedComponent(player, 
                "Points insuffisants : " + totalCost,
                "Insufficient points: " + totalCost
            ));
            return;
        }

        PointManager.modifyScore(player, -totalCost);
        for (BlockPos blockPos : connectedBlocks) {
            world.setBlock(blockPos, ZombieroolModBlocks.PATH.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        
        // Jouer le son pour tous les joueurs
        world.playSound(null, pos, 
            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "buy")),
            SoundSource.BLOCKS, 
            1.0F, 
            1.0F
        );
        
        // player.sendSystemMessage(getTranslatedComponent(player, "Achat réussi de " + connectedBlocks.size() + " blocs !", "Purchase successful for " + connectedBlocks.size() + " blocks!"));
    }

    private static void findAllConnectedBlocks(Level world, BlockPos pos, List<BlockPos> found, Set<BlockPos> checked) {
        if (checked.contains(pos) || !isValidBlock(world, pos)) return;
        
        checked.add(pos);
        found.add(pos);
        
        for (Direction dir : Direction.values()) {
            findAllConnectedBlocks(world, pos.relative(dir), found, checked);
        }
    }

    private static boolean isValidBlock(Level world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof ObstacleDoorBlock;
    }
}
