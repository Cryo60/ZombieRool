package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.TeleporterPlayerLock;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TeleporterActionPacketMixin {
    @Inject(method={"handleUseItem","handleUseItemOn","handleInteract","handlePlayerAction","handleSetCarriedItem","handlePlayerCommand","handleContainerClick","handleContainerButtonClick","handleSetCreativeModeSlot","handlePickItem","handleAnimate","handlePlaceRecipe"},at=@At("HEAD"),cancellable=true,require=1)
    private void zr$blockTeleportActions(CallbackInfo ci){
        var player=((ServerGamePacketListenerImpl)(Object)this).player;
        // Off-thread vanilla handlers schedule themselves; reject only their server-thread execution.
        if(player.getServer().isSameThread()&&(TeleporterPlayerLock.active(player)||me.cryo.zombierool.gameplay.LunarEquipment.busy(player))){// The client already predicts hotbar changes before this packet arrives.
            // Inventory contents alone do not restore its selected slot.
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(player.getInventory().selected));
            player.containerMenu.sendAllDataToRemote();ci.cancel();}
    }
}
