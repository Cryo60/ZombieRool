package local.skingalleryclient;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import java.util.UUID;

public final class LunarLandingRegressionTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("LUNAR LANDING CHECK: "+label);}
 private static FakePlayer player(ServerLevel level,String name){var p=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),name)){@Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;} @Override public void die(net.minecraft.world.damagesource.DamageSource source){setHealth(0);}};try{var f=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");f.setAccessible(true);f.setInt(p,0);}catch(Exception e){throw new RuntimeException(e);}p.setGameMode(GameType.SURVIVAL);p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(6);p.setHealth(p.getMaxHealth());return p;}
 public static int run(ServerLevel level){
  for(boolean helmet:new boolean[]{false,true}){
   var p=player(level,"LunarFall");p.setPos(70.5,80,.5);p.setOnGround(false);var n=LunarEquipment.data(p);n.putBoolean("owned",helmet);n.putBoolean("equipped",helmet);n.putDouble("gravity",.167);n.putBoolean("lunarFall",true);p.fallDistance=12;
   p.setPos(30.5,80,.5);LunarEquipment.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
   check(n.getDouble("gravity")==1&&!n.getBoolean("lunarFall"),"Leaving zone removes lunar fall immunity, helmet="+helmet);
   check(p.fallDistance==0,"Earth fall starts at zone exit, helmet="+helmet);
   p.setPos(30.5,65,.5);p.setOnGround(true);p.invulnerableTime=0;p.causeFallDamage(16,1,p.damageSources().fall());
   check(!p.isAlive(),"Long landing outside zone is lethal, helmet="+helmet);
   var inside=player(level,"LunarInside");inside.setPos(70.5,65,.5);inside.setOnGround(true);LunarEquipment.data(inside).putDouble("gravity",1);float before=inside.getHealth();inside.causeFallDamage(16,1,inside.damageSources().fall());
   check(inside.getHealth()==before,"Landing inside lunar zone still protected even before state tick");
  }
  var stale=player(level,"LunarStale");stale.setPos(30.5,65,.5);var state=LunarEquipment.data(stale);state.putDouble("gravity",.167);state.putBoolean("lunarFall",true);state.putBoolean("owned",true);state.putBoolean("equipped",true);stale.invulnerableTime=0;stale.causeFallDamage(16,1,stale.damageSources().fall());check(!stale.isAlive(),"Stale lunar flags cannot suppress damage at Earth landing before next player tick");
  var shortFall=player(level,"ShortFall");shortFall.setPos(30.5,65,.5);float health=shortFall.getHealth();shortFall.causeFallDamage(2,1,shortFall.damageSources().fall());check(shortFall.getHealth()==health,"Short Earth falls remain harmless");
  try{
   var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);boolean beforeRunning=running.getBoolean(null);running.setBoolean(null,true);
   try{for(boolean helmet:new boolean[]{false,true}){
    var drop=player(level,"HundredBlockFall");drop.setPos(30.5,65,.5);LunarEquipment.data(drop).putBoolean("equipped",helmet);
    var event=new net.minecraftforge.event.entity.living.LivingDamageEvent(drop,drop.damageSources().fall(),97);FixedPlayerDamage.damage(event);
    check(event.getAmount()==97,"Active match preserves 100-block fall damage instead of clamping it to one, helmet="+helmet);
    drop.causeFallDamage(100,1,drop.damageSources().fall());check(!drop.isAlive()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(drop.getUUID()),"100-block dry Earth landing is lethal during a match, helmet="+helmet);
   }}finally{running.setBoolean(null,beforeRunning);}
  }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
  System.out.println("LUNAR LANDING REGRESSION PASS: "+checks+" checks");return checks;
 }
}
