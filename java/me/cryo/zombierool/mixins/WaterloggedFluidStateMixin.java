package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
@Mixin(BlockBehaviour.class)
public abstract class WaterloggedFluidStateMixin {
 @Inject(method={"getFluidState","m_5888_"},at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$fluid(BlockState state,CallbackInfoReturnable<FluidState> ci){
  if((Object)this instanceof me.cryo.zombierool.block.WaterloggedElement&&state.getValue(BlockStateProperties.WATERLOGGED))ci.setReturnValue(Fluids.WATER.getSource(false));
 }
}
