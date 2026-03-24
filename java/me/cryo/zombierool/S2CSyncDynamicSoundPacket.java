package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.DynamicSoundLoader;

import java.util.function.Supplier;

/**
 * Paquet S2C pour le transfert fragmenté de fichiers .ogg.
 *
 * Protocole à 3 types de messages pour contourner la limite de 1 Mo de Forge :
 *
 *  BEGIN → annonce un nouveau transfert (soundEventName, category, totalSize)
 *  DATA  → un chunk de bytes (max CHUNK_SIZE)
 *  END   → signale que tous les chunks ont été envoyés
 *
 * Le client accumule les chunks dans DynamicSoundLoader et assemble le fichier
 * complet à la réception de END.
 */
public class S2CSyncDynamicSoundPacket {

    public static final int CHUNK_SIZE = 900_000; // 900 Ko < limite 1 Mo de Forge

    public enum Type { BEGIN, DATA, END }

    private final Type type;
    // BEGIN
    private final String soundEventName;
    private final String category;
    private final int totalSize;
    // DATA
    private final int chunkIndex;
    private final byte[] data;

    /** Constructeur BEGIN */
    public static S2CSyncDynamicSoundPacket begin(String soundEventName, String category, int totalSize) {
        return new S2CSyncDynamicSoundPacket(Type.BEGIN, soundEventName, category, totalSize, 0, null);
    }

    /** Constructeur DATA */
    public static S2CSyncDynamicSoundPacket data(String soundEventName, int chunkIndex, byte[] chunk) {
        return new S2CSyncDynamicSoundPacket(Type.DATA, soundEventName, "", 0, chunkIndex, chunk);
    }

    /** Constructeur END */
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
            case END -> {} // rien à écrire
        }
    }

    public static S2CSyncDynamicSoundPacket decode(FriendlyByteBuf buf) {
        Type type = buf.readEnum(Type.class);
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
        };
    }

    public static void handle(S2CSyncDynamicSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                switch (msg.type) {
                    case BEGIN -> DynamicSoundLoader.beginTransfer(msg.soundEventName, msg.category, msg.totalSize);
                    case DATA  -> DynamicSoundLoader.receiveChunk(msg.soundEventName, msg.chunkIndex, msg.data);
                    case END   -> DynamicSoundLoader.finalizeTransfer(msg.soundEventName);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}