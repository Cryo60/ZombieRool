package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class S2COpenConfigMenuPacket {
    public static void addLootTables(CompoundTag tag,net.minecraft.server.level.ServerLevel level){tag.put("mapConfigDefaults",new me.cryo.zombierool.config.WorldConfig().save(new CompoundTag()));var list=new net.minecraft.nbt.ListTag();for(String id:me.cryo.zombierool.gameplay.MiniBossLoot.tables(level))list.add(net.minecraft.nbt.StringTag.valueOf(id));tag.put("availableBossLootTables",list);tag.put("mapConfigSchemas",me.cryo.zombierool.config.MapConfigExtensions.schemas());tag.put("bossDefinitions",me.cryo.zombierool.config.MiniBossDefinitions.snapshot());}
    public final CompoundTag configData;
    public final boolean isGameRunning;
    public final int currentWave;

    public S2COpenConfigMenuPacket(CompoundTag configData, boolean isGameRunning, int currentWave) {
        this.configData = configData;
        this.isGameRunning = isGameRunning;
        this.currentWave = currentWave;
    }

    public static void encode(S2COpenConfigMenuPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.configData);
        buf.writeBoolean(msg.isGameRunning);
        buf.writeInt(msg.currentWave);
    }

    public static S2COpenConfigMenuPacket decode(FriendlyByteBuf buf) {
        return new S2COpenConfigMenuPacket(buf.readNbt(), buf.readBoolean(), buf.readInt());
    }

    public static void handle(S2COpenConfigMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2COpenConfigMenuPacket msg) {
            me.cryo.zombierool.config.MiniBossDefinitions.receive(msg.configData.getCompound("bossDefinitions"));
            net.minecraft.client.Minecraft.getInstance().setScreen(new me.cryo.zombierool.client.gui.WorldConfigMenuScreen(msg));
        }
    }
}
