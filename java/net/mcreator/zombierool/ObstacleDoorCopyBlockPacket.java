package net.mcreator.zombierool.network; // Assurez-vous que ce chemin de package est correct

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent; // CORRIGÉ: net.minecraftforge.network
import net.minecraftforge.network.NetworkDirection; // CORRIGÉ: net.minecraftforge.network
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

// Ce paquet sera envoyé du CLIENT au SERVEUR pour copier un bloc
public class ObstacleDoorCopyBlockPacket {
    private final BlockPos pos;
    private final ResourceLocation blockToCopyId;

    public ObstacleDoorCopyBlockPacket(BlockPos pos, Block blockToCopy) {
        this.pos = pos;
        this.blockToCopyId = ForgeRegistries.BLOCKS.getKey(blockToCopy);
    }

    public ObstacleDoorCopyBlockPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.blockToCopyId = buffer.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeResourceLocation(this.blockToCopyId);
    }

    public static void handle(ObstacleDoorCopyBlockPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // S'assurer que le code s'exécute côté serveur
            if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                net.minecraft.server.level.ServerPlayer player = context.getSender();
                if (player == null) return; // Devrait toujours être non-null pour un paquet client-serveur

                net.minecraft.world.level.Level level = player.level();
                BlockPos blockPos = msg.pos;
                Block blockToCopy = ForgeRegistries.BLOCKS.getValue(msg.blockToCopyId);

                if (blockToCopy != null) {
                    net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(blockPos);
                    if (entity instanceof net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity obstacleDoorEntity) {
                        obstacleDoorEntity.setCopiedBlock(blockToCopy);
                        obstacleDoorEntity.setChanged(); // Demande la mise à jour et la synchronisation au client
                        //System.out.println("DEBUG SERVER: Paquet ObstacleDoorCopyBlockPacket reçu. Bloc " + blockToCopy.getName().getString() + " copié à " + blockPos);
                    }	
                } else {
                    //System.err.println("ERREUR SERVER: Bloc à copier non trouvé pour l'ID : " + msg.blockToCopyId);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
