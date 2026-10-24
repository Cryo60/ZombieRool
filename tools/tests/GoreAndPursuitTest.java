package local.motiontests;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.GameType;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import java.util.*;
@Mod("motiontests")
public final class GoreAndPursuitTest {
 boolean requested,done,started,setupQueued;int age;long start;ServerPlayer player;ServerLevel level;
 final List<ZombieEntity> mobs=new ArrayList<>();final List<Long> samples=new ArrayList<>();jdk.jfr.Recording recording;
 public GoreAndPursuitTest(){MinecraftForge.EVENT_BUS.addListener(this::client);MinecraftForge.EVENT_BUS.addListener(this::server);}
 void setup(ServerPlayer p)throws Exception{
  player=p;level=p.serverLevel();level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
  for(var e:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
  var path=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:path"));
  for(int x=128;x<176;x++)for(int z=128;z<176;z++){level.setBlock(new BlockPos(x,63,z),path.defaultBlockState(),2);level.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);for(int y=65;y<70;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  for(int z=128;z<171;z++)for(int y=65;y<69;y++)level.setBlock(new BlockPos(152,y,z),Blocks.STONE.defaultBlockState(),2);
  p.setGameMode(GameType.ADVENTURE);p.teleportTo(level,166.5,65,138.5,0,0);p.setOnGround(true);
  for(int i=0;i<30;i++){var m=ZombieroolModEntities.ZOMBIE.get().create(level);m.setPos(134.5+(i%5)*1.1,65,133.5+(i/5)*1.1);m.setEmergenceTicks(0);m.setOnGround(true);m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.27);level.addFreshEntity(m);mobs.add(m);}
  started=true;System.out.println("MOTION SETUP 30 active chasers with a long wall detour");
 }
 void stairs()throws Exception{
  for(int x=132;x<=143;x++)for(int z=172;z<=174;z++)for(int y=65;y<=64+Math.min(9,x-132);y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2);
  player.teleportTo(level,142.5,74,173.5,0,0);player.setOnGround(true);
  var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(132.5,65,173.5);zombie.setOnGround(true);zombie.setEmergenceTicks(0);level.addFreshEntity(zombie);
  for(int i=0;i<260;i++){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+1);zombie.tickCount++;zombie.tick();player.invulnerableTime=1000;}
  if(zombie.getY()<73.5||zombie.getX()<140)throw new AssertionError("Pursuit lost the stair route: "+zombie.position());zombie.discard();
  System.out.println("MOTION STAIRS PASS");
 }
 void gore()throws Exception{
  for(var mob:mobs)mob.discard();
  var piece=ZombieroolModEntities.GORE_PIECE.get().create(level);piece.setPos(152.5,66,140.5);level.addFreshEntity(piece);piece.setDeltaMovement(.2,0,0);
  for(int i=0;i<120&&!piece.isRemoved();i++)piece.tick();
  System.out.println("MOTION GORE embedded="+!level.noCollision(piece,piece.getBoundingBox())+" stuck="+piece.isStuck()+" removed="+piece.isRemoved()+" position="+piece.position());
  if(Boolean.getBoolean("zr.motion.fixed")&&!piece.isRemoved()&&!me.cryo.zombierool.util.GoreCollision.clear(piece,piece.position()))throw new AssertionError("Gore remains embedded and spinning");
  piece.discard();
  if(Boolean.getBoolean("zr.motion.fixed")){
   var source=ZombieroolModEntities.ZOMBIE.get().create(level);source.setPos(151.7,65,141.5);
   for(int i=0;i<48;i++)GorePieceEntity.spawn(level,source,GorePieceEntity.MEAT,0,"");
   for(var entity:com.google.common.collect.Lists.newArrayList(level.getAllEntities()))if(entity instanceof GorePieceEntity gib){if(!me.cryo.zombierool.util.GoreCollision.clear(gib,gib.position()))throw new AssertionError("New gib spawned inside wall");gib.discard();}
   var resting=ZombieroolModEntities.GORE_PIECE.get().create(level);resting.setPos(148.5,65,140.5);level.addFreshEntity(resting);resting.tick();if(!resting.isStuck())throw new AssertionError("Gib did not settle on floor");level.setBlock(new BlockPos(148,64,140),Blocks.AIR.defaultBlockState(),2);for(int i=0;i<10;i++)resting.tick();if(resting.getY()>=64.9)throw new AssertionError("Gib floated after removing support");resting.discard();
   if(me.cryo.zombierool.client.career.CareerUnlockables.CAMOS.containsKey("camo_resprune"))throw new AssertionError("Removed camo still registered");
   System.out.println("MOTION GORE PASS: embedded recovery, wall-adjacent spawns, support removal, Resprune removal");
  }
 }
 void server(TickEvent.ServerTickEvent e){
  if(!started||done)return;
  if(e.phase==TickEvent.Phase.START){start=System.nanoTime();return;}
  try{
   age++;player.invulnerableTime=1000;
   if(age==60){recording=new jdk.jfr.Recording();recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(10));recording.start();}
   if(age>60)samples.add(System.nanoTime()-start);
   if(age%40==0)player.teleportTo(level,166.5,65,age%80==0?138.5:160.5,0,0);
   if(age==240){recording.stop();recording.dump(java.nio.file.Path.of(System.getProperty("motion.output"),"pursuit.jfr"));recording.close();java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("motion.output"),"ticks-ms.txt"),samples.stream().map(n->Double.toString(n/1e6)).toList());
                var steady=new ArrayList<Long>(samples.subList(Math.min(60,samples.size()-1),samples.size()));Collections.sort(steady);System.out.println("MOTION STEADY mean_ms="+steady.stream().mapToLong(Long::longValue).average().orElse(0)/1e6+" p95_ms="+steady.get((int)(steady.size()*.95))/1e6+" max_ms="+steady.get(steady.size()-1)/1e6);
                Collections.sort(samples);double mean=samples.stream().mapToLong(Long::longValue).average().orElse(0)/1e6;System.out.println("MOTION SERVER mean_ms="+mean+" p95_ms="+samples.get((int)(samples.size()*.95))/1e6+" max_ms="+samples.get(samples.size()-1)/1e6+" progress="+mobs.stream().filter(m->m.getX()>145).count());gore();if(Boolean.getBoolean("zr.motion.fixed"))stairs();done=true;System.out.println("MOTION PASS");Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
  }catch(Throwable error){done=true;error.printStackTrace();Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
 }
 void client(TickEvent.ClientTickEvent e){
  if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"FeedbackFixture");return;}
  if(!setupQueued&&mc.level!=null&&mc.getSingleplayerServer()!=null){setupQueued=true;mc.getSingleplayerServer().execute(()->{try{setup(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Exception error){error.printStackTrace();done=true;mc.execute(mc::stop);}});}
 }
}
