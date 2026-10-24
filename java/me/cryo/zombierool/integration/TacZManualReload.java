package me.cryo.zombierool.integration;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.world.entity.LivingEntity;
/** Shared timing for mapped manual-action reloads; ammunition is changed only by the server. */
public final class TacZManualReload {
 private TacZManualReload(){}
 public static final ThreadLocal<Boolean> CLIP_ANIMATION=ThreadLocal.withInitial(()->false);
 public static boolean magazineKar(net.minecraft.world.item.ItemStack stack){var d=me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);return d!=null&&!stack.getOrCreateTag().getBoolean("zombierool:unmapped")&&"kar98k".equals(me.cryo.zombierool.core.system.WeaponFacade.getCareerWeaponId(stack))&&"MAGAZINE".equalsIgnoreCase(d.ammo.reload_type);}
 public static double[] enlistedClip(net.minecraft.world.item.ItemStack stack){
  var gun=IGun.getIGunOrNull(stack);if(gun==null)return null;
  return switch(gun.getGunId(stack).toString()){case "ww:kar98k","ww:m1903"->new double[]{1.0833,1.625,.875};case "ww:m91"->new double[]{1.2917,.9583,1.2083};case "ww:p14"->new double[]{1.25,1.4167,1.375};default->null;};
 }
 public static boolean enlistedMagazine(net.minecraft.world.item.ItemStack stack){var d=me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);return enlistedClip(stack)!=null&&d!=null&&!stack.getOrCreateTag().getBoolean("zombierool:unmapped")&&"MAGAZINE".equalsIgnoreCase(d.ammo.reload_type);}
 public static long clipMillis(net.minecraft.world.item.ItemStack stack){var timing=enlistedClip(stack);if(timing!=null)return Math.round((timing[0]+timing[1]+timing[2])*1000);var gun=IGun.getIGunOrNull(stack);var d=me.cryo.zombierool.core.system.WeaponFacade.getDefinition(stack);if(gun!=null){var index=TimelessAPI.getCommonGunIndex(gun.getGunId(stack));if(index.isPresent()){var params=index.get().getGunData().getScriptParam();Object time=params==null?null:params.get("clip_load");if(time instanceof Number n&&n.doubleValue()>0)return Math.round(n.doubleValue()*1000);}}return d==null?1500:d.ammo.reload_time*50L;}

 public static boolean manual(net.minecraft.world.item.ItemStack stack){var gun=IGun.getIGunOrNull(stack);return gun!=null&&TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(i->i.getGunData().getBolt()==Bolt.MANUAL_ACTION).orElse(false);}
 public static boolean clipKar(net.minecraft.world.item.ItemStack stack){if(enlistedMagazine(stack))return true;if(!magazineKar(stack))return false;var gun=IGun.getIGunOrNull(stack);return gun!=null&&TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(i->{var p=i.getGunData().getScriptParam();return p!=null&&p.get("clip_load") instanceof Number;}).orElse(false);}
 public static long clipFeedMillis(net.minecraft.world.item.ItemStack stack){var timing=enlistedClip(stack);if(timing!=null)return Math.round((timing[0]+timing[1])*1000);var gun=IGun.getIGunOrNull(stack);if(gun!=null){var index=TimelessAPI.getCommonGunIndex(gun.getGunId(stack));if(index.isPresent()){var p=index.get().getGunData().getScriptParam();if(p!=null&&p.get("clip_load_feed") instanceof Number n)return Math.round(n.doubleValue()*1000);}}return clipMillis(stack);}
 public static void complete(LivingEntity shooter){
  var stack=shooter.getMainHandItem();var gun=IGun.getIGunOrNull(stack);if(gun==null)return;
  var index=TimelessAPI.getCommonGunIndex(gun.getGunId(stack));if(index.isEmpty()||index.get().getGunData().getBolt()!=Bolt.MANUAL_ACTION)return;
  int total=me.cryo.zombierool.core.system.WeaponFacade.getAmmo(stack),max=me.cryo.zombierool.core.system.WeaponFacade.getMaxAmmo(stack),reserve=me.cryo.zombierool.core.system.WeaponFacade.getReserve(stack);
  int added=Math.max(0,Math.min(max-total,reserve));
  if(added>0){me.cryo.zombierool.core.system.WeaponFacade.setReserve(stack,reserve-added);gun.setCurrentAmmoCount(stack,gun.getCurrentAmmoCount(stack)+added);}
  if(!gun.hasBulletInBarrel(stack)&&gun.getCurrentAmmoCount(stack)>0){gun.setCurrentAmmoCount(stack,gun.getCurrentAmmoCount(stack)-1);gun.setBulletInBarrel(stack,true);}
 }
}
