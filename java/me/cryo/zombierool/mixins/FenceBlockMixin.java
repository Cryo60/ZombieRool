package me.cryo.zombierool.mixins;

import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FenceBlock.class)
public class FenceBlockMixin {

    @Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
    private void zombierool_connectsTo(BlockState state, boolean isSideSolid, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        // 1. Force la connexion entre TOUTES les barrières (Même si elles n'ont pas de Tag)
        if (state.getBlock() instanceof FenceBlock) {
            cir.setReturnValue(true);
        }
        // 2. Force la connexion aux éléments de défense ZombieRool
        else if (state.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock ||
                 state.getBlock() instanceof DefenseDoorSystem.DefenseDoorOpenedBlock ||
                 state.getBlock() instanceof ObstacleDoorBlock ||
                 state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock ||
                 state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallDummyBlock) {
            cir.setReturnValue(true);
        }
    }
}