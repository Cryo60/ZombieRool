package local.hotfixtest;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.Minecraft;
public final class GunModeSwitchClientTest {
 private static int age;
 private static net.minecraft.world.item.ItemStack existing;
 private static int initialAmmo;
 private static long equippedAt;
 public static boolean tick() throws Exception {
  var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();age++;
  if(age==1)me.cryo.zombierool.configuration.ZRClientConfig.PREFER_ZR_WEAPONS.set(false);
  if(age==48||age==78){
   var data=IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
   data.isShootRecorded=false;data.clientStateLock=true;data.isBolting=true;data.chargeProgress=10;
   var screen=new me.cryo.zombierool.client.gui.ZRConfigScreen(null);mc.setScreen(screen);
   var field=screen.getClass().getDeclaredField("settings");field.setAccessible(true);
   for(Object setting:(java.util.List<?>)field.get(screen)){
    var key=setting.getClass().getDeclaredField("key");key.setAccessible(true);
    if(key.get(setting).equals("zombierool.config.setting.classic")){var draft=setting.getClass().getDeclaredField("draft");draft.setAccessible(true);draft.set(setting,age==48);}
   }
   java.util.concurrent.CompletableFuture.delayedExecutor(3,java.util.concurrent.TimeUnit.SECONDS).execute(()->mc.execute(()->{try{var save=screen.getClass().getDeclaredMethod("save");save.setAccessible(true);save.invoke(screen);var restored=IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();if(!restored.isShootRecorded||restored.isBolting||restored.chargeProgress!=0)throw new AssertionError("Mode change must clear stale TacZ actions");System.out.println("GUN MODE RESET PASS age="+age);}catch(Exception ex){throw new RuntimeException(ex);}}));
  }
  if(age==1||age==60||age==80||age==140)server.submit(()->{
   var p=server.getPlayerList().getPlayer(mc.player.getUUID());p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getAbilities().invulnerable=true;
   p.getPersistentData().putBoolean("zr_prefer_zr_weapons",age==60);
   if(age==1){
    var clocks=IGunOperator.fromLivingEntity(p).getDataHolder();long base=clocks.baseTimestamp;
    long fresh=System.currentTimeMillis()+10000;clocks.drawTimestamp=fresh;clocks.shootTimestamp=fresh-base;
    me.cryo.zombierool.integration.TacZIntegration.compensatePause(p,60000);
    if(clocks.drawTimestamp!=fresh||clocks.shootTimestamp!=fresh-base||clocks.baseTimestamp!=base)throw new AssertionError("A new draw/shot must not inherit the previous menu pause");
    clocks.initialData();clocks.drawTimestamp=-1;System.out.println("GUN MODE PAUSE CLOCK PASS");
    existing=WeaponFacade.createWeaponStack("ak47",false,p);initialAmmo=WeaponFacade.getAmmo(existing);}
   var stack=age==1||age==80?existing:WeaponFacade.createWeaponStack(age==140?"g36c":"ak47",false,p);
   p.getInventory().selected=0;p.getInventory().setItem(0,stack);
   if(age==60)((me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem)stack.getItem()).tryShoot(stack,p,0);
   p.inventoryMenu.broadcastChanges();equippedAt=System.currentTimeMillis();
  }).get();
  if(age==40||age==120||age==180){
   if(System.currentTimeMillis()-equippedAt<3000){age--;return false;}
   var client=IClientPlayerGunOperator.fromLocalPlayer(mc.player);var shared=IGunOperator.fromLivingEntity(mc.player);
   var result=client.shoot();System.out.println("GUN SWITCH SHOT age="+age+" result="+result+" draw="+shared.getSynDrawCoolDown()+" shoot="+shared.getSynShootCoolDown()+" lock="+client.getDataHolder().clientStateLock);
   if(result!=ShootResult.SUCCESS)throw new AssertionError("Gun switch blocked: "+result);
  }
  if(age==44||age==124)server.submit(()->{int expected=initialAmmo-(age==44?1:2);if(WeaponFacade.getAmmo(existing)!=expected)throw new AssertionError("Server must consume the actual TacZ shot: "+WeaponFacade.getAmmo(existing)+" expected "+expected);System.out.println("GUN MODE SERVER SHOT PASS age="+age);}).get();
  if(age==190)System.out.println("GUN MODE SWITCH PASS");return age>=190;
 }
}
