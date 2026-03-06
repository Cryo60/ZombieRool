package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientBlockCrackHandler;

import java.util.function.Supplier;

public class VisualBlockCrackPacket {
    private final BlockPos pos;
    private final int damageLevel; // 0-9 pour la texture de fissure

    public VisualBlockCrackPacket(BlockPos pos, int damageLevel) {
        this.pos = pos;
        this.damageLevel = damageLevel;
    }

    public VisualBlockCrackPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.damageLevel = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeInt(this.damageLevel);
    }

    public static void handle(VisualBlockCrackPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBlockCrackHandler.handlePacket(msg.pos, msg.damageLevel));
        });
        ctx.get().setPacketHandled(true);
    }
}