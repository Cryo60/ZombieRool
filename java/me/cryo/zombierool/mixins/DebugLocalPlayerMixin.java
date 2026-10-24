package me.cryo.zombierool.mixins;
import net.minecraft.client.player.LocalPlayer;
import me.cryo.zombierool.gameplay.DebugPlayerControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LocalPlayer.class)
public abstract class DebugLocalPlayerMixin {
    @Inject(method="moveTowardsClosestSpace",at=@At("HEAD"),cancellable=true)
    private void zr$noWallEjection(double x,double z,CallbackInfo ci){
        var player=(LocalPlayer)(Object)this;
        if(DebugPlayerControls.noclip(player)&&player.getAbilities().flying){DebugPlayerControls.applyCollision(player);ci.cancel();}
    }
}
