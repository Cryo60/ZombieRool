package me.cryo.zombierool.mixins;
import me.cryo.zombierool.gameplay.TraversalMotion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class TraversalMovementMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void zombierool_curveOnly(MoverType type,Vec3 delta,CallbackInfo ci){if(TraversalMotion.active((Entity)(Object)this)||me.cryo.zombierool.gameplay.TeleporterPlayerLock.active((Entity)(Object)this))ci.cancel();}
    @Inject(method="push(DDD)V",at=@At("HEAD"),cancellable=true)
    private void zombierool_noPush(double x,double y,double z,CallbackInfo ci){if(TraversalMotion.active((Entity)(Object)this)||me.cryo.zombierool.gameplay.TeleporterPlayerLock.active((Entity)(Object)this))ci.cancel();}
}
