package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.TraversalMotion;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TraversalPlayerPacketMixin {
    @Shadow private boolean clientIsFloating;
    @Inject(method="tick",at=@At("HEAD"),require=1)
    private void zr$authorizedFlight(CallbackInfo ci){
        if(TraversalMotion.active(((ServerGamePacketListenerImpl)(Object)this).player)||me.cryo.zombierool.gameplay.TeleporterPlayerLock.active(((ServerGamePacketListenerImpl)(Object)this).player))clientIsFloating=false;
    }
    // Run after vanilla's thread check: only the authoritative route may move the rider.
    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",shift=At.Shift.AFTER),cancellable=true,require=1)
    private void zr$routeMovement(ServerboundMovePlayerPacket packet,CallbackInfo ci){
        var player=((ServerGamePacketListenerImpl)(Object)this).player;
        if(me.cryo.zombierool.gameplay.TeleporterPlayerLock.active(player)){ci.cancel();return;}
        me.cryo.zombierool.gameplay.DebugPlayerControls.applyCollision(player);
        if(!TraversalMotion.active(player))return;
        float yaw=packet.getYRot(player.getYRot()),pitch=packet.getXRot(player.getXRot());
        if(Float.isFinite(yaw)&&Float.isFinite(pitch)){player.setYRot(Mth.wrapDegrees(yaw));player.setXRot(Mth.clamp(pitch,-90,90));}
        ci.cancel();
    }
}
