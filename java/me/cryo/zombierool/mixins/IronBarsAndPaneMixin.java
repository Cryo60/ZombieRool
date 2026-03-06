package me.cryo.zombierool.mixins;

import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.ObstacleDoorBlock;
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
if (state.getBlock() instanceof DefenseDoorSystem.BaseDefenseDoor ||
state.getBlock() instanceof ObstacleDoorBlock) {
cir.setReturnValue(true);
}
}
}