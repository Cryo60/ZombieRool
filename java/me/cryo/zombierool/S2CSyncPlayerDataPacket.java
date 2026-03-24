package me.cryo.zombierool.core.network;

import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CSyncPlayerDataPacket {
    private final CompoundTag data;

    public S2CSyncPlayerDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(S2CSyncPlayerDataPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static S2CSyncPlayerDataPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncPlayerDataPacket(buf.readNbt());
    }

    public static void handle(S2CSyncPlayerDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handle(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientPacketHandler {
        public static void handle(S2CSyncPlayerDataPacket msg) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                    cap.deserializeNBT(msg.data);
                });
            }
        }
    }
}