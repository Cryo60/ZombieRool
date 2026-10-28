package me.cryo.zombierool.mixins;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MatchPausePlayerMixin {
 @Shadow private boolean clientIsFloating;
 @Shadow private boolean clientVehicleIsFloating;
 @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerPlayer;doTick()V"))
 private void zr$freezePlayer(ServerPlayer player){
  if(!me.cryo.zombierool.gameplay.MatchPause.frozen())player.doTick();
  else {clientIsFloating=false;clientVehicleIsFloating=false;}
 }
}
