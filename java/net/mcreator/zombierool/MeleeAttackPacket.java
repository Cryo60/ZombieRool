package net.mcreator.zombierool.network;

import java.util.function.Supplier;

import net.mcreator.zombierool.procedures.MeleeAttackHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class MeleeAttackPacket {

    public MeleeAttackPacket() {
    }

    public MeleeAttackPacket(FriendlyByteBuf buffer) {
    }

    public void encode(FriendlyByteBuf buffer) {
    }

    public static void handle(MeleeAttackPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                MeleeAttackHandler.performMeleeAttack(sender);
            }
        });
        context.setPacketHandled(true);
    }
}