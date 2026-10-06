package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.ClientTabListRenderer;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CSyncPlayerStatsPacket {

    private final Map<UUID, PlayerStatsManager.PlayerStats> stats;

    public S2CSyncPlayerStatsPacket(Map<UUID, PlayerStatsManager.PlayerStats> stats) {
        this.stats = stats;
    }

    public S2CSyncPlayerStatsPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        this.stats = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            PlayerStatsManager.PlayerStats s = new PlayerStatsManager.PlayerStats();
            s.score = buf.readInt();
            s.totalPoints = buf.readInt();
            s.kills = buf.readInt();
            s.headshots = buf.readInt();
            s.assists = buf.readInt();
            s.deaths = buf.readInt();
            this.stats.put(uuid, s);
        }
    }

    public static void encode(S2CSyncPlayerStatsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.stats.size());
        for (Map.Entry<UUID, PlayerStatsManager.PlayerStats> entry : msg.stats.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeInt(entry.getValue().score);
            buf.writeInt(entry.getValue().totalPoints);
            buf.writeInt(entry.getValue().kills);
            buf.writeInt(entry.getValue().headshots);
            buf.writeInt(entry.getValue().assists);
            buf.writeInt(entry.getValue().deaths);
        }
    }

    public static S2CSyncPlayerStatsPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncPlayerStatsPacket(buf);
    }

    public static void handle(S2CSyncPlayerStatsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientTabListRenderer.updateStats(msg.stats);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}