package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.LogicalSide;
import java.util.function.Supplier;

public class S2CProgressWeaponStatPacket {

    private final String weaponId;
    private final int kills;
    private final int headshots;
    private final int papCount;

    public S2CProgressWeaponStatPacket(String weaponId, int kills, int headshots, int papCount) {
        this.weaponId = weaponId;
        this.kills = kills;
        this.headshots = headshots;
        this.papCount = papCount;
    }

    public S2CProgressWeaponStatPacket(FriendlyByteBuf buf) {
        this.weaponId = buf.readUtf();
        this.kills = buf.readInt();
        this.headshots = buf.readInt();
        this.papCount = buf.readInt();
    }

    public static void encode(S2CProgressWeaponStatPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.weaponId);
        buf.writeInt(msg.kills);
        buf.writeInt(msg.headshots);
        buf.writeInt(msg.papCount);
    }

    public static void handle(S2CProgressWeaponStatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                me.cryo.zombierool.client.career.LocalCareerManager.addWeaponStat(msg.weaponId, msg.kills, msg.headshots, msg.papCount);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}