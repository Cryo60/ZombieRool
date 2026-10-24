package local.atmospherevisual;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
@Mod("atmospherevisual")
public final class VehicleMovementVisualTest {
 boolean requested,done;int age,checks,samples;volatile String failure;volatile double wall;final BlockPos pos=new BlockPos(0,65,15);final String[] vehicles={"tank","plane","truck","helicopter"};
 public VehicleMovementVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
 void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
 void release(){var o=Minecraft.getInstance().options;o.keyUp.setDown(false);o.keyJump.setDown(false);o.keyShift.setDown(false);}
 void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
  if(failure!=null)throw new AssertionError(failure);if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
  if(age==30)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);for(int x=-18;x<=18;x++)for(int z=-6;z<=38;z++){l.setBlock(new BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);for(int y=65;y<78;y++)l.setBlock(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}l.setDayTime(6000);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());});
  for(int i=0;i<vehicles.length;i++){int start=60+i*160;String vehicle=vehicles[i];
   if(age==start){release();server(p->{var l=p.serverLevel();l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);l.setBlock(pos,MapDeviceSystem.ALL_TYPES.get("decor_"+vehicle).get().defaultBlockState(),3);var d=(MapDeviceSystem.Device)l.getBlockEntity(pos);var n=d.config();n.putBoolean("requires_power",false);d.configure(n);d.onLoad();d.setActive(true);var geometry=VehicleDecorPhysics.geometry(l,pos);var edge=geometry.movement().stream().min(java.util.Comparator.comparingDouble(b->b.minX)).orElseThrow();wall=edge.minX;p.teleportTo(l,edge.minX-.4,65.01,(edge.minZ+edge.maxZ)*.5,-90,0);});}
   if(age==start+15){mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(true);}
   if(age>=start+20&&age<start+135){samples++;if(mc.player.getPose()==net.minecraft.world.entity.Pose.SWIMMING||mc.player.isFallFlying())throw new AssertionError(vehicle+" forced a swimming/flying pose");if(!VehicleDecorPhysics.collisions(mc.level,mc.player.getBoundingBox().deflate(.025)).isEmpty())throw new AssertionError(vehicle+" embedded client player");}
   if(age==start+100){mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(true);}
   if(age==start+135){release();check(mc.player.getX()<wall-.28,vehicle+" blocks repeated jumps and crouching without crossing its side");check(mc.player.getY()<65.2,vehicle+" leaves the player on the floor");try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),vehicle+"-blocked.png"));}}
  }
  if(age==60+vehicles.length*160){release();check(samples>=400,"Client movement sampled across all four vehicle families");done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" vehicle movement checks / "+samples+" live movement ticks");mc.stop();}
 }catch(Throwable t){release();done=true;t.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
