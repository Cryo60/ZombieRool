package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.career.CareerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SEquipCamoPacket {
    private final String weaponId;
    private final String camoId;

    public C2SEquipCamoPacket(String weaponId, String camoId) {
        this.weaponId = weaponId;
        this.camoId = camoId;
    }

    public C2SEquipCamoPacket(FriendlyByteBuf buf) {
        this.weaponId = buf.readUtf();
        this.camoId = buf.readUtf();
    }

    public static void encode(C2SEquipCamoPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.weaponId);
        buf.writeUtf(msg.camoId);
    }

    public static C2SEquipCamoPacket decode(FriendlyByteBuf buf) {
        return new C2SEquipCamoPacket(buf);
    }

    public static void handle(C2SEquipCamoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CareerManager.equipCamo(player, msg.weaponId, msg.camoId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}