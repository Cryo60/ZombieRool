package me.cryo.zombierool.mixins;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import me.cryo.zombierool.gameplay.MatchPause;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftServer.class)
public abstract class MatchPauseServerMixin {
 @Inject(method="tickServer",at=@At("HEAD"),cancellable=true)
 private void zr$pause(BooleanSupplier time,CallbackInfo ci){
  var server=(MinecraftServer)(Object)this;MatchPause.pulse(server);
  if(MatchPause.frozen()){server.getConnection().tick();server.getPlayerList().tick();ci.cancel();}
 }
}
