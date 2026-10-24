package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CEquipmentFlashPacket(int ticks) {
    public static void encode(S2CEquipmentFlashPacket p,FriendlyByteBuf b){b.writeVarInt(p.ticks);}
    public static S2CEquipmentFlashPacket decode(FriendlyByteBuf b){return new S2CEquipmentFlashPacket(b.readVarInt());}
    public static void handle(S2CEquipmentFlashPacket p,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->me.cryo.zombierool.client.EquipmentFlashOverlay.flash(p.ticks));ctx.get().setPacketHandled(true);}
}
