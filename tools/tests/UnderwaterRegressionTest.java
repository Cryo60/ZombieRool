package local.skingalleryclient;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

public final class UnderwaterRegressionTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("UNDERWATER CHECK: "+label);}
 private static void step(ServerPlayer p){p.tickCount++;p.invulnerableTime=0;p.baseTick();LunarEquipment.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
 public static void build(ServerLevel l){
  for(int x=160;x<=180;x++)for(int z=-6;z<=6;z++){
   l.setBlock(new BlockPos(x,63,z),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),2);
   for(int y=64;y<=73;y++){boolean edge=x==160||x==180||z==-6||z==6||y==64||y==73;l.setBlock(new BlockPos(x,y,z),edge?((y==64||y==73)&&x%4==2&&z%4==0?Blocks.SEA_LANTERN.defaultBlockState():Blocks.GLASS.defaultBlockState()):Blocks.WATER.defaultBlockState(),2);}
  }
  for(int x=174;x<=179;x++)for(int z=-3;z<=3;z++)for(int y=65;y<=71;y++){
   boolean edge=x==174||x==179||z==-3||z==3||y==71;l.setBlock(new BlockPos(x,y,z),edge?Blocks.GLASS.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  var door=Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH);l.setBlock(new BlockPos(176,65,-3),door,2);l.setBlock(new BlockPos(176,66,-3),door.setValue(DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
  for(int x=174;x<=178;x++)l.setBlock(new BlockPos(x,65,-4),Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),2);
  l.setBlock(new BlockPos(176,65,-2),Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),2);
  for(int x=159;x<=181;x++)for(int z=-10;z<=-7;z++)l.setBlock(new BlockPos(x,73,z),Blocks.STONE_BRICKS.defaultBlockState(),2);
  var station=me.cryo.zombierool.block.system.LunarStation.STATION.get().defaultBlockState();l.setBlock(new BlockPos(165,74,-8),station,2);l.setBlock(new BlockPos(165,75,-8),station.setValue(me.cryo.zombierool.block.system.LunarStation.StationBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
  l.setDefaultSpawnPos(new BlockPos(163,74,-9),0);
 }
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();build(l);new me.cryo.zombierool.scripting.ZombieroolAPI(l).configureLunarEnvironment(1,false,5);
  p.setGameMode(GameType.SURVIVAL);p.removeAllEffects();p.getInventory().clearContent();p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);p.teleportTo(l,165.5,65,.5,0,0);LunarEquipment.revoke(p);LunarEquipment.acquire(p);var n=LunarEquipment.data(p);
  n.putBoolean("equipped",true);n.putInt("air",100);for(int i=0;i<340;i++)step(p);
  check(p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER),"Fixture really submerges the player's eyes");check(n.getBoolean("underwater")&&n.getBoolean("needsOxygen"),"Underwater environment reaches shared oxygen state");check(p.getHealth()==p.getMaxHealth()&&n.getInt("air")==100,"Helmet prevents drowning past the full vanilla reserve");check(p.canBreatheUnderwater(),"Vanilla second drowning timer disabled");
  check(LunarEquipment.setEquipped(p,false),"Helmet can be removed underwater");for(int i=0;i<3;i++)step(p);check(n.getInt("air")==100,"Removal remains sealed before unseal frame");step(p);check(n.getInt("air")==99,"Oxygen starts at the unseal frame");
  for(int i=0;i<98;i++)step(p);check(n.getInt("air")==1&&p.getHealth()==p.getMaxHealth(),"Exactly one reserve tick per underwater tick, no duplicate drowning damage");
  float health=p.getHealth();p.tickCount=1999;step(p);check(n.getInt("air")==0&&p.getHealth()==health-2,"Empty reserve causes the shared asphyxia damage");
  p.setHealth(p.getMaxHealth());p.invulnerableTime=0;n.putBoolean("changing",false);check(LunarEquipment.setEquipped(p,true),"Can put helmet back with empty oxygen underwater");for(int i=0;i<35;i++)step(p);check(p.getHealth()==p.getMaxHealth()&&LunarEquipment.equipped(p),"Equipping protects immediately under water, before seal");check(n.getInt("air")==100,"Protected helmet refills reserve");
  LunarEquipment.revoke(p);n.putInt("air",40);p.teleportTo(l,177,65,.5,0,0);step(p);check(!n.getBoolean("underwater")&&!n.getBoolean("needsOxygen")&&n.getInt("air")==44,"Physical dry air pocket restores oxygen without helmet");
  p.teleportTo(l,165.5,65,.5,0,0);step(p);check(n.getInt("air")==43,"Returning to water resumes the same reserve");
  LunarEnvironment.get(l).put("underwater-regression-vacuum",new AABB(184,64,-2,190,80,3),1,true);p.teleportTo(l,185.5,65,.5,0,0);step(p);check(!n.getBoolean("underwater")&&n.getBoolean("vacuum")&&n.getInt("air")==42,"Moving from water to vacuum does not reset oxygen");
  p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING,200));step(p);check(n.getInt("air")==41,"Water breathing potion does not protect against vacuum");
  p.teleportTo(l,165.5,65,.5,0,0);step(p);check(!n.getBoolean("needsOxygen")&&n.getInt("air")==45,"Water breathing potion protects underwater");p.removeEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONDUIT_POWER,200));step(p);check(!n.getBoolean("needsOxygen"),"Conduit protection still works underwater");p.removeEffect(net.minecraft.world.effect.MobEffects.CONDUIT_POWER);LunarEnvironment.get(l).remove("underwater-regression-vacuum");
  var zombie=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);zombie.setPos(169.5,65,.5);l.addFreshEntity(zombie);zombie.setEmergenceTicks(0);zombie.getPersistentData().remove("zr_emerge_no_ai");zombie.setNoAi(false);zombie.setOnGround(true);zombie.baseTick();var path=zombie.getNavigation().createPath(p,0);check(zombie.canBreatheUnderwater()&&RouteNavigation.reachesPlayer(path,p),"Underwater zombie can breathe and find a route on the pool floor");zombie.discard();
  var grenade=new me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity(me.cryo.zombierool.core.registry.ZRThrowableRegistry.GRENADE_ENTITY.get(),l);grenade.setPos(165.5,68,3.5);grenade.setDeltaMovement(.2,.1,0);for(int i=0;i<3;i++)grenade.tick();check(grenade.isInWater()&&grenade.getFuse()==97,"Real grenade moves in water and its fuse keeps running");grenade.discard();
  LunarEquipment.revoke(p);LunarEquipment.acquire(p);n.putBoolean("equipped",true);n.putInt("air",100);p.tickCount=4000;p.setHealth(p.getMaxHealth());LunarEquipment.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
  var gun=me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack("ak47",false,p);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);check(me.cryo.zombierool.core.system.WeaponFacade.isTaczWeapon(gun),"Real TACZ gun available in water");
  var fire=new com.tacz.guns.api.event.common.GunShootEvent(p,gun,net.minecraftforge.fml.LogicalSide.SERVER);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(fire);check(!fire.isCanceled(),"TACZ shooting allowed underwater when helmet is idle");check(EquipmentSystem.valid(p),"Grenades and equipment remain usable underwater");
  System.out.println("UNDERWATER REGRESSION PASS: "+checks+" checks");return checks;
 }
}
