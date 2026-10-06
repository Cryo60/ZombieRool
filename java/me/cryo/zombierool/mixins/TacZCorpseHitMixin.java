package me.cryo.zombierool.mixins;

import com.tacz.guns.util.EntityUtil;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityUtil.class, remap = false)
public class TacZCorpseHitMixin {

    @Redirect(
            method = {"findEntityOnPath", "findEntitiesOnPath"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isAlive()Z"),
            remap = true
    )
    private static boolean zombierool_hitLingeringCorpse(Entity entity) {
        if (entity instanceof AbstractZombieRoolEntity zombie && zombie.isLingeringCorpse()) {
            return true;
        }
        return entity.isAlive();
    }
}
