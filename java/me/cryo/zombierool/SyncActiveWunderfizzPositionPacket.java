package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet pour synchroniser la position active de la Wunderfizz avec les clients
 * (différent de SyncWunderfizzStatePacket qui synchronise l'état de la machine)
 */
public class SyncActiveWunderfizzPositionPacket {
    private final BlockPos activePos;
    private final boolean hasActivePosition;

    public SyncActiveWunderfizzPositionPacket(BlockPos activePos) {
        this.activePos = activePos;
        this.hasActivePosition = activePos != null;
    }

    public SyncActiveWunderfizzPositionPacket(FriendlyByteBuf buf) {
        this.hasActivePosition = buf.readBoolean();
        this.activePos = hasActivePosition ? buf.readBlockPos() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hasActivePosition);
        if (hasActivePosition) {
            buf.writeBlockPos(activePos);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Stocker la position active côté client
            me.cryo.zombierool.handlers.KeyInputHandler.setActiveWunderfizzPosition(activePos);
        });
        ctx.get().setPacketHandled(true);
    }
}