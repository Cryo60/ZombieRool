package me.cryo.zombierool.mixins;

import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronBarsBlock.class)
public class IronBarsAndPaneMixin {

    @Inject(method = "attachsTo", at = @At("HEAD"), cancellable = true)
    private void zombierool_attachsTo(BlockState state, boolean isSolidSide, CallbackInfoReturnable<Boolean> cir) {
        // 1. Force la connexion entre TOUTES les vitres et barreaux de fer
        if (state.getBlock() instanceof IronBarsBlock) {
            cir.setReturnValue(true);
        }
        // 2. Force la connexion aux éléments de défense ZombieRool
        else if (state.getBlock() instanceof DefenseDoorSystem.BaseDefenseDoor ||
                 state.getBlock() instanceof ObstacleDoorBlock ||
                 state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock ||
                 state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallDummyBlock) {
            cir.setReturnValue(true);
        }
    }
}