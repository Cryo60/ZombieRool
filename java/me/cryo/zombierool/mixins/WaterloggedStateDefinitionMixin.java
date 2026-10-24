package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.state.properties.*;
@Mixin(net.minecraft.world.level.block.state.StateDefinition.Builder.class)
public abstract class WaterloggedStateDefinitionMixin {
 @Shadow(aliases={"f_61095_"},remap=false) @Final private Object owner;
 @Shadow(aliases={"f_61096_"},remap=false) @Final private java.util.Map<String,Property<?>> properties;
 @Inject(method={"create","m_61101_"},at=@At("HEAD"),remap=false)
 private void zr$waterProperty(CallbackInfoReturnable<?> ci){
  if(owner instanceof me.cryo.zombierool.block.WaterloggedElement&&!properties.containsKey("waterlogged"))
   ((net.minecraft.world.level.block.state.StateDefinition.Builder<?,?>)(Object)this).add(BlockStateProperties.WATERLOGGED);
 }
}
