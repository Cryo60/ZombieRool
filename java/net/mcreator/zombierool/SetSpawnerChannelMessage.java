package net.mcreator.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;

import java.util.function.Supplier;

public class SetSpawnerChannelMessage {
    private final BlockPos pos;
    private final int canal;

    public SetSpawnerChannelMessage(BlockPos pos, int canal) {
        this.pos = pos;
        this.canal = canal;
    }

    public static void encode(SetSpawnerChannelMessage message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeInt(message.canal);
    }

    public static SetSpawnerChannelMessage decode(FriendlyByteBuf buffer) {
        return new SetSpawnerChannelMessage(buffer.readBlockPos(), buffer.readInt());
    }

    public static void handle(SetSpawnerChannelMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Ne traiter que si c'est bien le serveur qui reçoit
            if (context.getSender() != null) {
                BlockEntity be = context.getSender().level().getBlockEntity(message.pos);
                if (be instanceof SpawnerZombieBlockEntity z) {
                    z.setCanal(message.canal);
                    // plus d’activation ici
                } else if (be instanceof SpawnerCrawlerBlockEntity c) {
                    c.setCanal(message.canal);
                } else if (be instanceof SpawnerDogBlockEntity d) {
                    d.setCanal(message.canal);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
