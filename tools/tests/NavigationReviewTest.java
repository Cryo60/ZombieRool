package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.*;

public final class NavigationReviewTest {
 private static final class DamageProbe extends net.minecraftforge.common.util.FakePlayer {
  DamageProbe(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ZiplineDamageProbe"));}
  @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
 }
 private static int assertions;
 private static void check(boolean ok,String message){System.out.println("26.7 CHECK: "+message+" = "+ok);if(!ok)throw new AssertionError(message);assertions++;}
 private static TraversalSystem.Anchor link(ServerLevel level,BlockPos p,BlockPos q,net.minecraft.world.level.block.Block block){
  level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(q.below(),Blocks.STONE.defaultBlockState(),3);
  level.setBlock(p,block.defaultBlockState(),3);level.setBlock(q,block.defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
  var root=(TraversalSystem.Anchor)level.getBlockEntity(p);root.exit=q;((TraversalSystem.Anchor)level.getBlockEntity(q)).root=p;root.sync();root.onLoad();return root;
 }
 public static void run(ServerLevel level,ServerPlayer player)throws Exception{
  for(int x=440;x<=500;x++)for(int z=-5;z<=5;z++){
   level.getChunkAt(new BlockPos(x,80,z));level.setBlock(new BlockPos(x,78,z),Blocks.AIR.defaultBlockState(),3);level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
   for(int y=80;y<=84;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
  }
  var root=link(level,new BlockPos(450,80,0),new BlockPos(480,80,0),TraversalSystem.ZIPLINE.get());
  var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(440.5,80,.5);level.addFreshEntity(zombie);zombie.setEmergenceTicks(0);zombie.setNoAi(false);zombie.setOnGround(true);
  var path=zombie.getNavigation().createPath(new BlockPos(490,80,0),0);
  check(path==null||!path.canReach(),"Nearby zipline endpoints never authorize ordinary unmarked terrain");
  for(int x=440;x<=500;x++)for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);
  path=zombie.getNavigation().createPath(new BlockPos(490,80,0),0);
  check(path!=null&&path.canReach(),"Zipline entry and exit connect through explicitly authorized terrain");
  boolean edge=false;for(int i=0;i<path.getNodeCount()-1;i++)if(path.getNode(i).asBlockPos().equals(root.getBlockPos())&&path.getNode(i+1).asBlockPos().equals(root.exit))edge=true;
  check(edge,"Authorized endpoint access preserves use of the planned zipline edge");
  for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(445,80,z),ZombieroolModBlocks.LIMIT.get().defaultBlockState(),3);
  var restricted=zombie.getNavigation().createPath(new BlockPos(490,80,0),0);
  check(restricted==null||!restricted.canReach(),"Automatic endpoint areas continue to respect a Limit Block barrier");
  for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(445,80,z),Blocks.AIR.defaultBlockState(),3);
  var reopened=zombie.getNavigation().createPath(new BlockPos(490,80,0),0);
  check(reopened!=null&&reopened.canReach(),"Per-search column caches reflect terrain changes in the next path calculation");
  level.destroyBlock(root.getBlockPos(),false);
  for(int x=440;x<=450;x++)level.setBlock(new BlockPos(x,79+x-440,0),Blocks.STONE.defaultBlockState(),3);
  for(int x=451;x<480;x++)for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(x,79,z),Blocks.AIR.defaultBlockState(),3);
  var highRoot=link(level,new BlockPos(450,90,0),new BlockPos(480,80,0),TraversalSystem.ZIPLINE.get());
  zombie.setPos(440.5,80,.5);var stairRoute=zombie.getNavigation().createPath(new BlockPos(490,80,0),0);
  if(stairRoute!=null)for(int i=0;i<stairRoute.getNodeCount();i++)System.out.println("STAIR ROUTE: "+stairRoute.getNode(i).asBlockPos());
  check(stairRoute!=null&&stairRoute.canReach(),"A staircase with authorized columns reaches a zipline ten blocks higher");
  boolean highEdge=false;for(int i=0;i<stairRoute.getNodeCount()-1;i++)if(stairRoute.getNode(i).asBlockPos().equals(highRoot.getBlockPos())&&stairRoute.getNode(i+1).asBlockPos().equals(highRoot.exit))highEdge=true;
  check(highEdge,"The authorized staircase approach leads to the upper zipline edge");
  level.destroyBlock(highRoot.getBlockPos(),false);for(int x=440;x<=450;x++)for(int y=80;y<=90;y++)level.setBlock(new BlockPos(x,y,0),Blocks.AIR.defaultBlockState(),3);
  for(int x=451;x<480;x++)for(int z=-5;z<=5;z++)level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
  root=link(level,new BlockPos(450,80,0),new BlockPos(480,80,0),TraversalSystem.ZIPLINE.get());
  var old=TraversalSystem.loaded(level);check(old==TraversalSystem.loaded(level),"Repeated route queries reuse the loaded-root snapshot");
  var configuration=root.config();configuration.putBoolean("allowPlayers",false);configuration.putBoolean("bidirectional",true);root.configure(configuration);
  var endpoint=(TraversalSystem.Anchor)level.getBlockEntity(root.exit);
  check(!root.allowPlayers&&!endpoint.allowPlayers&&!endpoint.config().getBoolean("allowPlayers"),"Disabling players applies to both zipline endpoints");
  player.setGameMode(GameType.SURVIVAL);player.setPos(root.start());ZiplineInteraction.use(player,root.getBlockPos());
  check(!TraversalMotion.active(player)&&root.accepts(zombie),"Player-only restriction blocks boarding while enemies retain access");
  check(TraversalSystem.edge(zombie,new Node(root.exit.getX(),root.exit.getY(),root.exit.getZ())).reverse(),"Changing bidirectionality invalidates the endpoint index immediately");
  var legacy=root.saveWithoutMetadata();legacy.remove("allowPlayers");root.load(legacy);check(root.allowPlayers,"Existing saved ziplines keep player access by default");
  // A rider cannot step into the void while waiting for an exact entry-center hit.
  var planned=new net.minecraft.world.level.pathfinder.Path(List.of(new Node(448,80,0),new Node(450,80,0),new Node(480,80,0)),root.exit,true);planned.setNextNodeIndex(1);
  zombie.setPos(448.8,80,.5);zombie.getNavigation().moveTo(planned,1);TraversalSystem.approach(zombie);
  check(TraversalMotion.active(zombie),"A planned zipline catches the zombie before the balcony edge");TraversalMotion.cancel(zombie);
  var queue=new ArrayList<me.cryo.zombierool.entity.ZombieEntity>();
  for(int i=0;i<4;i++){
   var rider=ZombieroolModEntities.ZOMBIE.get().create(level);rider.setPos(448.85+i*.15,80,.4+i*.06);level.addFreshEntity(rider);rider.setEmergenceTicks(0);rider.setNoAi(false);rider.setOnGround(true);
   var queuePath=new net.minecraft.world.level.pathfinder.Path(List.of(new Node(448,80,0),new Node(450,80,0),new Node(480,80,0)),root.exit,true);queuePath.setNextNodeIndex(1);rider.getNavigation().moveTo(queuePath,1);queue.add(rider);
  }
  check(queue.stream().allMatch(TraversalSystem::boardingZipline),"Overlapping zombies are recognized as boarding the same planned zipline");
  for(var rider:queue){TraversalSystem.approach(rider);rider.tick();}
  check(queue.stream().allMatch(rider->TraversalMotion.active(rider)&&rider.getDeltaMovement().horizontalDistanceSqr()<1e-9),"Four overlapping riders board without adding mutual horizontal push");
  for(var rider:queue){TraversalMotion.cancel(rider);rider.discard();}
  var tick=TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);tick.setAccessible(true);
  var victim=new DamageProbe(level);level.addNewPlayer(victim);victim.setGameMode(GameType.SURVIVAL);victim.getAbilities().invulnerable=false;victim.setInvulnerable(false);
  var spawnShield=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");spawnShield.setAccessible(true);spawnShield.setInt(victim,0);
  level.getServer().getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
  player.setGameMode(GameType.CREATIVE);zombie.setPos(root.start());zombie.setTarget(victim);root.duration=60;victim.setPos(456.5,80,.5);
  victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);victim.setHealth(1024);victim.invulnerableTime=0;
  TraversalMotion.start(zombie,new TraversalSystem.Route(root,false));float before=victim.getHealth();
  for(int i=0;i<14;i++)tick.invoke(null,level);
  check(victim.getHealth()<before,"A zipline-riding zombie damages a player it passes");float after=victim.getHealth();
  tick.invoke(null,level);check(victim.getHealth()==after,"Zipline attacks retain a cooldown instead of damaging every tick");TraversalMotion.cancel(zombie);zombie.discard();victim.discard();
  var wallPos=new BlockPos(495,90,3);for(int y=80;y<=89;y++)level.setBlock(new BlockPos(496,y,3),Blocks.STONE.defaultBlockState(),3);
  level.setBlock(wallPos,TraversalSystem.WALL.get().defaultBlockState(),3);var wall=(TraversalSystem.Anchor)level.getBlockEntity(wallPos);check(wall.ready(),"Wall test has a valid descent");
  var crawler=ZombieroolModEntities.CRAWLER.get().create(level);crawler.setPos(wall.start());level.addFreshEntity(crawler);crawler.setEmergenceTicks(0);crawler.setNoAi(false);
  float groundHeight=crawler.getBbHeight();TraversalMotion.start(crawler,new TraversalSystem.Route(wall,false));
  var box=crawler.getBoundingBox();check(box.minY<crawler.getY()-.9&&box.maxY>crawler.getY()+.9,"Wall crawler hitbox covers the rotated model above and below its origin");
  check(box.clip(crawler.position().add(-4,.85,0),crawler.position().add(4,.85,0)).isPresent(),"A shot through the wall crawler's upper body hits its box");
  check(me.cryo.zombierool.core.manager.DamageManager.computeHitZone(crawler,crawler.position().add(0,-.85,0)).equals("head"),"Wall crawler head detection follows its downward-facing rotated body");
  TraversalMotion.cancel(crawler);check(crawler.getBbHeight()==groundHeight&&Math.abs(crawler.getBoundingBox().minY-crawler.getY())<.001,"Crawler restores the normal ground hitbox after wall traversal");crawler.discard();
  var olympia=me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.get("olympia");
  check(olympia!=null,"Olympia definition is available for balance checks");
  check(ShotgunBalance.multiplier(olympia,3)==1&&Math.abs(ShotgunBalance.multiplier(olympia,50)-.1)<.001,"Shotguns retain close damage and lose 90 percent at long range");
  float longHeadshot=olympia.stats.damage*olympia.ballistics.count*2*ShotgunBalance.multiplier(olympia,50);
  float round10=(me.cryo.zombierool.config.WorldConfig.get(level).getZombieBaseHealth()+36)*1.15f;
  check(longHeadshot*2<round10,"Even all eight Olympia headshot pellets with Double Tap at 50 blocks cannot one-shot a default round-10 zombie");
  check(ShotgunBalance.multiplier(olympia,12)>ShotgunBalance.multiplier(olympia,20),"Shotgun falloff is monotonic between six and twenty-four blocks");
  var rifle=new me.cryo.zombierool.core.system.WeaponSystem.Definition();rifle.type="SNIPER";check(ShotgunBalance.multiplier(rifle,50)==1,"Sniper range damage is unaffected by shotgun balance");
  var dropped=new net.minecraft.world.entity.item.ItemEntity(level,490.5,80,2,new ItemStack(Items.DIAMOND));level.addFreshEntity(dropped);
  var savedDrop=new net.minecraft.nbt.CompoundTag();dropped.saveWithoutId(savedDrop);
  var stand=new net.minecraft.world.entity.decoration.ArmorStand(level,490.5,80,3);level.addFreshEntity(stand);WaveManager.clearDroppedItems(level);
  check(dropped.isRemoved()&&!stand.isRemoved(),"Match cleanup removes ground items while retaining authored entities");stand.discard();
  var stale=net.minecraft.world.entity.EntityType.ITEM.create(level);stale.load(savedDrop);var rejoin=new net.minecraftforge.event.entity.EntityJoinLevelEvent(stale,level,true);DroppedItemCleanup.join(rejoin);
  check(rejoin.isCanceled(),"A saved drop from an unloaded old-match chunk is rejected when that chunk returns");
  var fresh=new net.minecraft.world.entity.item.ItemEntity(level,490.5,80,2,new ItemStack(Items.DIAMOND));level.addFreshEntity(fresh);var freshTag=new net.minecraft.nbt.CompoundTag();fresh.saveWithoutId(freshTag);
  var restored=net.minecraft.world.entity.EntityType.ITEM.create(level);restored.load(freshTag);var currentJoin=new net.minecraftforge.event.entity.EntityJoinLevelEvent(restored,level,true);DroppedItemCleanup.join(currentJoin);
  check(!currentJoin.isCanceled(),"Saving and reloading a current-match dropped item preserves it");fresh.discard();
  for(var block:List.of(TraversalSystem.ZIPLINE.get(),TraversalSystem.JUMP.get(),TraversalSystem.NODE.get()))for(int removePhase:new int[]{0,2}){
   var p=new BlockPos(460,80,-3);var q=new BlockPos(468,80,-3);var a=link(level,p,q,block);BlockPos peak=new BlockPos(464,83,-3);
   if(block==TraversalSystem.NODE.get()){level.setBlock(peak,block.defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,1),3);a.apex=peak;((TraversalSystem.Anchor)level.getBlockEntity(peak)).root=p;a.sync();}
   level.destroyBlock(removePhase==0?p:q,false);
   check(level.getBlockState(p).isAir()&&level.getBlockState(q).isAir()&&(block!=TraversalSystem.NODE.get()||level.getBlockState(peak).isAir()),"Breaking "+block+" phase "+removePhase+" removes the complete link");
  }
  var p=new BlockPos(460,80,-3);var q=new BlockPos(468,80,-3);var vault=link(level,p,q,TraversalSystem.NODE.get());var peak=new BlockPos(464,83,-3);
  level.setBlock(peak,TraversalSystem.NODE.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,1),3);vault.apex=peak;((TraversalSystem.Anchor)level.getBlockEntity(peak)).root=p;vault.sync();level.destroyBlock(peak,false);
  check(level.getBlockState(p).isAir()&&level.getBlockState(q).isAir(),"Breaking a Traversal Summit also removes both endpoints");
  player.setGameMode(GameType.CREATIVE);
  for(var block:List.of(me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get(),me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get(),me.cryo.zombierool.core.registry.ZRBlocks.GLASS_DEFENSE_DOOR.get(),DefenseWallSystem.MAIN_BLOCK.get(),DefenseWallSystem.DUMMY_BLOCK.get())){
   var state=block.defaultBlockState();var stage=state.getProperties().stream().filter(v->v.getName().equals("stage")).findFirst().orElseThrow();
   @SuppressWarnings("unchecked") var stages=(net.minecraft.world.level.block.state.properties.Property<Integer>)stage;
   state=state.setValue(stages,0);var pos=new BlockPos(492,80,-4);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,state,3);
   for(var mode:List.of(GameType.SURVIVAL,GameType.CREATIVE)){player.setGameMode(mode);check(state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty()==(mode==GameType.CREATIVE&&(block==me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get()||block==me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get())),"Stage-zero "+block+" respects creative passage for "+mode);}
   var probe=ZombieroolModEntities.ZOMBIE.get().create(level);check(state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.of(probe)).isEmpty(),"Stage-zero "+block+" allows an enemy to cross");
  }
  for(int x=442;x<=449;x++)for(int z=2;z<=4;z++)for(int y=80;y<=83;y++)level.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),3);
  player.setGameMode(GameType.SURVIVAL);player.setPos(448.5,82.5,3.5);player.baseTick();
  var waterZombie=ZombieroolModEntities.ZOMBIE.get().create(level);waterZombie.setPos(443.5,80,3.5);waterZombie.setEmergenceTicks(0);waterZombie.setNoAi(false);level.addFreshEntity(waterZombie);waterZombie.baseTick();
  check(player.isInWater()&&waterZombie.isInWater(),"Water pursuit fixture has a swimming player and a submerged zombie");
  check(RouteNavigation.pursuitPosition(player).y==80,"Swimming player's pursuit target is projected to the pool bed");
  var underwater=waterZombie.getNavigation().createPath(player,0);
  check(RouteNavigation.reachesPlayer(underwater,player)&&underwater.getEndNode().y==80,"Submerged zombie retains a complete bottom route to the swimming player");waterZombie.discard();player.setGameMode(GameType.CREATIVE);
  System.out.println("26.7 NAVIGATION REVIEW PASS: "+assertions+" assertions");
 }
}
