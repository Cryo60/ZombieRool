package local.skingalleryclient;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Mob;
import java.util.*;

public final class TwoWayTransferTest {
 private static int checks;
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("TWO WAY CHECK: "+text);}
 private static Object get(String name)throws Exception{var f=WaveManager.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void set(String name,Object value)throws Exception{var f=WaveManager.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 private static List<Mob> enemies(ServerLevel l,BlockPos origin){var list=new ArrayList<Mob>();for(var e:l.getAllEntities())if(e instanceof Mob m&&m.isAlive()&&m.getPersistentData().contains("zr_spawn_origin")&&m.getPersistentData().getLong("zr_spawn_origin")==origin.asLong())list.add(m);return list;}
 private static void clock(ServerLevel l,int ticks){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+ticks);}
 private static void transfer(ServerLevel l,ServerPlayer p,TeleporterSystem.Pad source)throws Exception{String error=TeleporterTravel.depart(p,source);check(error.isEmpty(),"Departure accepted from "+source.config.getString("id")+": "+error);clock(l,20);TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,l.getServer()));}
 @SuppressWarnings({"unchecked","rawtypes"}) public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var pos=p.position();long time=l.getGameTime();var registry=SpawnerRegistry.getSpawners(l);var active=(Set<UUID>)get("activeMobs");var previousActive=new HashSet<>(active);var unlocked=new HashSet<>(WaveManager.UNLOCKED_CHANNELS);var locked=new HashSet<>(WaveManager.LOCKED_CHANNELS);Map<String,Object> old=new HashMap<>();for(String field:new String[]{"gameRunning","isSpecialWave","currentWave","zombiesToKill","currentState"})old.put(field,get(field));
  var a=new BlockPos(410,65,0);var b=new BlockPos(440,65,0);var sa=a.south(5);var sb=b.south(5);TeleporterSystem.Pad pa=null,pb=null;
  try{
   SpawnerRegistry.clearRegistry(l);SpawnChannelActions.reset();active.clear();WaveManager.UNLOCKED_CHANNELS.clear();WaveManager.LOCKED_CHANNELS.clear();WaveManager.UNLOCKED_CHANNELS.add(0);
   set("gameRunning",true);set("isSpecialWave",false);set("currentWave",1);set("zombiesToKill",4);var f=WaveManager.class.getDeclaredField("currentState");f.setAccessible(true);f.set(null,Enum.valueOf((Class)f.getType(),"WAITING_CLEAR"));
   for(int x=406;x<=446;x++)for(int z=-3;z<=9;z++){l.setBlock(new BlockPos(x,63,z),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),2);l.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);for(int y=65;y<70;y++)l.setBlock(new BlockPos(x,y,z),x>=436?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);}
   var ef=ServerLevel.class.getDeclaredField("entityManager");ef.setAccessible(true);var entityManager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)ef.get(l);
   for(int cx=25;cx<=27;cx++)for(int cz=-1;cz<=0;cz++)entityManager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cx,cz),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
   for(var origin:List.of(sa,sb))for(var m:enemies(l,origin))m.discard();
   for(var at:List.of(sa,sb)){l.setBlock(at,UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState(),2);var be=(UniversalSpawnerSystem.UniversalSpawnerBlockEntity)l.getBlockEntity(at);be.setConfig(UniversalSpawnerSystem.SpawnerMobType.ZOMBIE,"",at.equals(sa)?"0":"1","0",false,1);be.onLoad();}
   l.setBlock(a,TeleporterSystem.PLATFORM.get().defaultBlockState(),2);l.setBlock(b,TeleporterSystem.PLATFORM.get().defaultBlockState(),2);pa=(TeleporterSystem.Pad)l.getBlockEntity(a);pb=(TeleporterSystem.Pad)l.getBlockEntity(b);
   for(var pad:List.of(pa,pb)){var n=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);boolean moon=pad==pa;n.putString("id",moon?"test-lune":"test-aquatique");n.putString("destination",moon?"test-aquatique":"test-lune");n.putString("channel","two-way-regression");n.putBoolean("requiresPower",false);n.putInt("chargeTime",1);n.putInt("cooldown",0);n.putString("enableChannels",moon?"1":"0");n.putString("disableChannels",moon?"0":"1");n.putBoolean("redistributeMobs",true);pad.configure(n);pad.onLoad();check(pad.config.getString("enableChannels").equals(moon?"1":"0"),"Each source keeps its own channel config after sync and onLoad");}
   for(int i=0;i<4;i++){var m=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);m.setPos(410+i,65,6);check(l.addFreshEntity(m),"Original zombie joins the source chunk");m.setEmergenceTicks(0);m.setNoAi(true);SpawnRedistribution.track(m,l,sa);WaveManager.attachAfterRedistribution(m);}
   check(enemies(l,sa).size()==4,"All four source zombies are visible before the transfer");
   p.setGameMode(GameType.SURVIVAL);p.teleportTo(l,410.5,65.126,.5,0,0);LunarEquipment.revoke(p);
   for(int cycle=0;cycle<2;cycle++){
    transfer(l,p,pa);check(p.getX()>439&&WaveManager.LOCKED_CHANNELS.contains(0)&&WaveManager.UNLOCKED_CHANNELS.contains(1),"Arrival at aquatic source does not undo Moon departure channels");
    for(int i=0;i<4;i++){clock(l,10);SpawnRedistribution.tick(l);}check(enemies(l,sb).size()==4&&!SpawnRedistribution.pending(l),"All four enemies redistributed into water even while earlier arrivals stand still");check(WaveManager.remainingEnemies()==4&&active.size()==4,"Redistribution preserves all four enemies in wave accounting");
    clock(l,10); // Wait until the 48-tick arrival tunnel lock has ended.
    transfer(l,p,pb);check(p.getX()<412&&WaveManager.UNLOCKED_CHANNELS.contains(0)&&WaveManager.LOCKED_CHANNELS.contains(1),"Return activates Moon channel and leaves aquatic channel disabled");
    for(int i=0;i<4;i++){clock(l,10);SpawnRedistribution.tick(l);}check(enemies(l,sa).size()==4&&!SpawnRedistribution.pending(l),"All four enemies return to Moon spawners");
    clock(l,10);
   }
  }finally{
   TeleporterTravel.reset(l);for(var origin:List.of(sa,sb))for(var m:enemies(l,origin))m.discard();SpawnChannelActions.reset();active.clear();active.addAll(previousActive);for(var e:old.entrySet())set(e.getKey(),e.getValue());WaveManager.UNLOCKED_CHANNELS.clear();WaveManager.UNLOCKED_CHANNELS.addAll(unlocked);WaveManager.LOCKED_CHANNELS.clear();WaveManager.LOCKED_CHANNELS.addAll(locked);l.getServer().getWorldData().overworldData().setGameTime(time);p.setGameMode(GameType.CREATIVE);p.teleportTo(l,pos.x,pos.y,pos.z,0,0);
   for(var at:List.of(a,b,sa,sb))l.setBlock(at,Blocks.AIR.defaultBlockState(),2);SpawnerRegistry.clearRegistry(l);for(var be:registry)SpawnerRegistry.registerSpawner(l,be);
  }
  System.out.println("TWO WAY TRANSFER PASS: "+checks+" checks");return checks;
 }
}
