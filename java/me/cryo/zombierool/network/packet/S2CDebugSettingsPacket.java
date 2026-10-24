package me.cryo.zombierool.network.packet;

import java.util.function.Supplier;
import me.cryo.zombierool.gameplay.DebugPlayerControls;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

public record S2CDebugSettingsPacket(boolean god,boolean noclip,int perkLimit) {
    public static void encode(S2CDebugSettingsPacket p,FriendlyByteBuf b){b.writeBoolean(p.god);b.writeBoolean(p.noclip);b.writeVarInt(p.perkLimit);}
    public static S2CDebugSettingsPacket decode(FriendlyByteBuf b){return new S2CDebugSettingsPacket(b.readBoolean(),b.readBoolean(),b.readVarInt());}
    public static void handle(S2CDebugSettingsPacket p,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{
            DebugPlayerControls.clientGod=p.god;DebugPlayerControls.clientNoclip=p.noclip;DebugPlayerControls.clientPerkLimit=p.perkLimit;
            var player=net.minecraft.client.Minecraft.getInstance().player;
            if(player!=null)player.noPhysics=p.noclip&&player.getAbilities().flying||player.isSpectator();
        }));ctx.get().setPacketHandled(true);
    }
}
