package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.gameplay.WaveManager;
import java.util.function.Supplier;
public class C2SRequestConfigMenuPacket {
    private final boolean enemies;
    public C2SRequestConfigMenuPacket() {this(false);}
    public C2SRequestConfigMenuPacket(boolean enemies) {this.enemies=enemies;}
    public C2SRequestConfigMenuPacket(FriendlyByteBuf buf) {enemies=buf.readBoolean();}
    public void encode(FriendlyByteBuf buf) {buf.writeBoolean(enemies);}
    public static void handle(C2SRequestConfigMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                CompoundTag tag = new CompoundTag();if(msg.enemies)tag.putString("focusTab","enemies");
                WorldConfig.get(player.serverLevel()).saveEditable(tag);
                                    me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket.addLootTables(tag,player.serverLevel());
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2COpenConfigMenuPacket(tag, WaveManager.isGameRunning(), WaveManager.getCurrentWave())
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
