package local.hotfixtest;
import java.util.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
public final class Oct12GameplayTest {
 private static final class DamageProbe extends net.minecraftforge.common.util.FakePlayer {
  DamageProbe(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"InfernoProbe"));}
  @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
 }
 private static int checks;
 private static boolean illuminated(ServerLevel level,UUID id)throws Exception{
  var field=TeleporterTravel.class.getDeclaredField("DEPARTURES");field.setAccessible(true);
  var departures=(java.util.Map<?,?>)field.get(null);var list=(java.util.List<?>)departures.get(level);
  for(var departure:list){var method=departure.getClass().getDeclaredMethod("illuminated");method.setAccessible(true);if(((java.util.Set<?>)method.invoke(departure)).contains(id))return true;}
  return false;
 }
 private static void fiestaTraversal(ServerLevel level,ServerPlayer player)throws Exception{
  var motionTick=TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);motionTick.setAccessible(true);
  var chief=ZombieroolModEntities.MR_CHIEF.get().create(level);chief.configure(new me.cryo.zombierool.config.MrChiefConfig(),true);chief.setPos(1040,80,0);level.addFreshEntity(chief);
  var riders=new ArrayList<ZombieEntity>();
  try{
   for(var block:List.of(TraversalSystem.ZIPLINE.get(),TraversalSystem.WALL.get())){
    var pos=new BlockPos(1042+riders.size()*8,85,0);loadFixture(level,pos);level.setBlock(pos,block.defaultBlockState(),3);var anchor=(TraversalSystem.Anchor)level.getBlockEntity(pos);
    anchor.exit=pos.offset(5,-4,0);anchor.duration=12;
    var mob=zombie(level,anchor.start());mob.setEmergenceTicks(0);mob.getPersistentData().remove("zr_emerge_no_ai");mob.setNoAi(false);riders.add(mob);
    TraversalMotion.start(mob,new TraversalSystem.Route(anchor,false));check(TraversalMotion.active(mob)&&mob.isNoAi(),"Navigation animation takes temporary ownership of AI before Mr. Chief");
   }
   motionTick.invoke(null,level);chief.die(level.damageSources().playerAttack(player));check(chief.isPartying(),"Real Mr. Chief kill begins the party freeze");
   var positions=riders.stream().map(ZombieEntity::position).toList();
   for(int i=0;i<24;i++){for(var mob:riders)FiestaFreeze.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));motionTick.invoke(null,level);}
   for(int i=0;i<riders.size();i++){var mob=riders.get(i);check(mob.position().equals(positions.get(i))&&TraversalMotion.packet(TraversalMotion.flight(mob)).elapsed()==1,"Party pauses a navigation curve without finishing or moving the frozen rider");}
   chief.discard();
   for(var mob:riders){FiestaFreeze.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));check(TraversalMotion.active(mob)&&mob.isNoAi(),"Party release preserves the active curve's temporary AI lock");}
   for(int i=0;i<12;i++)motionTick.invoke(null,level);
   for(var mob:riders)check(!TraversalMotion.active(mob)&&!mob.isNoAi()&&!mob.isNoGravity()&&!mob.noPhysics,"After Mr. Chief, navigation completes and restores live zombie AI and gravity");
  }finally{chief.discard();for(var mob:riders){TraversalMotion.cancel(mob);mob.discard();FiestaFreeze.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));}}
 }
 private static void check(boolean value,String description){if(!value)throw new AssertionError(description);checks++;System.out.println("26.7 CHECK: "+description+" = true");}
 private static void loadFixture(ServerLevel level,BlockPos position){
  level.getChunkAt(position);
  try{var field=ServerLevel.class.getDeclaredField("entityManager");field.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)field.get(level);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(position),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
 }
 private static ZombieEntity zombie(ServerLevel level,Vec3 position){loadFixture(level,BlockPos.containing(position));var mob=ZombieroolModEntities.ZOMBIE.get().create(level);mob.setPos(position);mob.setNoAi(true);mob.setEmergenceTicks(0);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.getPersistentData().remove("zr_emerge_no_ai");return mob;}
 public static void run(ServerLevel level,ServerPlayer player,ServerPlayer second)throws Exception{
  var old=player.position();var mode=player.gameMode.getGameModeForPlayer();long clock=level.getGameTime();float hp=player.getHealth();
  player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setHealth(player.getMaxHealth());
  level.getChunkAt(new BlockPos(880,80,0));
  var mob=zombie(level,new Vec3(880.5,80,.5));float initial=mob.getHealth();
  check(!mob.hurt(level.damageSources().hotFloor(),1)&&mob.isInferno()&&mob.getHealth()==initial,"Hot floor ignites a zombie without health damage");
  InfernoZombie.ignite(mob);double speed=mob.getAttributeValue(Attributes.MOVEMENT_SPEED);InfernoZombie.ignite(mob);
  check(mob.isSuperSprinter()&&mob.getAttributeValue(Attributes.MOVEMENT_SPEED)==speed&&speed>.23,"Inferno speed is permanent and its modifier never stacks");
  var saved=mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());var restored=ZombieroolModEntities.ZOMBIE.get().create(level);restored.load(saved);check(restored.isInferno(),"Inferno state survives saving and loading");restored.discard();
  check(InfernoZombie.hot(Blocks.MAGMA_BLOCK.defaultBlockState())&&InfernoZombie.hot(Blocks.SOUL_FIRE.defaultBlockState())&&!InfernoZombie.hot(Blocks.STONE.defaultBlockState()),"Magma and fire blocks trigger inferno, ordinary ground does not");
  var probe=new DamageProbe(level);level.addNewPlayer(probe);probe.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);probe.getAbilities().invulnerable=false;probe.setInvulnerable(false);var shield=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");shield.setAccessible(true);shield.setInt(probe,0);probe.setPos(882,80,.5);probe.invulnerableTime=0;float before=probe.getHealth();mob.hurt(level.damageSources().genericKill(),10000);
  check(!mob.isAlive()&&probe.getHealth()<before,"Inferno death damages nearby survival players");
  float after=probe.getHealth();InfernoZombie.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(mob,level.damageSources().generic()));check(probe.getHealth()==after,"Repeated death processing cannot repeat the inferno explosion");mob.discard();probe.discard();
  for(int x=900;x<=904;x++)for(int z=-2;z<=2;z++)for(int y=79;y<=84;y++)level.setBlock(new BlockPos(x,y,z),y==79?Blocks.STONE.defaultBlockState():Blocks.WATER.defaultBlockState(),2);
  mob=zombie(level,new Vec3(902.5,80,.5));player.setPos(902.5,83,.5);mob.tick();player.tick();
  check(SubmergedMelee.canReach(mob,player),"Submerged zombie can strike a swimmer directly above it");
  level.setBlock(new BlockPos(902,82,0),Blocks.STONE.defaultBlockState(),2);check(!SubmergedMelee.canReach(mob,player),"Upward water reach cannot pass through a solid floor");
  level.setBlock(new BlockPos(902,82,0),Blocks.WATER.defaultBlockState(),2);player.setPos(904.5,83,.5);check(!SubmergedMelee.canReach(mob,player),"Upward water reach does not extend horizontal melee range");mob.discard();
  mob=zombie(level,new Vec3(930,80,0));var human=ZombieroolModEntities.VRIL_HUMAN.get().create(level);human.owner=player.getUUID();human.setPos(934,80,0);human.setNoAi(true);level.addFreshEntity(human);
  check(WonderWeaponEffects.decoy(mob,36)==human,"Nearby humanified zombie attracts pursuit");mob.setTarget(human);
  check(WonderWeaponEffects.decoy(mob,15)==human,"Small player distance changes cannot interrupt the decoy commitment");
  mob.setTarget(player);check(mob.getTarget()==human,"Player target goal cannot overwrite a committed live decoy");
  level.getServer().getWorldData().overworldData().setGameTime(clock+61);check(WonderWeaponEffects.decoy(mob,1)==null,"A much closer player can regain priority after the commitment expires");
  human.discard();mob.discard();level.getServer().getWorldData().overworldData().setGameTime(clock);
  var p=new BlockPos(950,80,0);var q=new BlockPos(962,80,0);
  for(var pos:List.of(p,q)){level.getChunkAt(pos);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);level.setBlock(pos,TeleporterSystem.PLATFORM.get().defaultBlockState().setValue(TeleporterSystem.Platform.PART,4),2);((TeleporterSystem.Pad)level.getBlockEntity(pos)).onLoad();}
  var source=(TeleporterSystem.Pad)level.getBlockEntity(p);var target=(TeleporterSystem.Pad)level.getBlockEntity(q);
  var config=source.config.copy();config.putBoolean("requiresPower",false);config.putBoolean("requiresLink",false);config.putString("destination",target.config.getString("id"));config.putInt("cost",0);config.putInt("chargeTime",3);config.putInt("cooldown",30);config.putInt("stay",0);source.configure(config);
  player.setPos(950.5,80.13,.5);check(TeleporterTravel.depart(player,source).isEmpty()&&source.busy,"Teleporter begins its server-controlled charge");check(TeleporterTravel.bossProtected(player),"Charging travelers receive boss protection while on their platform");
  var observerPad=new TeleporterSystem.Pad(p,source.getBlockState());observerPad.load(source.getUpdatePacket().getTag());
  check(observerPad.busy&&observerPad.chargeUntil==source.chargeUntil,"Block update sent to observing clients includes the live electricity interval");
  player.setPos(955,80,.5);check(!TeleporterTravel.bossProtected(player),"Leaving a charging platform immediately removes its boss protection");TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
  check(source.busy&&TeleporterTravel.busy(level,p),"Leaving the platform does not interrupt its charge");
  check(!illuminated(level,player.getUUID()),"Leaving the pad immediately clears only the player's lightning overlay");
  player.setPos(950.5,80.13,.5);TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
  check(illuminated(level,player.getUUID())&&source.busy,"Returning resumes screen lightning without restarting the platform charge");
  level.getServer().getWorldData().overworldData().setGameTime(source.chargeUntil);
  TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
  check(player.position().distanceToSqr(Vec3.atBottomCenterOf(q).add(0,.126,0))<.01,"Returning before the last instant still teleports the player");
  var secondPos=second.position();var secondMode=second.gameMode.getGameModeForPlayer();int score=PointManager.getScore(player);var secondConnection=second.connection;
  try{
   second.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),second){
    @Override public void teleport(double x,double y,double z,float yaw,float pitch){second.setPos(x,y,z);second.setYRot(yaw);second.setXRot(pitch);}
    @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
   };
   second.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);second.setHealth(second.getMaxHealth());second.setPos(950.7,80.13,.5);
   check(TeleporterSystem.validPlayer(second)&&source.onPlatform(second)&&level.players().contains(second)&&level.getServer().getPlayerList().getPlayer(second.getUUID())==second,"Both test players are alive and registered on the platform");
   source.cooldownUntil=0;source.sync();player.getPersistentData().remove("zr_teleporter_lock");player.setPos(950.5,80.13,.5);
   config=source.config.copy();config.putInt("cost",50);source.configure(config);PointManager.setScore(player,100);
   check(TeleporterTravel.depart(player,source).isEmpty()&&PointManager.getScore(player)==50,"An activated departure charges its price once at launch");
   long due=source.chargeUntil;player.setPos(955,80,.5);check(!TeleporterTravel.bossProtected(player),"Leaving a charging platform immediately removes its boss protection");
   TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
   check(source.busy&&source.chargeUntil==due,"A departing payer leaves the other player's full charge intact");
   check(!illuminated(level,player.getUUID())&&illuminated(level,second.getUUID()),"A leaving payer cannot clear the other player's lightning overlay");
   level.getServer().getWorldData().overworldData().setGameTime(due);
   TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
   check(second.position().distanceToSqr(Vec3.atBottomCenterOf(q).add(0,.126,0))<.01&&player.getX()==955,"Only the player still on the platform is transported");
   check(!source.busy&&source.cooldownUntil>due&&PointManager.getScore(player)==50,"Partial departure enters cooldown without charging twice");
   source.cooldownUntil=0;source.sync();player.setPos(950.5,80.13,.5);second.setPos(secondPos);
   check(TeleporterTravel.depart(player,source).isEmpty(),"Empty-platform test starts normally");due=source.chargeUntil;player.setPos(955,80,.5);check(!TeleporterTravel.bossProtected(player),"Leaving a charging platform immediately removes its boss protection");
   TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));check(source.busy,"An empty platform continues charging to its deadline");
   level.getServer().getWorldData().overworldData().setGameTime(due);TeleporterTravel.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
   check(!source.busy&&source.cooldownUntil>due&&player.getX()==955,"Empty departure consumes cooldown without transporting absent players");
  }finally{second.connection=secondConnection;second.setPos(secondPos);second.setGameMode(secondMode);PointManager.setScore(player,score);}
  fiestaTraversal(level,player);
  loadFixture(level,new BlockPos(1000,80,0));player.setPos(1000,80,0);player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ZombieroolModMobEffects.PERKS_EFFECT_ROYAL_BEER.get(),200));
  var knight=ZombieroolModEntities.WHITE_KNIGHT.get().create(level);knight.setPos(1000,80,0);knight.tame(player);knight.setNoAi(true);level.addFreshEntity(knight);
  var distant=zombie(level,new Vec3(1006,80,0));var close=zombie(level,new Vec3(1002,80,0));distant.setTarget(player);
  var guardian=new SpectralGuardianGoal(knight);guardian.tick();check(knight.getTarget()==close,"Spectral guardian chooses the closest comparable threat without enemy-class priority");
  close.discard();knight.tickCount+=10;guardian.tick();check(knight.getTarget()==distant,"Spectral guardian replaces a defeated target with a live threat to its owner");
  distant.setPos(1001.5,80,0);float enemyHp=distant.getHealth();knight.performSweepAttack(distant);check(knight.getSpectralSwing()==18&&distant.getHealth()==enemyHp,"Spectral sweep is visibly telegraphed before causing damage");
  knight.setNoGravity(true);distant.setNoGravity(true);for(int i=0;i<9;i++)knight.tick();check(distant.getHealth()<enemyHp,"Spectral sweep deals damage after its wind-up");
  float knightHp=knight.getHealth();check(!knight.hurt(level.damageSources().playerAttack(player),5)&&knight.getHealth()==knightHp,"Spectral ally remains immune to player friendly fire");
  knight.discard();distant.discard();player.removeEffect(ZombieroolModMobEffects.PERKS_EFFECT_ROYAL_BEER.get());
  var victim=zombie(level,new Vec3(970,80,0));player.setPos(980,80,0);
  MapDeviceSystem.teleporterShock(victim,player);check(!victim.isAlive(),"Teleport charge uses lethal electric-trap damage");victim.discard();
  var inventory=player.getInventory().save(new net.minecraft.nbt.ListTag());
  try{
   player.getInventory().clearContent();IngotSaleDrops.queue(player);
   var crawler=ZombieroolModEntities.CRAWLER.get().create(level);
   check(!IngotSaleDrops.consume(player,crawler),"A crawler kill cannot consume a guaranteed zombie drop");crawler.discard();
   var other=new DamageProbe(level);victim=zombie(level,new Vec3(975,80,0));
   check(!IngotSaleDrops.consume(other,victim),"Another player's kill cannot take the queued ingot");
   victim.hurt(level.damageSources().playerAttack(player),100000);
   check(player.getInventory().countItem(ZombieroolModItems.INGOT_SALE.get())==1,"Next real zombie kill gives exactly one guaranteed Ingot Sale");victim.discard();
   victim=zombie(level,new Vec3(975,80,0));victim.hurt(level.damageSources().playerAttack(player),100000);
   check(player.getInventory().countItem(ZombieroolModItems.INGOT_SALE.get())==1,"Following kills cannot repeat a consumed console request");victim.discard();
   for(int i=0;i<36;i++)player.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
   loadFixture(level,player.blockPosition());IngotSaleDrops.queue(player);victim=zombie(level,new Vec3(975,80,0));victim.hurt(level.damageSources().playerAttack(player),100000);
   var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(2),e->e.getItem().is(ZombieroolModItems.INGOT_SALE.get()));
   check(drops.size()==1&&drops.get(0).getPersistentData().getUUID("zr_drop_owner").equals(player.getUUID()),"Full inventory produces a protected ground drop rather than losing the guaranteed ingot");drops.forEach(net.minecraft.world.entity.Entity::discard);victim.discard();
  }finally{player.getInventory().load(inventory);}
  player.setPos(old.x,old.y,old.z);player.setGameMode(mode);player.setHealth(hp);level.getServer().getWorldData().overworldData().setGameTime(clock);
  System.out.println("OCT12 GAMEPLAY PASS: "+checks+" assertions");
 }
}
