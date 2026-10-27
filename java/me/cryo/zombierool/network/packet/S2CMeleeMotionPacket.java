package me.cryo.zombierool.network.packet;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

public record S2CMeleeMotionPacket(UUID player,int duration,int contact,int combo,boolean heavy,boolean hit) {
    public S2CMeleeMotionPacket(FriendlyByteBuf b){this(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean());}
    public void encode(FriendlyByteBuf b){b.writeUUID(player);b.writeVarInt(duration);b.writeVarInt(contact);b.writeVarInt(combo);b.writeBoolean(heavy);b.writeBoolean(hit);}
    public static void handle(S2CMeleeMotionPacket p,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.MeleeMotionClient.accept(p)));
        ctx.get().setPacketHandled(true);
    }
}
