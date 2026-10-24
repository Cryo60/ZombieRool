package me.cryo.zombierool.client;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import me.cryo.zombierool.integration.TacZManualReload;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Queue a manual reload until the shot and bolt have finished, before playing any animation. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class TacZPendingReload {
 private static String pending="";private static int slot,expires;
 public static boolean ready(net.minecraft.client.player.LocalPlayer p){var op=IGunOperator.fromLivingEntity(p);return !op.getSynIsBolting()&&op.getSynShootCoolDown()<=0&&op.getSynDrawCoolDown()<=0&&IClientPlayerGunOperator.fromLocalPlayer(p).getClientShootCoolDown()<=0;}
 public static boolean defer(){
  var p=Minecraft.getInstance().player;if(p==null||!TacZManualReload.manual(p.getMainHandItem()))return false;
  if(ready(p)){pending="";return false;}
  var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(p.getMainHandItem());pending=gun.getGunId(p.getMainHandItem()).toString();slot=p.getInventory().selected;expires=p.tickCount+100;return true;
 }
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END||pending.isEmpty())return;var mc=Minecraft.getInstance();var p=mc.player;
  if(p==null||mc.level==null||mc.screen!=null||!p.isAlive()||p.getInventory().selected!=slot||p.tickCount>expires){pending="";return;}
  var stack=p.getMainHandItem();var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
  if(gun==null||!pending.equals(gun.getGunId(stack).toString())||WeaponFacade.getReserve(stack)<=0||WeaponFacade.getAmmo(stack)>=WeaponFacade.getMaxAmmo(stack)||IGunOperator.fromLivingEntity(p).getSynReloadState().getStateType().isReloading()){pending="";return;}
  if(!mc.isPaused()&&ready(p)){pending="";IClientPlayerGunOperator.fromLocalPlayer(p).reload();}
 }
}
