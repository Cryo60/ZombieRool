package me.cryo.zombierool.mixins;

import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.ObstacleDoorBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
public class WallBlockMixin {
@Inject(method = "connectsTo", at = @At("HEAD"), cancellable = true)
private void zombierool_connectsTo(BlockState state, boolean isSideSolid, Direction direction, CallbackInfoReturnable<Boolean> cir) {
if (state.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock ||
state.getBlock() instanceof DefenseDoorSystem.DefenseDoorOpenedBlock ||
state.getBlock() instanceof ObstacleDoorBlock) {
cir.setReturnValue(true);
}
}
}