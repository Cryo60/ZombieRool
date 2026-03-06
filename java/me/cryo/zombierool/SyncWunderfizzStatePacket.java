package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;

import java.util.function.Supplier;

public class SyncWunderfizzStatePacket {
    private final BlockPos pos;
    private final String state;
    private final String selectedPerkId;

    public SyncWunderfizzStatePacket(BlockPos pos, String state, String selectedPerkId) {
        this.pos = pos;
        this.state = state;
        this.selectedPerkId = selectedPerkId;
    }

    public SyncWunderfizzStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.state = buf.readUtf();
        this.selectedPerkId = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(state);
        buf.writeBoolean(selectedPerkId != null);
        if (selectedPerkId != null) {
            buf.writeUtf(selectedPerkId);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Level level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof DerWunderfizzBlockEntity wunderfizz) {
                    wunderfizz.setStateFromPacket(state, selectedPerkId);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}