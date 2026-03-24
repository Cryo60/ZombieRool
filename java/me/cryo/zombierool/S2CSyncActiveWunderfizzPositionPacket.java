package me.cryo.zombierool.network;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

public class S2CSyncActiveWunderfizzPositionPacket {
    private final BlockPos activePos;
    private final boolean hasActivePosition;

    public S2CSyncActiveWunderfizzPositionPacket(BlockPos activePos) {
        this.activePos = activePos;
        this.hasActivePosition = activePos != null;
    }

    public S2CSyncActiveWunderfizzPositionPacket(FriendlyByteBuf buf) {
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
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                me.cryo.zombierool.handlers.KeyInputHandler.setActiveWunderfizzPosition(activePos);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}