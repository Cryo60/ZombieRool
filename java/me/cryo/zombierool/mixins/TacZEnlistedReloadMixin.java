package me.cryo.zombierool.mixins;
import com.tacz.guns.api.client.animation.statemachine.*;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import me.cryo.zombierool.integration.TacZManualReload;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Enlisted supplies a complete three-part clip reload, but its Lua uses a different per-round sequence. */
@Mixin(value=LuaAnimationState.class,remap=false)
public class TacZEnlistedReloadMixin {
 @Unique private int zr$phase=-1;
 @Unique private me.cryo.zombierool.integration.TacZBoltReloadPlan zr$plan;
 @Unique private int zr$mainTrack;
 @Unique private String zr$retreat;
 @Unique private static java.lang.reflect.Field zr$stateField,zr$scriptField;
 @Unique private static java.lang.reflect.Method zr$get,zr$integer,zr$string;
 @Unique private boolean zr$reloadState(){
  try{
   if(zr$stateField==null){
    zr$stateField=LuaAnimationState.class.getDeclaredField("stateTable");zr$stateField.setAccessible(true);
    zr$scriptField=LuaAnimationState.class.getDeclaredField("scriptTable");zr$scriptField.setAccessible(true);
    var type=zr$stateField.getType();zr$get=type.getMethod("get",String.class);zr$integer=type.getMethod("toint");zr$string=type.getMethod("tojstring");
   }
   Object state=zr$stateField.get(this),script=zr$scriptField.get(this);
   if(!zr$get.invoke(zr$get.invoke(script,"main_track_states"),"reload").equals(state))return false;
   zr$mainTrack=(Integer)zr$integer.invoke(zr$get.invoke(script,"MAIN_TRACK"));
   zr$retreat=(String)zr$string.invoke(zr$get.invoke(state,"retreat"));return true;
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read Enlisted reload state",e);}
 }
 @Unique private int zr$track(AnimationStateContext c){return c.getTrack(0,zr$mainTrack);}
 @Inject(method="entryAction",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$enter(AnimationStateContext c,CallbackInfo ci){
  zr$phase=-1;var p=Minecraft.getInstance().player;
  if(p==null||!me.cryo.zombierool.integration.TacZBoltReloadPlan.supports(p.getMainHandItem())||!zr$reloadState())return;
  zr$plan=me.cryo.zombierool.integration.TacZBoltReloadPlan.create(p.getMainHandItem());if(zr$plan==null)return;
  for(var step:zr$plan.steps)if(!c.hasAnimationPrototype(step.animation()))return;
  zr$phase=0;c.runAnimation(zr$plan.steps.get(0).animation(),zr$track(c),false,ObjectAnimation.PlayType.PLAY_ONCE_HOLD.ordinal(),0);ci.cancel();
 }
 @Inject(method="update",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$update(AnimationStateContext c,CallbackInfo ci){
  if(zr$phase<0)return;ci.cancel();if(!c.isHolding(zr$track(c)))return;
  if(++zr$phase<zr$plan.steps.size())c.runAnimation(zr$plan.steps.get(zr$phase).animation(),zr$track(c),false,ObjectAnimation.PlayType.PLAY_ONCE_HOLD.ordinal(),0);
  else c.trigger(zr$retreat);
 }
 @Inject(method="exitAction",at=@At("TAIL"),remap=false)
 private void zr$exit(AnimationStateContext c,CallbackInfo ci){
  if(zr$phase>=0){
   if(zr$phase<zr$plan.steps.size()&&c.hasAnimationPrototype("reload_end"))c.runAnimation("reload_end",zr$track(c),false,ObjectAnimation.PlayType.PLAY_ONCE_STOP.ordinal(),0);
   else c.stopAnimation(zr$track(c));
   zr$phase=-1;zr$plan=null;
  }
 }
}
