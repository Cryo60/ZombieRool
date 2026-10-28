package me.cryo.zombierool.mixins;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class MatchPausePacketMixin {
 @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"),cancellable=true)
 private void zr$pausePackets(ChannelHandlerContext channel,Packet<?> packet,CallbackInfo ci){
  if(!me.cryo.zombierool.gameplay.MatchPause.frozen()||!(((Connection)(Object)this).getPacketListener() instanceof net.minecraft.server.network.ServerGamePacketListenerImpl))return;
  // Keep the connection, chat, teleport acknowledgement and pause vote responsive.
  if(packet instanceof ServerboundKeepAlivePacket||packet instanceof ServerboundPongPacket||packet instanceof ServerboundChatPacket||packet instanceof ServerboundChatAckPacket||packet instanceof ServerboundAcceptTeleportationPacket||packet instanceof ServerboundClientInformationPacket)return;
  if(packet instanceof ServerboundCustomPayloadPacket payload&&payload.getIdentifier().equals(me.cryo.zombierool.gameplay.MatchPause.ID))return;
  ci.cancel();
 }
}
