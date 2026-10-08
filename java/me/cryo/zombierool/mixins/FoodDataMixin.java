package me.cryo.zombierool.mixins;

import me.cryo.zombierool.gameplay.ZombiePlayerHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The mod's delayed regeneration owns healing while its hunger system is disabled. */
@Mixin(FoodData.class)
public class FoodDataMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"),require=2)
    private void zombierool$preventParallelRegeneration(Player player,float amount) {
        if(!ZombiePlayerHandler.DISABLE_HUNGER) player.heal(amount);
    }
}
