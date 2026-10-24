package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.DebugPlayerControls;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class DebugCollisionMixin {
    @Inject(method="move",at=@At("HEAD"))
    private void zr$debugCollision(MoverType type,Vec3 delta,CallbackInfo ci){
        if((Object)this instanceof Player player)DebugPlayerControls.applyCollision(player);
    }
}
