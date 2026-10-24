package local.skingalleryclient;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
public final class FullAiMovementTest {
 private static int checks;
 private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;System.out.println("FULL AI CHECK: "+text);}
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var old=p.position();long originalTime=l.getGameTime();boolean previous=EnemyWaterMovement.enabled(l);var effects=p.getActiveEffects().stream().map(net.minecraft.world.effect.MobEffectInstance::new).toList();
  try{p.removeAllEffects();p.setGameMode(GameType.SURVIVAL);me.cryo.zombierool.player.PlayerDownManager.resetAll();
   for(boolean swimming:new boolean[]{false,true})for(boolean water:new boolean[]{false,true}){
    EnemyWaterMovement.configure(l,swimming);int sx=water?640:600;
    for(int x=sx;x<=sx+22;x++)for(int z=-2;z<=2;z++){
     l.setBlock(new BlockPos(x,63,z),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),2);l.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);for(int y=65;y<=74;y++)l.setBlock(new BlockPos(x,y,z),water?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
    }
    p.teleportTo(l,sx+18.5,swimming&&water?70:65,.5,0,0);p.baseTick();p.setOnGround(!water);p.setHealth(p.getMaxHealth());
    var m=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);m.setPos(sx+2.5,65,.5);l.addFreshEntity(m);m.setOnGround(true);
    try{for(int i=0;i<400;i++){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+1);m.tick();if(i%40==0)System.out.println("FULL AI DEBUG swim="+swimming+" water="+water+" t="+i+" pos="+m.position()+" target="+(m.getTarget()==p)+" noai="+m.isNoAi()+" emerge="+m.getEmergenceTicks()+" speed="+m.getSpeed()+" path="+m.getNavigation().getPath());if(m.distanceToSqr(p)<4)break;}
     check(m.getX()>sx+8,"Full zombie AI walks toward player, swimming="+swimming+", water="+water);
     check(m.getTarget()==p,"Full AI retains player target, swimming="+swimming+", water="+water);
     if(swimming&&water)check(m.getY()>68,"Full AI swims upward toward elevated player");
    }finally{m.discard();}
   }
  }finally{l.getServer().getWorldData().overworldData().setGameTime(originalTime);EnemyWaterMovement.configure(l,previous);p.removeAllEffects();for(var effect:effects)p.addEffect(effect);p.setGameMode(GameType.CREATIVE);p.teleportTo(l,old.x,old.y,old.z,0,0);}
  System.out.println("FULL AI MOVEMENT PASS: "+checks+" checks");return checks;
 }
}
