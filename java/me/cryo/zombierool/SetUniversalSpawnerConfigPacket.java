package me.cryo.zombierool.network;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetUniversalSpawnerConfigPacket {
    private final BlockPos pos;
    private final UniversalSpawnerSystem.SpawnerMobType mobType;
    private final String zone;
    private final String startChannels;
    private final String stopChannels;
    private final boolean requirePower;
    private final int spawnWeight;

    public SetUniversalSpawnerConfigPacket(BlockPos pos, UniversalSpawnerSystem.SpawnerMobType mobType, String zone, String startChannels, String stopChannels, boolean requirePower, int spawnWeight) {
        this.pos = pos;
        this.mobType = mobType;
        this.zone = zone;
        this.startChannels = startChannels;
        this.stopChannels = stopChannels;
        this.requirePower = requirePower;
        this.spawnWeight = spawnWeight;
    }

    public static void encode(SetUniversalSpawnerConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.mobType);
        buf.writeUtf(msg.zone);
        buf.writeUtf(msg.startChannels);
        buf.writeUtf(msg.stopChannels);
        buf.writeBoolean(msg.requirePower);
        buf.writeInt(msg.spawnWeight);
    }

    public static SetUniversalSpawnerConfigPacket decode(FriendlyByteBuf buf) {
        return new SetUniversalSpawnerConfigPacket(
                buf.readBlockPos(),
                buf.readEnum(UniversalSpawnerSystem.SpawnerMobType.class),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readInt()
        );
    }

    public static void handle(SetUniversalSpawnerConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity spawner) {
                    spawner.setConfig(msg.mobType, msg.zone, msg.startChannels, msg.stopChannels, msg.requirePower, msg.spawnWeight);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}