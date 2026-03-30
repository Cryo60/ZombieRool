package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.DynamicSoundLoader;

import java.util.function.Supplier;

public class S2CSyncDynamicSoundPacket {
    public static final int CHUNK_SIZE = 900_000; 

    public enum Type { START_ALL, BEGIN, DATA, END, FINISH_ALL }

    private final Type type;
    private final String soundEventName;
    private final String category;
    private final int totalSize;
    private final int chunkIndex;
    private final byte[] data;

    public static S2CSyncDynamicSoundPacket startAll() {
        return new S2CSyncDynamicSoundPacket(Type.START_ALL, "", "", 0, 0, null);
    }

    public static S2CSyncDynamicSoundPacket finishAll() {
        return new S2CSyncDynamicSoundPacket(Type.FINISH_ALL, "", "", 0, 0, null);
    }

    public static S2CSyncDynamicSoundPacket begin(String soundEventName, String category, int totalSize) {
        return new S2CSyncDynamicSoundPacket(Type.BEGIN, soundEventName, category, totalSize, 0, null);
    }

    public static S2CSyncDynamicSoundPacket data(String soundEventName, int chunkIndex, byte[] chunk) {
        return new S2CSyncDynamicSoundPacket(Type.DATA, soundEventName, "", 0, chunkIndex, chunk);
    }

    public static S2CSyncDynamicSoundPacket end(String soundEventName) {
        return new S2CSyncDynamicSoundPacket(Type.END, soundEventName, "", 0, 0, null);
    }

    private S2CSyncDynamicSoundPacket(Type type, String soundEventName, String category,
                                       int totalSize, int chunkIndex, byte[] data) {
        this.type = type;
        this.soundEventName = soundEventName;
        this.category = category;
        this.totalSize = totalSize;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }

    public static void encode(S2CSyncDynamicSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
        if (msg.type == Type.START_ALL || msg.type == Type.FINISH_ALL) return;
        
        buf.writeUtf(msg.soundEventName);

        switch (msg.type) {
            case BEGIN -> {
                buf.writeUtf(msg.category);
                buf.writeInt(msg.totalSize);
            }
            case DATA -> {
                buf.writeInt(msg.chunkIndex);
                buf.writeByteArray(msg.data);
            }
            case END -> {} 
        }
    }

    public static S2CSyncDynamicSoundPacket decode(FriendlyByteBuf buf) {
        Type type = buf.readEnum(Type.class);
        if (type == Type.START_ALL) return startAll();
        if (type == Type.FINISH_ALL) return finishAll();

        String soundEventName = buf.readUtf();

        return switch (type) {
            case BEGIN -> {
                String category = buf.readUtf();
                int totalSize = buf.readInt();
                yield new S2CSyncDynamicSoundPacket(type, soundEventName, category, totalSize, 0, null);
            }
            case DATA -> {
                int chunkIndex = buf.readInt();
                byte[] data = buf.readByteArray();
                yield new S2CSyncDynamicSoundPacket(type, soundEventName, "", 0, chunkIndex, data);
            }
            case END -> new S2CSyncDynamicSoundPacket(type, soundEventName, "", 0, 0, null);
            default -> null; // Ne devrait jamais arriver grâce au check précédent
        };
    }

    public static void handle(S2CSyncDynamicSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                switch (msg.type) {
                    case START_ALL -> DynamicSoundLoader.setSyncing(true);
                    case BEGIN -> DynamicSoundLoader.beginTransfer(msg.soundEventName, msg.category, msg.totalSize);
                    case DATA  -> DynamicSoundLoader.receiveChunk(msg.soundEventName, msg.chunkIndex, msg.data);
                    case END   -> DynamicSoundLoader.finalizeTransfer(msg.soundEventName);
                    case FINISH_ALL -> DynamicSoundLoader.setSyncing(false);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}