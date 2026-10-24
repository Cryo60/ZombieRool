package local.deliverytests;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
@Mod("deliverytests")
public final class MachineDeliveryTest {
 int checks;
 public MachineDeliveryTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("DELIVERY CHECK: "+text);}
 MachineDeliverySystem.Delivery site(ServerLevel l,int x,boolean roll){var p=new BlockPos(x,80,0);l.setBlock(p,MachineDeliverySystem.BLOCK.get().defaultBlockState(),3);var d=MachineDeliverySystem.at(l,p);var n=d.config();if(roll){n.putString("role","controller");n.putString("mode","roll");n.putString("trigger","door_channel");}d.configure(n);return d;}
 void pool(CompoundTag n,String... ids){var p=new ListTag();for(String id:ids)p.add(StringTag.valueOf(id));n.put("pool",p);}
 MachineDeliverySystem.Delivery active(ServerLevel l){return MachineDeliverySystem.all(l).stream().filter(d->d.phase.equals("animating")).findFirst().orElseThrow();}
 MachineDeliverySystem.Delivery finish(MachineDeliverySystem.Delivery d){var l=(ServerLevel)d.getLevel();d.started=l.getGameTime()-d.animationDuration;d.tick();var result=MachineDeliverySystem.at(l,d.getBlockPos());check(result!=null&&result.phase.equals("landed"),"Landed state survives replacement of the carpet by the machine");return result;}
 void lua(String code,String text){String result=LuaScriptManager.executeString(code);check(result.contains("true")&&!result.contains("Error"),text+" -> "+result);}
 void start(ServerStartedEvent event){var server=event.getServer();var l=server.overworld();try{
  MachineDeliverySystem.reset(l);for(var p:BlockPos.betweenClosed(-5,78,-5,50,86,5))l.setBlock(p,p.getY()==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
  check(me.cryo.zombierool.init.CreativeCatalog.category(new net.minecraft.world.item.ItemStack(MachineDeliverySystem.ITEM.get()))==me.cryo.zombierool.init.CreativeCatalog.Category.MACHINES,"Landing Site belongs to Machines and traps");
  var a=site(l,6,false);var b=site(l,12,false);var c=site(l,18,false);var roll=site(l,24,true);var sky=MachineDeliverySystem.sky(l);check(sky.config().getInt("duration")==200,"New sky drops and the old default use a ten-second fall");var clean=MachineDeliverySystem.defaults();clean.putBoolean("enabled",false);sky.configure(clean);
  check(sky.virtualSky&&sky.getBlockPos().equals(MachineDeliverySystem.SKY_POS),"Sky Delivery is stored in map data, without a controller block");
  check(!sky.config().getBoolean("enabled")&&a.config().getString("role").equals("landing"),"A new carpet is only a landing site and automatic sky drops start disabled");
  check(a.getBlockState().getCollisionShape(l,a.getBlockPos()).isEmpty()&&a.getBlockState().getShape(l,a.getBlockPos()).max(Direction.Axis.Y)<=.0625,"Carpet is thin and has no collision");
  check(a.baseOffset()==0&&a.getBlockState().getBlock().isPathfindable(a.getBlockState(),l,a.getBlockPos(),net.minecraft.world.level.pathfinder.PathComputationType.LAND),"Entities can navigate over the carpet");
  var n=sky.config();n.putBoolean("enabled",true);n.putInt("firstRound",3);n.putInt("everyRounds",2);pool(n,"juggernog","speed_cola","pack_a_punch");sky.configure(n);
  for(var d:List.of(a,b,c))l.setBlock(d.getBlockPos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
  var used=new HashSet<String>();var points=new HashSet<BlockPos>();
  MachineDeliverySystem.onWaveStart(l,1);check(sky.queued==0,"First round setting is respected");
  for(int round:new int[]{3,5,7}){
   MachineDeliverySystem.onWaveStart(l,round);var flight=active(l);check(used.add(flight.selected)&&points.add(flight.getBlockPos()),"No repeated machine or destination on round "+round);
   check(flight.falling&&!flight.controller()&&flight.displayed(0).equals(flight.selected),"Sky drop picks its machine before flight without showing a roulette");
   MachineDeliverySystem.onWaveStart(l,round);check(sky.queued==0,"Duplicate notification of the same round does not duplicate a delivery");
   var landed=finish(flight);var main=landed.getBlockPos().above(landed.selected.equals(MachineDeliverySystem.PACK)?0:1);var be=l.getBlockEntity(main);
   check(l.getBlockState(landed.getBlockPos().below()).is(Blocks.REDSTONE_BLOCK),"Delivery leaves the power block under the carpet untouched");
   check(be!=null&&be.getPersistentData().hasUUID("zr_delivery"),"Actual landed machine retains its ownership token");
   if(be instanceof PerksSystem.PerksAColaBlockEntity perk)check(MapPower.syncPerk(l,main)&&perk.getSavedPerkId().equals(landed.selected),"Delivered perk receives power from the block below its footprint");
   if(be instanceof PackAPunchSystem.PackAPunchBlockEntity pack)check(MapPower.receives(l,main)&&pack.getPrice()==5000,"Delivered Pack-a-Punch receives power from underneath");
  }
  check(used.contains(MachineDeliverySystem.PACK)&&sky.deliveries==3,"Shared sky pool includes Pack-a-Punch without a group setting");
  check(sky.trigger("",null).equals("empty_pool"),"An exhausted pool cannot overwrite occupied landing sites");
  var saved=MachineDeliverySystem.Index.get(l).save(new CompoundTag());check(saved.getList("sites",10).size()==4&&saved.getCompound("sky").getInt("Deliveries")==3,"Map data saves the sites and sky schedule after carpets are replaced");
  MachineDeliverySystem.reset(l);
  for(var pos:points){var d=MachineDeliverySystem.at(l,pos);check(d!=null&&d.phase.equals("idle")&&l.getBlockState(pos).is(MachineDeliverySystem.BLOCK.get()),"Reset restores the physical landing carpet and its saved configuration");check(l.getBlockState(pos.below()).is(Blocks.REDSTONE_BLOCK),"Reset also preserves the independent power block");}
  roll=MachineDeliverySystem.at(l,new BlockPos(24,80,0));n=roll.config();n.putInt("doorChannel",7);pool(n,"juggernog");roll.configure(n);
  var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,true);((net.minecraft.world.level.storage.ServerLevelData)l.getLevelData()).setGameTime(1000);
  MachineDeliverySystem.onDoorOpened(l,8);check(roll.phase.equals("idle"),"An unrelated door channel does not start this roulette");
  MachineDeliverySystem.onDoorOpened(l,7);check(roll.phase.equals("animating")&&!roll.falling,"Door and carpet are linked by their channel number, without coordinates");
  MachineDeliverySystem.onDoorOpened(l,7);check(roll.phase.equals("animating"),"Repeated opening of a door channel does not reroll a reserved machine");
  l.setBlock(roll.getBlockPos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);var landed=finish(roll);var main=landed.getBlockPos().above();check(((PerksSystem.PerksAColaBlockEntity)l.getBlockEntity(main)).isPowered(),"Roulette landing also leaves its power source below the machine");
  l.setBlock(landed.getBlockPos().below(),Blocks.STONE.defaultBlockState(),3);check(!MapPower.syncPerk(l,main),"The landing site does not supply power by itself");
  var activator=me.cryo.zombierool.init.ZombieroolModBlocks.ACTIVATOR.get();l.setBlock(landed.getBlockPos().below(),activator.defaultBlockState(),3);me.cryo.zombierool.config.GlobalSwitchState.setActivated(l,true);check(MapPower.syncPerk(l,main),"An activator directly below the carpet powers the delivered perk");me.cryo.zombierool.config.GlobalSwitchState.setActivated(l,false);check(!MapPower.syncPerk(l,main),"Disabling the map's current turns the delivered perk off");l.setBlock(landed.getBlockPos().below(),Blocks.STONE.defaultBlockState(),3);
  sky.resetGroup();check(MachineDeliverySystem.at(l,landed.getBlockPos()).phase.equals("landed"),"Resetting sky deliveries leaves door roulettes alone");landed.reset();
  roll=MachineDeliverySystem.at(l,new BlockPos(24,80,0));n=roll.config();n.putString("trigger","manual");n.putBoolean("noRepeats",false);roll.configure(n);check(roll.trigger("juggernog",null).isEmpty(),"Restored roulette can start again after an explicit reset");
  l.setBlock(roll.getBlockPos().above(),Blocks.GOLD_BLOCK.defaultBlockState(),3);roll.started=l.getGameTime()-roll.animationDuration;roll.tick();check(roll.error.equals("blocked_landing")&&l.getBlockState(roll.getBlockPos().above()).is(Blocks.GOLD_BLOCK),"An obstruction appearing during flight is preserved");l.removeBlock(roll.getBlockPos().above(),false);roll.reset();
  n=sky.config();n.putBoolean("noRepeats",false);pool(n,"juggernog");sky.configure(n);sky.round(3);sky.round(5);check(sky.queued==1,"A delivery round during an existing flight is queued");finish(active(l));sky.tick();check(sky.queued==0&&active(l).falling,"Map-wide controller drains the queued delivery after landing");sky.cancel();sky.resetGroup();running.setBoolean(null,false);
  LuaScriptManager.loadScripts(l);
  lua("local z=ZombieroolAPI; return z:configureSkyDelivery({enabled=true,everyRounds=4,firstRound=2,pool={'speed_cola'}}) and z:getSkyDelivery().everyRounds==4", "Lua exposes map-wide sky settings without a controller position");
  lua("return ZombieroolAPI:triggerSkyDelivery()==''", "Lua can trigger the shared sky pool");finish(active(l));
  lua("ZombieroolAPI:resetSkyDelivery(); return ZombieroolAPI:getSkyDelivery().deliveries==0", "Lua resets sky sites and the automatic schedule");
  lua("local z=ZombieroolAPI; return z:configureMachineDelivery(24,80,0,{trigger='manual',pool={'quick_revive'}}) and z:triggerMachineDelivery(24,80,0)==''", "Lua still configures and triggers individual roulettes");finish(active(l));
  MachineDeliverySystem.reset(l);check(MachineDeliverySystem.all(l).stream().allMatch(d->d.phase.equals("idle")),"Match reset restores carpets, schedules and reservations");
  var guardSite=MachineDeliverySystem.at(l,new BlockPos(24,80,0));n=guardSite.config();n.putBoolean("noRepeats",false);n.putString("trigger","manual");pool(n,"juggernog");guardSite.configure(n);
  var camper=new net.minecraftforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"LandingCamper"));camper.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);l.addNewPlayer(camper);camper.setPos(24.5,80,.5);
  check(guardSite.trigger("juggernog",null).isEmpty(),"Roulette can start with a camper on its marker");
  guardSite.tick();check(!camper.getBoundingBox().intersects(guardSite.landingVolume())&&camper.getY()<=80,"A camper is moved sideways during the animation, never onto the machine");
  for(int jump=0;jump<3;jump++){camper.setPos(24.5,80+jump*.1,.5);guardSite.tick();check(!camper.getBoundingBox().intersects(guardSite.landingVolume())&&camper.getY()<=80,"Jump attempt "+jump+" cannot get inside the reserved machine volume");}
  camper.setPos(24.5,80,.5);guardSite.started=l.getGameTime()-guardSite.animationDuration;guardSite.tick();check(!camper.getBoundingBox().intersects(guardSite.landingVolume())&&camper.getY()<=80,"Final landing checks the player again before materializing a solid machine");MachineDeliverySystem.at(l,guardSite.getBlockPos()).reset();
  guardSite=MachineDeliverySystem.at(l,new BlockPos(24,80,0));camper.setPos(24.5,80,.5);
  for(var q:BlockPos.betweenClosed(20,80,-4,28,82,4))if(q.getX()!=24||q.getZ()!=0)l.setBlock(q,Blocks.STONE.defaultBlockState(),3);
  check(guardSite.trigger("juggernog",null).isEmpty(),"Crowded marker begins its animation without replacing its surrounding walls");guardSite.started=l.getGameTime()-guardSite.animationDuration;guardSite.tick();check(guardSite.phase.equals("animating")&&l.getBlockState(guardSite.getBlockPos()).is(MachineDeliverySystem.BLOCK.get()),"If no safe horizontal exit exists, landing waits instead of trapping or raising the player");
  l.removeBlock(new BlockPos(25,80,0),false);l.removeBlock(new BlockPos(25,81,0),false);l.removeBlock(new BlockPos(25,82,0),false);guardSite.tick();check(MachineDeliverySystem.at(l,guardSite.getBlockPos()).phase.equals("landed")&&camper.getY()<=80,"Landing resumes when a safe side exit becomes available");camper.discard();MachineDeliverySystem.reset(l);
  check(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(new net.minecraft.resources.ResourceLocation("zombierool:delivery_alarm"))&&net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(new net.minecraft.resources.ResourceLocation("zombierool:delivery_impact")),"Original delivery alarm and impact sound events are registered");
  // Exercise the real end-of-match journal, rather than only the delivery reset helper.
  var endSite=site(l,40,false);n=sky.config();n.putBoolean("enabled",true);n.putBoolean("noRepeats",false);pool(n,"juggernog");sky.configure(n);
  MatchWorldJournal.restore(l);running.setBoolean(null,true);
  check(sky.trigger("juggernog",endSite.getBlockPos()).isEmpty(),"Sky delivery starts during an active match");finish(active(l));
  WaveManager.endGame(l,null);
  var restored=MachineDeliverySystem.at(l,endSite.getBlockPos());check(restored!=null&&restored.phase.equals("idle")&&l.getBlockState(endSite.getBlockPos()).is(MachineDeliverySystem.BLOCK.get()),"Actual match end restores an idle Landing Site after the world journal");
  restored.started=l.getGameTime()-1200;restored.tick();check(l.getBlockState(endSite.getBlockPos()).is(MachineDeliverySystem.BLOCK.get())&&l.getBlockState(endSite.getBlockPos().above()).isAir(),"Restored site cannot replay its completed delivery after match end");
  System.out.println("DELIVERY RUNTIME PASS: "+checks+" checks");
 }catch(Throwable t){t.printStackTrace();System.out.println("DELIVERY RUNTIME FAILED");}finally{try{var f=WaveManager.class.getDeclaredField("gameRunning");f.setAccessible(true);f.setBoolean(null,false);}catch(Exception ignored){}server.halt(false);}}
}
