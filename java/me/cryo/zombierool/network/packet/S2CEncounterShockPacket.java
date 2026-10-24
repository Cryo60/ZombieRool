package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CEncounterShockPacket(int ticks){
    public static void encode(S2CEncounterShockPacket p,FriendlyByteBuf b){b.writeVarInt(p.ticks);}
    public static S2CEncounterShockPacket decode(FriendlyByteBuf b){return new S2CEncounterShockPacket(b.readVarInt());}
    public static void handle(S2CEncounterShockPacket p,Supplier<NetworkEvent.Context> context){var c=context.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.EncounterScreenEffects.shock(p.ticks)));c.setPacketHandled(true);}
}
