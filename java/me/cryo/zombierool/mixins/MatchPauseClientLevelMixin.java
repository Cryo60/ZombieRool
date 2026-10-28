package me.cryo.zombierool.mixins;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Freeze client prediction without pausing the networking loop needed for resume votes. */
@Mixin(ClientLevel.class)
public abstract class MatchPauseClientLevelMixin {
 @Inject(method={"tick","tickEntities"},at=@At("HEAD"),cancellable=true)
 private void zr$freezePrediction(CallbackInfo ci){if(me.cryo.zombierool.client.MatchPauseClient.paused())ci.cancel();}
}
