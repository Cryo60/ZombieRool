package local.hotfixtest;
import me.cryo.zombierool.config.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
public final class BossBehaviorTest {
 private static int count;
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;System.out.println("BOSS BEHAVIOR CHECK: "+message);}
 private static java.lang.reflect.Field waveField(String name)throws Exception{var f=WaveManager.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private static void rule(ServerLevel l,String trigger,String target,int wave,BlockPos pos){var n=new CompoundTag();n.putString("bossSwitchTrigger",trigger);n.putString("bossSwitchTarget",target);n.putInt("bossSwitchWave",wave);n.putInt("bossSwitchX",pos.getX());n.putInt("bossSwitchY",pos.getY());n.putInt("bossSwitchZ",pos.getZ());n.putString("bossSwitchSoulIds","");SpecialEnemyConfig.update(l,n);MiniBossTransitions.reset(l);}
 private static void unlockFully(ServerLevel level,ServerPlayer player,BlockPos pos){
  int duration=SpecialEnemyConfig.get(level).getInt("brutusUnlockTicks");
  for(int t=0;t<duration;t++){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+1);if(t%5==0)BrutusMachineLocks.holdUnlock(player,pos);BrutusMachineLocks.tickUnlock(player);}
 }
 public static void run(ServerLevel level,ServerPlayer player)throws Exception{
  var tickClock=net.minecraft.server.MinecraftServer.class.getDeclaredField("tickCount");tickClock.setAccessible(true);int startTick=tickClock.getInt(level.getServer());var old=SpecialEnemyConfig.get(level);var paused=waveField("isPausedByPlayer").get(null);var running=waveField("gameRunning").get(null);var wave=waveField("currentWave").get(null);var oldPos=player.position();var mode=player.gameMode.getGameModeForPlayer();boolean power=GlobalSwitchState.isActivated(level);long clock=level.getGameTime();int score=PointManager.getScore(player);var pos=new BlockPos(1540,80,0);var created=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
  try{
   WorldConfig.get(level).setSpecialEnemies(SpecialEnemyConfig.defaults());waveField("gameRunning").set(null,true);waveField("isPausedByPlayer").set(null,false);waveField("currentWave").set(null,8);player.setGameMode(GameType.ADVENTURE);player.setPos(1558,80,.5);
   var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)managerField.get(level);
   for(int cx=95;cx<=98;cx++)for(int cz=-1;cz<=0;cz++){level.getChunk(cx,cz);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cx,cz),FullChunkStatus.ENTITY_TICKING);}
   for(int x=1536;x<=1564;x++)for(int z=-3;z<=3;z++){level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);for(int y=80;y<=85;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
   level.setBlock(pos,UniversalSpawnerSystem.MINI_BOSS_SPAWNER_BLOCK.get().defaultBlockState(),3);var spawner=(UniversalSpawnerBlockEntity)level.getBlockEntity(pos);spawner.onLoad();
   var live=ZombieroolModEntities.PANZER.get().create(level);live.getPersistentData().putBoolean("zr_special_spawner",true);live.setPos(1540.5,80,.5);level.addFreshEntity(live);live.setNoAi(true);created.add(live);
   rule(level,"wave","brutus",9,pos);check(!MiniBossTransitions.evaluate(level),"Wave trigger waits for the configured round");waveField("currentWave").set(null,9);check(MiniBossTransitions.evaluate(level)&&SpecialEnemyConfig.get(level).getString("type").equals("brutus"),"Wave trigger switches the map's next boss");check(live.isPanzer()&&spawner.getMobType()==SpawnerMobType.BRUTUS,"Live boss remains Panzer while spawners adopt Brutus");spawner.setConfig(SpawnerMobType.PANZER,"","0","0",false,1);check(SpecialEnemyConfig.get(level).getString("type").equals("brutus")&&spawner.getMobType()==SpawnerMobType.BRUTUS,"Editing a spawner cannot reset the global boss choice");check(!MiniBossTransitions.evaluate(level),"A transition fires only once per match");
   GlobalSwitchState.setActivated(level,false);rule(level,"power","panzer",9,pos);check(!MiniBossTransitions.evaluate(level),"Power trigger waits for the switch");GlobalSwitchState.setActivated(level,true);check(MiniBossTransitions.evaluate(level),"Power trigger changes the selected boss");
   var activation=pos.offset(2,0,0);level.setBlock(activation,ZombieroolModBlocks.ACTIVATOR.get().defaultBlockState(),3);rule(level,"activator","brutus",9,activation);check(MiniBossTransitions.evaluate(level),"Activated block at configured coordinates switches the boss");level.setBlock(activation,Blocks.AIR.defaultBlockState(),3);MiniBossTransitions.reset(level);check(!MiniBossTransitions.evaluate(level),"Missing activator never triggers a switch");
   level.setBlock(activation,ZombieroolModBlocks.ALPHA_ACTIVATOR.get().defaultBlockState(),3);String channel=((me.cryo.zombierool.block.AbstractActivatorBlock)level.getBlockState(activation).getBlock()).signalChannel();boolean channelBefore=MultiReceptorState.isActivated(level,channel);MultiReceptorState.setActivated(level,channel,false);rule(level,"activator","panzer",9,activation);check(!MiniBossTransitions.evaluate(level),"Channel activator waits for its own channel");MultiReceptorState.setActivated(level,channel,true);check(MiniBossTransitions.evaluate(level),"Channel activator switches bosses independently of the global power state");MultiReceptorState.setActivated(level,channel,channelBefore);level.setBlock(activation,Blocks.AIR.defaultBlockState(),3);
   var soulPos=pos.offset(4,0,0);level.setBlock(soulPos,MapAuthorSystem.SOUL_CHEST.get().defaultBlockState(),3);var chest=(SoulChestSystem.Chest)level.getBlockEntity(soulPos);chest.enabled=true;chest.completed=false;chest.objectiveId="boss_transition_test";SoulObjectiveRegistry.get(level).update(chest);var checkerPos=pos.offset(6,0,0);level.setBlock(checkerPos,MapAuthorSystem.COMPLETION.get().defaultBlockState(),3);var checker=(SoulCompletionSystem.Checker)level.getBlockEntity(checkerPos);checker.ids=chest.objectiveId;checker.enabled=true;rule(level,"soul_checker","panzer",9,checkerPos);check(!MiniBossTransitions.evaluate(level),"Soul checker waits for its objectives");chest.completed=true;SoulObjectiveRegistry.get(level).update(chest);check(MiniBossTransitions.evaluate(level),"Completed soul checker changes the boss");
   check(MiniBossDefinitions.contains("zombierool_test:warden"),"Datapack mini-boss definition registers without Java");var lua=new me.cryo.zombierool.scripting.ZombieroolAPI(level);var globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();globals.set("api",me.cryo.zombierool.scripting.LegacySoundBindings.wrap(lua));
   globals.load("assert(api:setMiniBossType('zombierool_test:warden')); assert(api:getMiniBossType()=='zombierool_test:warden'); assert(api:getSpecialSpawner(1540,80,0).type=='zombierool_test:warden'); assert(api:configureSpecialSpawner(1540,80,0,{weight=3})); assert(api:getMiniBossType()=='zombierool_test:warden'); assert(not api:setMiniBossType('unknown:missing'))", "live-custom-boss").call();check(SpecialEnemyConfig.get(level).getString("type").equals("zombierool_test:warden"),"Real LuaJ switches to custom bosses and preserves the choice when editing local settings");
   rule(level,"none","brutus",9,pos);check(SpecialEnemySpawns.spawn(level,spawner,true),"Custom mini boss spawns through the shared spawner");var custom=level.getEntitiesOfClass(me.cryo.zombierool.entity.SpecialEnemyEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(5)).stream().filter(e->e.kind().equals("zombierool_test:warden")).findFirst().orElseThrow();created.add(custom);check(custom.isBoss()&&!custom.isPanzer()&&custom.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)==.42&&custom.bossProfile().getDouble("health")==320,"Custom boss inherits its template and datapack statistics");var saved=new CompoundTag();custom.saveWithoutId(saved);var restored=ZombieroolModEntities.CUSTOM_MINI_BOSS.get().create(level);restored.load(saved);check(restored.kind().equals(custom.kind())&&restored.bossProfile().equals(custom.bossProfile()),"Custom boss identity and texture profile survive entity NBT reload");custom.discard();live.discard();
   var machine=pos.offset(10,0,0);level.setBlock(machine,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.POWERED,true),3);((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(machine)).setPowered(true);BrutusMachineLocks.reset(level);var brutus=ZombieroolModEntities.BRUTUS.get().create(level);brutus.moveTo(1546.5,80,.5,0,0);brutus.getPersistentData().putBoolean("zr_special_spawner",true);brutus.setEmergenceTicks(0);brutus.setOnGround(true);level.addFreshEntity(brutus);created.add(brutus);
   for(int t=0;t<240&&!BrutusMachineLocks.locked(level,machine);t++){level.getServer().getWorldData().overworldData().setGameTime(clock+t);tickClock.setInt(level.getServer(),startTick+t);brutus.tickCount=t;brutus.tick();}
   check(BrutusMachineLocks.locked(level,machine),"Brutus locks an operational perk machine encountered while pursuing its player");var lockState=BrutusMachineLocks.get(level).save(new CompoundTag());check(java.util.Arrays.stream(lockState.getLongArray("positions")).anyMatch(p->p==machine.asLong()),"Machine locks persist in world data");player.setPos(1548.5,80,.5);PointManager.setScore(player,0);BrutusMachineLocks.holdUnlock(player,machine);BrutusMachineLocks.tickUnlock(player);
   check(BrutusMachineLocks.locked(level,machine)&&PointManager.getScore(player)==0,"Insufficient points prevent timed unlocking without charging");
   PointManager.setScore(player,2500);BrutusMachineLocks.holdUnlock(player,machine);
   for(int t=0;t<30;t++)BrutusMachineLocks.tickUnlock(player);
   check(BrutusMachineLocks.locked(level,machine)&&PointManager.getScore(player)==2500,"The first half of unlocking neither charges points nor removes chains");
   BrutusMachineLocks.cancelUnlock(player);for(int t=0;t<60;t++)BrutusMachineLocks.tickUnlock(player);
   check(BrutusMachineLocks.locked(level,machine)&&PointManager.getScore(player)==2500,"Releasing the action key cancels progress without payment");
   BrutusMachineLocks.holdUnlock(player,machine);player.setPos(1558,80,.5);BrutusMachineLocks.tickUnlock(player);player.setPos(1548.5,80,.5);
   for(int t=0;t<60;t++)BrutusMachineLocks.tickUnlock(player);
   check(BrutusMachineLocks.locked(level,machine)&&PointManager.getScore(player)==2500,"Leaving interaction range cancels the hold");
   BrutusMachineLocks.holdUnlock(player,machine);level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+11);BrutusMachineLocks.tickUnlock(player);
   for(int t=0;t<60;t++)BrutusMachineLocks.tickUnlock(player);
   check(BrutusMachineLocks.locked(level,machine),"A missing held-key heartbeat cancels server progress");
   BrutusMachineLocks.holdUnlock(player,machine);PointManager.setScore(player,1000);BrutusMachineLocks.tickUnlock(player);PointManager.setScore(player,2500);
   for(int t=0;t<60;t++)BrutusMachineLocks.tickUnlock(player);
   check(BrutusMachineLocks.locked(level,machine)&&PointManager.getScore(player)==2500,"Losing the required balance cancels unlocking instead of allowing a later free completion");
   unlockFully(level,player,machine);
   check(!BrutusMachineLocks.locked(level,machine)&&PointManager.getScore(player)==500,"Completing the hold charges exactly 2000 points and only unlocks the machine");
   var unlockPrice=new CompoundTag();unlockPrice.putInt("brutusUnlockCost",2001);SpecialEnemyConfig.update(level,unlockPrice);BrutusMachineLocks.lock(level,machine);PointManager.setScore(player,2500);unlockFully(level,player,machine);check(PointManager.getScore(player)==499,"Configured unlock prices are charged exactly without point rounding");unlockPrice.putInt("brutusUnlockCost",2000);SpecialEnemyConfig.update(level,unlockPrice);player.setPos(1558,80,.5);level.getServer().getWorldData().overworldData().setGameTime(clock);brutus.discard();level.setBlock(machine,Blocks.AIR.defaultBlockState(),3);
   var box=pos.offset(12,0,0);level.setBlock(box,MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxSystem.MysteryBoxBlock.ACTIVE,true),3);brutus=ZombieroolModEntities.BRUTUS.get().create(level);brutus.moveTo(1548.5,80,.5,0,0);brutus.getPersistentData().putBoolean("zr_special_spawner",true);brutus.setEmergenceTicks(0);brutus.setOnGround(true);level.addFreshEntity(brutus);created.add(brutus);for(int t=0;t<240&&!BrutusMachineLocks.locked(level,box);t++){level.getServer().getWorldData().overworldData().setGameTime(clock+t);tickClock.setInt(level.getServer(),startTick+t);brutus.tickCount=t;brutus.tick();}check(BrutusMachineLocks.locked(level,box),"Brutus disables the active mystery box encountered during player pursuit");brutus.discard();level.setBlock(box,Blocks.AIR.defaultBlockState(),3);
   // Machine locking is opportunistic; it must not replace combat or tour the map.
   level.setBlock(machine,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.POWERED,true),3);((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(machine)).setPowered(true);
   var priorityBoss=ZombieroolModEntities.BRUTUS.get().create(level);priorityBoss.moveTo(1546.5,80,.5,0,0);priorityBoss.setOnGround(true);priorityBoss.setTarget(player);created.add(priorityBoss);
   var priorityGoal=new BrutusMachineLocks.LockGoal(priorityBoss);
   player.setPos(1549.5,80,2.5);check(!priorityGoal.canUse(),"A nearby visible player has priority over an eligible machine");
   player.setPos(1563.5,80,.5);priorityBoss.setPos(1540.5,80,.5);check(!new BrutusMachineLocks.LockGoal(priorityBoss).canUse(),"A machine ten blocks away is not detected or pursued");
   priorityBoss.setPos(1554.5,80,.5);check(!new BrutusMachineLocks.LockGoal(priorityBoss).canUse(),"Brutus does not turn away from its prey to visit a machine behind it");
   priorityBoss.setPos(1546.5,80,.5);check(!new BrutusMachineLocks.LockGoal(priorityBoss).canUse(),"A visible machine outside striking distance cannot trigger a detour");
   priorityBoss.setPos(1548.5,80,.5);var cooldownGoal=new BrutusMachineLocks.LockGoal(priorityBoss);check(cooldownGoal.canUse(),"A visible machine already within striking distance remains eligible");cooldownGoal.start();priorityBoss.setPos(1548.5,80,.5);
   for(int t=0;t<24;t++)cooldownGoal.tick();check(BrutusMachineLocks.locked(level,machine),"An opportunistic machine lock still completes normally");cooldownGoal.stop();
   level.setBlock(machine,Blocks.AIR.defaultBlockState(),3);level.setBlock(box,MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxSystem.MysteryBoxBlock.ACTIVE,true),3);
   level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+200);check(!cooldownGoal.canUse(),"After locking a machine Brutus spends at least thirty seconds pursuing players instead of chaining locks");
   level.setBlock(box,Blocks.AIR.defaultBlockState(),3);BrutusMachineLocks.reset(level);
   level.setBlock(machine,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.POWERED,true),3);((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(machine)).setPowered(true);
   var interruptedGoal=new BrutusMachineLocks.LockGoal(priorityBoss);check(interruptedGoal.canUse(),"A fresh machine opportunity can start before combat");interruptedGoal.start();
   player.setPos(1550.5,80,.5);check(interruptedGoal.canContinueToUse(),"Nearby players no longer cancel a committed machine slam");for(int t=0;t<24;t++)interruptedGoal.tick();check(BrutusMachineLocks.locked(level,machine),"A machine lock completes while the player remains within melee range");interruptedGoal.stop();BrutusMachineLocks.reset(level);player.setPos(1563.5,80,.5);
   var attackedGoal=new BrutusMachineLocks.LockGoal(priorityBoss);check(attackedGoal.canUse(),"The boss can start a machine action while its target is distant");attackedGoal.start();
   check(priorityBoss.hurt(level.damageSources().playerAttack(player),8),"The ranged player's shot is accepted by Brutus");check(attackedGoal.canContinueToUse(),"Distant player fire cannot cancel every machine slam during a chase");for(int t=0;t<24;t++)attackedGoal.tick();check(BrutusMachineLocks.locked(level,machine),"A machine slam completes under distant player fire");attackedGoal.stop();priorityBoss.discard();level.setBlock(machine,Blocks.AIR.defaultBlockState(),3);player.setPos(1558,80,.5);
   BrutusMachineLocks.reset(level);
   // An operational machine inside a solid enclosure must never monopolise Brutus' movement.
   level.setBlock(machine,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.POWERED,true),3);((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(machine)).setPowered(true);
   for(int x=1549;x<=1551;x++)for(int z=-1;z<=1;z++)for(int y=80;y<=84;y++)if(x!=1550||z!=0)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
   brutus=ZombieroolModEntities.BRUTUS.get().create(level);brutus.moveTo(1548.5,80,.5,0,0);brutus.setEmergenceTicks(0);brutus.setOnGround(true);brutus.getPersistentData().putBoolean("zr_special_spawner",true);level.addFreshEntity(brutus);created.add(brutus);
   brutus.setEmergenceTicks(0);brutus.setNoAi(false);brutus.setOnGround(true);brutus.getPersistentData().remove("zr_emerge_no_ai");
   ((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(machine)).setPowered(true);
   var lockGoal=new BrutusMachineLocks.LockGoal(brutus);
   check(!lockGoal.canUse(),"Brutus rejects a powered machine enclosed by walls instead of accepting an approximate path");
   for(int x=1549;x<=1551;x++)for(int z=-1;z<=1;z++)for(int y=80;y<=84;y++)if(x!=1550||z!=0)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
   ((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(machine)).setPowered(true);
   level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+601);
   check(lockGoal.canUse(),"A rejected machine becomes eligible again after its retry delay");lockGoal.start();
   // Losing striking distance cancels the slam without starting any machine pursuit.
   check(brutus.getNavigation().getPath()==null,"Machine locking never starts a competing navigation path");
   brutus.setPos(1545.5,80,.5);
   check(!lockGoal.canContinueToUse(),"Leaving striking distance cancels the lock instead of chasing the machine");lockGoal.stop();
   check(!brutus.getPersistentData().getBoolean("zr_boss_machine")&&!lockGoal.canUse(),"Abandoning a machine releases movement and prevents immediate retries");
   brutus.discard();level.setBlock(machine,Blocks.AIR.defaultBlockState(),3);level.getServer().getWorldData().overworldData().setGameTime(clock);
   var shortcutEntry=pos.offset(8,0,0);var shortcutExit=pos.offset(16,0,0);
   level.setBlock(shortcutEntry,TraversalSystem.JUMP.get().defaultBlockState(),3);level.setBlock(shortcutExit,TraversalSystem.JUMP.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
   var shortcut=(TraversalSystem.Anchor)level.getBlockEntity(shortcutEntry);shortcut.root=shortcutEntry;shortcut.exit=shortcutExit;((TraversalSystem.Anchor)level.getBlockEntity(shortcutExit)).root=shortcutEntry;shortcut.sync();((TraversalSystem.Anchor)level.getBlockEntity(shortcutExit)).sync();
   var groundProbe=ZombieroolModEntities.BRUTUS.get().create(level);groundProbe.moveTo(1546.5,80,.5,0,0);groundProbe.setOnGround(true);created.add(groundProbe);
   var groundPath=groundProbe.getNavigation().createPath(player,0);check(groundPath!=null&&RouteNavigation.reachesPlayer(groundPath,player),"Boss can plan the nearby player's accessible ground route");
   boolean unnecessaryJump=false;for(int i=1;i<groundPath.getNodeCount();i++)unnecessaryJump|=groundPath.getNode(i).distanceTo(groundPath.getNode(i-1))>2;
   check(!unnecessaryJump,"A nearby walking chase does not detour through a transport link priced as a single step");
   check(!new BossJumpLinkGoal(groundProbe).canUse()&&RouteNavigation.reachesPlayer(groundProbe.getNavigation().getPath(),player),"Jump recovery first restores a complete pursuit path instead of boarding a greedy shortcut");
   var shortcutRoute=new TraversalSystem.Route(shortcut,false);groundProbe.setPos(shortcutRoute.start());groundProbe.setTarget(player);
   groundProbe.getPersistentData().putBoolean("zr_boss_machine",true);
   groundProbe.getNavigation().moveTo(new net.minecraft.world.level.pathfinder.Path(java.util.List.of(new net.minecraft.world.level.pathfinder.Node(shortcutEntry.getX(),shortcutEntry.getY(),shortcutEntry.getZ()),new net.minecraft.world.level.pathfinder.Node(shortcutExit.getX(),shortcutExit.getY(),shortcutExit.getZ())),shortcutExit,true),1);
   TraversalSystem.approach(groundProbe);check(!TraversalMotion.active(groundProbe),"A pending navigation link cannot board while Brutus is slamming a machine");
   TraversalMotion.start(groundProbe,shortcutRoute);check(!TraversalMotion.active(groundProbe)&&BossTraversalHistory.allows(groundProbe,shortcutRoute),"Direct transport activation is rejected during a machine slam without consuming the link history");
   groundProbe.getPersistentData().remove("zr_boss_machine");
   player.setPos(1551.5,80,.5);
   TraversalSystem.approach(groundProbe);
   check(!TraversalMotion.active(groundProbe)&&RouteNavigation.reachesPlayer(groundProbe.getNavigation().getPath(),player),"A player joining the boss platform cancels an obsolete transport departure before boarding");
   boolean staleLink=false;var recovered=groundProbe.getNavigation().getPath();for(int i=1;i<recovered.getNodeCount();i++)staleLink|=recovered.getNode(i-1).distanceTo(recovered.getNode(i))>2;
   check(!staleLink,"Recovered close pursuit walks to the player without jumping away and returning");
   groundProbe.setPos(1548.95,80,.5);groundProbe.getNavigation().stop();groundProbe.getNavigation().moveTo(groundProbe.getNavigation().createPath(player,0),1);
   var follow=RouteNavigation.class.getDeclaredMethod("followThePath");follow.setAccessible(true);follow.invoke(groundProbe.getNavigation());
   check(groundProbe.getNavigation().getPath().getNextEntityPos(groundProbe).x>groundProbe.getX(),"Replanning ahead does not steer a boss backwards to its own starting cell centre");
   player.setPos(1558,80,.5);groundProbe.getNavigation().stop();

   groundProbe.discard();level.destroyBlock(shortcutEntry,false);level.setBlock(shortcutExit,Blocks.AIR.defaultBlockState(),3);
   var entry=pos.offset(4,0,0);var exit=pos.offset(14,0,0);level.setBlock(soulPos,Blocks.AIR.defaultBlockState(),3);level.setBlock(checkerPos,Blocks.AIR.defaultBlockState(),3);for(int x=1545;x<1554;x++)for(int z=-3;z<=3;z++){level.setBlock(new BlockPos(x,79,z),Blocks.AIR.defaultBlockState(),3);level.setBlock(new BlockPos(x,78,z),Blocks.AIR.defaultBlockState(),3);}level.setBlock(entry,TraversalSystem.JUMP.get().defaultBlockState(),3);level.setBlock(exit,TraversalSystem.JUMP.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);var anchor=(TraversalSystem.Anchor)level.getBlockEntity(entry);var endpoint=(TraversalSystem.Anchor)level.getBlockEntity(exit);anchor.root=entry;anchor.exit=exit;endpoint.root=entry;anchor.duration=16;anchor.clearance=3;anchor.sync();endpoint.sync();
   var motion=TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);motion.setAccessible(true);
   for(var type:java.util.List.of(ZombieroolModEntities.PANZER.get(),ZombieroolModEntities.BRUTUS.get())){var boss=type.create(level);boss.moveTo(1543.5,80,.5,90,0);boss.setEmergenceTicks(0);boss.setOnGround(true);boss.getPersistentData().putBoolean("zr_special_spawner",true);level.addFreshEntity(boss);created.add(boss);boolean crossed=false;for(int t=0;t<220&&boss.getX()<1555;t++){tickClock.setInt(level.getServer(),startTick+t);boss.tickCount=t;level.getServer().getWorldData().overworldData().setGameTime(clock+t);boss.tick();crossed|=TraversalMotion.active(boss);motion.invoke(null,level);}check(crossed&&boss.getX()>1554&&!boss.isNoAi()&&!boss.noPhysics,boss.kind()+" boards a jump link at the ledge and resumes pursuit on the other side");boss.discard();}
   anchor.bidirectional=true;anchor.sync();
   var probe=ZombieroolModEntities.BRUTUS.get().create(level);probe.moveTo(1544.5,80,.5,0,0);probe.setTarget(player);probe.setOnGround(true);created.add(probe);
   var forward=new TraversalSystem.Route(anchor,false);var reverse=new TraversalSystem.Route(anchor,true);
   check(BossTraversalHistory.allows(probe,forward),"A new objective permits its first directed traversal");TraversalMotion.start(probe,forward);
   check(!BossTraversalHistory.allows(probe,reverse)&&!BossTraversalHistory.allows(probe,forward),"The same objective cannot immediately reuse a jump or reverse back through its endpoint");
   probe.getPersistentData().putBoolean("zr_boss_machine",true);
   check(!BossTraversalHistory.allows(probe,reverse),"Switching to a machine objective preserves the transport circuit history");
   probe.getPersistentData().remove("zr_boss_machine");
   check(!BossTraversalHistory.allows(probe,forward),"Returning to player pursuit cannot restart the previous circuit");
   player.setPos(1563.5,80,.5);BossTraversalHistory.objective(probe,player);
   check(!BossTraversalHistory.allows(probe,reverse)&&!BossTraversalHistory.allows(probe,forward),"Moving over four blocks on the arrival side cannot reset transport history");
   for(int t=0;t<anchor.duration;t++)motion.invoke(null,level);
   check(!TraversalMotion.active(probe)&&!probe.isNoAi()&&probe.getNavigation().getPath()==null,"Finishing transport discards the departure navigation and restores AI");
   // A second authored link that leads back to the old departure also closes a cycle.
   var cycleEntry=pos.offset(14,0,2);var cycleExit=pos.offset(4,0,2);
   level.setBlock(cycleEntry,TraversalSystem.JUMP.get().defaultBlockState(),3);level.setBlock(cycleExit,TraversalSystem.JUMP.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
   var cycle=(TraversalSystem.Anchor)level.getBlockEntity(cycleEntry);cycle.root=cycleEntry;cycle.exit=entry;((TraversalSystem.Anchor)level.getBlockEntity(cycleExit)).root=cycleEntry;cycle.sync();
   check(!BossTraversalHistory.allows(probe,new TraversalSystem.Route(cycle,false)),"A different link cannot close a cycle back to a recent departure");
   cycle.exit=pos.offset(22,0,2);BossTraversalHistory.started(probe,new TraversalSystem.Route(cycle,false));
   probe.getPersistentData().putBoolean("zr_boss_machine",true);
   check(!BossTraversalHistory.allows(probe,forward),"A multi-link circuit retains its first jump after a machine interruption");
   probe.getPersistentData().remove("zr_boss_machine");cycle.exit=entry;
   check(!BossTraversalHistory.allows(probe,new TraversalSystem.Route(cycle,false)),"The later link cannot close the circuit after returning from machine pursuit");
   player.setPos(1540.5,80,.5);check(BossTraversalHistory.allows(probe,reverse),"Moving the pursued player permits a legitimate return traversal");
   level.setBlock(cycleEntry,Blocks.AIR.defaultBlockState(),3);level.setBlock(cycleExit,Blocks.AIR.defaultBlockState(),3);probe.discard();
   // Full pursuit across two disconnected platforms: jump, zipline, machine, then moving prey.
   for(int cx=98;cx<=101;cx++){level.getChunk(cx,0);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cx,0),FullChunkStatus.ENTITY_TICKING);}
   for(int x=1580;x<=1615;x++)for(int z=8;z<=12;z++){
       boolean gap=x>1584&&x<1593||x>1597&&x<1606;
       level.setBlock(new BlockPos(x,79,z),gap?Blocks.AIR.defaultBlockState():Blocks.STONE.defaultBlockState(),3);
       level.setBlock(new BlockPos(x,78,z),gap?Blocks.AIR.defaultBlockState():ZombieroolModBlocks.PATH.get().defaultBlockState(),3);
       for(int y=80;y<=85;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
   }
   var towerJumpStart=new BlockPos(1584,80,10);var towerJumpEnd=new BlockPos(1593,80,10);
   var towerZipStart=new BlockPos(1597,80,10);var towerZipEnd=new BlockPos(1606,80,10);
   level.setBlock(towerJumpStart,TraversalSystem.JUMP.get().defaultBlockState(),3);level.setBlock(towerJumpEnd,TraversalSystem.JUMP.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
   level.setBlock(towerZipStart,TraversalSystem.ZIPLINE.get().defaultBlockState(),3);level.setBlock(towerZipEnd,TraversalSystem.ZIPLINE.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
   var towerJump=(TraversalSystem.Anchor)level.getBlockEntity(towerJumpStart);towerJump.root=towerJumpStart;towerJump.exit=towerJumpEnd;towerJump.bidirectional=true;towerJump.duration=16;towerJump.clearance=3;((TraversalSystem.Anchor)level.getBlockEntity(towerJumpEnd)).root=towerJumpStart;towerJump.sync();
   var towerZip=(TraversalSystem.Anchor)level.getBlockEntity(towerZipStart);towerZip.root=towerZipStart;towerZip.exit=towerZipEnd;towerZip.bidirectional=true;towerZip.duration=24;((TraversalSystem.Anchor)level.getBlockEntity(towerZipEnd)).root=towerZipStart;towerZip.sync();
   var towerPerk=new BlockPos(1608,80,11);level.setBlock(towerPerk,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.POWERED,true),3);((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(towerPerk)).setPowered(true);
   player.setPos(1612.5,80,10.5);var towerBoss=ZombieroolModEntities.BRUTUS.get().create(level);towerBoss.moveTo(1583.5,80,10.5,0,0);towerBoss.setOnGround(true);towerBoss.getPersistentData().putBoolean("zr_special_spawner",true);level.addFreshEntity(towerBoss);created.add(towerBoss);
   var towerFlights=new java.util.ArrayList<Integer>();boolean towerFlying=false;
   for(int t=0;t<400;t++){
       tickClock.setInt(level.getServer(),startTick+t);level.getServer().getWorldData().overworldData().setGameTime(clock+t);towerBoss.tickCount=t;player.setHealth(player.getMaxHealth());
       if(t>240)player.setPos(1611.5+(t%40<20?0:1),80,10.5);
       towerBoss.tick();boolean active=TraversalMotion.active(towerBoss);if(active&&!towerFlying)towerFlights.add(TraversalMotion.flight(towerBoss).style);motion.invoke(null,level);towerFlying=TraversalMotion.active(towerBoss);
   }
   check(towerFlights.equals(java.util.List.of(0,1))&&towerBoss.getX()>1606,"A complete jump then zipline pursuit reaches the moving player without reverse or repeated boarding over 400 ticks");
   check(BrutusMachineLocks.locked(level,towerPerk),"Brutus locks a machine encountered after the zipline while continuing the same pursuit");
   towerBoss.discard();level.destroyBlock(towerJumpStart,false);level.setBlock(towerJumpEnd,Blocks.AIR.defaultBlockState(),3);level.destroyBlock(towerZipStart,false);level.setBlock(towerZipEnd,Blocks.AIR.defaultBlockState(),3);level.setBlock(towerPerk,Blocks.AIR.defaultBlockState(),3);BrutusMachineLocks.reset(level);
   // Reproduce the reported ten-block jump at the exact map coordinates.
   var reportedEntry=new BlockPos(-9,-61,16);var reportedExit=new BlockPos(-9,-61,6);
   for(int cz=0;cz<=1;cz++){level.getChunk(-1,cz);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(-1,cz),FullChunkStatus.ENTITY_TICKING);}
   for(int x=-11;x<=-7;x++)for(int z=3;z<=18;z++){
       level.setBlock(new BlockPos(x,-62,z),(z>6&&z<16?Blocks.AIR:Blocks.STONE).defaultBlockState(),3);
       level.setBlock(new BlockPos(x,-63,z),z>6&&z<16?Blocks.AIR.defaultBlockState():ZombieroolModBlocks.PATH.get().defaultBlockState(),3);
       for(int y=-61;y<=-56;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
   }
   level.setBlock(reportedEntry,TraversalSystem.JUMP.get().defaultBlockState(),3);level.setBlock(reportedExit,TraversalSystem.JUMP.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
   var reported=(TraversalSystem.Anchor)level.getBlockEntity(reportedEntry);reported.root=reportedEntry;reported.exit=reportedExit;reported.duration=16;reported.clearance=3;reported.bidirectional=true;var reportedEnd=(TraversalSystem.Anchor)level.getBlockEntity(reportedExit);reportedEnd.root=reportedEntry;reported.sync();reportedEnd.sync();
   player.setPos(-8.5,-61,4.5);var loopBoss=ZombieroolModEntities.BRUTUS.get().create(level);loopBoss.moveTo(-8.5,-61,16.5,0,0);loopBoss.setOnGround(true);loopBoss.getPersistentData().putBoolean("zr_special_spawner",true);level.addFreshEntity(loopBoss);created.add(loopBoss);int flights=0;boolean previousFlight=false;
   for(int t=0;t<400;t++){tickClock.setInt(level.getServer(),startTick+t);level.getServer().getWorldData().overworldData().setGameTime(clock+t);loopBoss.tickCount=t;loopBoss.tick();boolean flying=TraversalMotion.active(loopBoss);if(flying&&!previousFlight)flights++;motion.invoke(null,level);previousFlight=TraversalMotion.active(loopBoss);}
   check(flights==1&&loopBoss.getZ()<7&&!loopBoss.isNoAi(),"Reported jump at -9 -61 16 / -9 -61 6 is traversed once, with no return loop over 400 AI ticks");
   loopBoss.discard();level.destroyBlock(reportedEntry,false);level.setBlock(reportedExit,Blocks.AIR.defaultBlockState(),3);player.setPos(1558,80,.5);
   WorldConfig.get(level).setSpecialEnemies(SpecialEnemyConfig.defaults());MatchConfigSnapshot.begin(level);check(lua.setMiniBossType("brutus"),"A running match can change its boss through Lua");MatchConfigSnapshot.restore(level);check(SpecialEnemyConfig.get(level).getString("type").equals("panzer"),"Ending a match restores the configured initial boss for the next game");
   level.destroyBlock(entry,false);level.setBlock(exit,Blocks.AIR.defaultBlockState(),3);System.out.println("BOSS BEHAVIOR PASS: "+count+" assertions");BossPursuitMatrixTest.run(level,player);
  }finally{tickClock.setInt(level.getServer(),startTick);created.forEach(net.minecraft.world.entity.Entity::discard);SoulObjectiveRegistry.get(level).remove(pos.offset(4,0,0));WorldConfig.get(level).setSpecialEnemies(old);SpecialEnemyConfig.syncSpawners(level);MiniBossTransitions.reset(level);BrutusMachineLocks.reset(level);waveField("isPausedByPlayer").set(null,paused);waveField("gameRunning").set(null,running);waveField("currentWave").set(null,wave);GlobalSwitchState.setActivated(level,power);level.getServer().getWorldData().overworldData().setGameTime(clock);player.setPos(oldPos);player.setGameMode(mode);PointManager.modifyScore(player,score-PointManager.getScore(player));for(int x=1540;x<=1554;x++)level.setBlock(new BlockPos(x,80,0),Blocks.AIR.defaultBlockState(),3);}
 }
}
