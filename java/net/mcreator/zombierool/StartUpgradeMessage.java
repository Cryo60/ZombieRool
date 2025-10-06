package net.mcreator.zombierool.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.logic.PackAPunchManager;

public class StartUpgradeMessage {
    private final BlockPos pos;

    public StartUpgradeMessage(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(StartUpgradeMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static StartUpgradeMessage decode(FriendlyByteBuf buf) {
        return new StartUpgradeMessage(buf.readBlockPos());
    }

    public static void handler(StartUpgradeMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            PackAPunchManager.tryUsePack(player, level, msg.pos);
        });
        ctx.get().setPacketHandled(true);
    }
}