package me.cryo.zombierool.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CSyncWunderfizzStatePacket {
    private final BlockPos pos;
    private final String state;
    private final String selectedPerkId;
    private final boolean shared;
    private final UUID buyer;

    public S2CSyncWunderfizzStatePacket(BlockPos pos, String state, String selectedPerkId, boolean shared, UUID buyer) {
        this.pos = pos;
        this.state = state;
        this.selectedPerkId = selectedPerkId;
        this.shared = shared;
        this.buyer = buyer;
    }

    public S2CSyncWunderfizzStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.state = buf.readUtf();
        this.selectedPerkId = buf.readBoolean() ? buf.readUtf() : null;
        this.shared = buf.readBoolean();
        this.buyer = buf.readBoolean() ? buf.readUUID() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(state);
        buf.writeBoolean(selectedPerkId != null);
        if (selectedPerkId != null) {
            buf.writeUtf(selectedPerkId);
        }
        buf.writeBoolean(shared);
        buf.writeBoolean(buyer != null);
        if (buyer != null) {
            buf.writeUUID(buyer);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(this)));
        ctx.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CSyncWunderfizzStatePacket msg) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;
            BlockEntity be = level.getBlockEntity(msg.pos);
            if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
                wunderfizz.setStateFromPacket(msg.state, msg.selectedPerkId, msg.shared, msg.buyer);
            }
        }
    }
}