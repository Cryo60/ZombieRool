package local.atmospherevisual;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
@Mod("atmospherevisual")
public final class SkyObjectsVisualTest {
 boolean requested,done;int age,checks;volatile String failure;String[] ids={"nuketown_zombies","moon","origins","shangri_la","die_rise","custom"};
 public SkyObjectsVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+label);}
 void benchmark(){
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().getId();
  for(String id:new String[]{"nuketown_zombies","origins"}){var n=scene(id);for(int j=0;j<12;j++)SkyObjectsRenderer.geometryCount(n,j*.016f);long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();int faces=0;for(int j=0;j<60;j++)faces=SkyObjectsRenderer.geometryCount(n,j*.016f);System.out.println("SKY BENCHMARK "+id+": faces="+faces+" ns/frame="+(System.nanoTime()-start)/60+" bytes/frame="+(bean.getThreadAllocatedBytes(thread)-bytes)/60);}
 }
 void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){if(image.getWidth()<640||image.getHeight()<360)throw new AssertionError("Invalid offscreen capture size "+image.getWidth()+"x"+image.getHeight());image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
 void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();t.printStackTrace();}});}
 CompoundTag scene(String id){if(!id.equals("custom"))return MapAtmosphere.preset(id);var n=MapAtmosphere.preset("moon");var rows=new ListTag();rows.add(SkyObjects.importModel("{\"name\":\"Sky beacon\",\"boxes\":[{\"sx\":1,\"sy\":2,\"sz\":1,\"r\":0.2,\"g\":0.8,\"b\":1},{\"sx\":2,\"sy\":0.4,\"sz\":0.4,\"r\":1,\"g\":0.5,\"b\":0.1}]}","beacon"));n.put("skyObjects",rows);return n;}
 void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
  if(failure!=null)throw new AssertionError(failure);
  if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
  if(mc.level==null||mc.player==null||mc.isPaused())return;
  if(mc.getMainRenderTarget().width<640||mc.getMainRenderTarget().height<360){mc.getWindow().setWidth(1280);mc.getWindow().setHeight(720);mc.resizeDisplay();}
  age++;
  if(age==35){check(OriginalSkyEffects.sourceElements("cloud")==23,"Original nuclear effect has 23 source elements");check(OriginalSkyEffects.sourceElements("eclipse")==15,"Original eclipse has 15 source elements");check(OriginalSkyEffects.aircraftFrames()==350,"Original aircraft animation loads all 350 frames");mc.options.hideGui=true;server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(p.serverLevel(),.5,72,28.5,0,-30);});}
  if(age==105){var n=scene("nuketown_zombies");var cloud=n.getList("skyObjects",10).getCompound(0);cloud.putDouble("distance",65);MapAtmosphereClient.receive("preview",n);mc.player.setXRot(-48);}
  if(age==114)benchmark();
  if(age==115)shot("nuketown_zombies-under-crown");
  if(age==125)shot("nuketown_zombies-under-crown-animated");
  for(int i=0;i<ids.length;i++){int start=55+i*75;String id=ids[i];
   if(age==start){var n=scene(id);var first=n.getList("skyObjects",10).getCompound(0);mc.player.setYRot(-(float)first.getDouble("yaw"));mc.player.setYHeadRot(mc.player.getYRot());mc.player.setXRot(-(float)first.getDouble("elevation"));ClientEnvironmentEffects.setFogPreset("none",0,0,0,.5f,512);var off=n.copy();off.put("skyObjects",new ListTag());MapAtmosphereClient.receive("preview",off);check(SkyObjectsRenderer.geometryCount(n,5)>0,id+" builds real 3D geometry");}
   if(age==start+5)shot(id+"-baseline");
   if(age==start+8)MapAtmosphereClient.receive("preview",scene(id));
   if(age==start+15){check(SkyObjectsRenderer.shader!=null,"Sky geometry shader loaded for "+id);shot(id);}
   if(age==start+45)shot(id+"-animated");
  }
  if(age==510){var n=scene("moon");var first=n.getList("skyObjects",10).getCompound(0);first.putDouble("yaw",0);first.putDouble("elevation",25);MapAtmosphereClient.receive("preview",n);mc.player.setYRot(0);mc.player.setXRot(-25);server(p->{p.teleportTo(p.serverLevel(),.5,72,28.5,0,-25);for(var at:net.minecraft.core.BlockPos.betweenClosed(-2,72,34,2,80,34))p.serverLevel().setBlock(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);});}
  if(age==530)shot("occluded-by-wall");
  if(age==535)ClientEnvironmentEffects.setFogPreset("white",1,1,1,1,12);
  if(age==545){check(ClientEnvironmentEffects.hasMapFog(),"Map fog hides sky scenery");shot("fog-no-scenery");}
  if(age==550){ClientEnvironmentEffects.setFogPreset("none",0,0,0,.5f,512);mc.options.hideGui=false;mc.setScreen(new SkyObjectsScreen(null,scene("moon"),rows->{}));}
  if(age==560){check(mc.screen instanceof SkyObjectsScreen,"Map-maker sky object editor opens");shot("sky-objects-editor");mc.screen.onClose();}
  if(age==575||age==605){float flightAge=age==575?2:6;var n=scene("die_rise");var first=n.getList("skyObjects",10).getCompound(0);var only=new ListTag();only.add(first);n.put("skyObjects",only);first.putDouble("phase",flightAge*20);first.putDouble("speed",0);var flight=SkyObjects.meteorFlight(flightAge,first.getString("id"));mc.player.setYRot(-(float)(first.getDouble("yaw")+flight.azimuthOffset()));mc.player.setXRot(-(float)(first.getDouble("elevation")+flight.elevationOffset()));MapAtmosphereClient.receive("preview",n);mc.options.hideGui=true;if(age==575){float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();server(p->p.teleportTo(p.serverLevel(),.5,100,1000.5,yaw,pitch));}}
  if(age==590)shot("meteor-high");
  if(age==620)shot("meteor-horizon");
  if(age==630){MapAtmosphereClient.receive("preview",MapAtmosphere.defaults());check(MapAtmosphereClient.settings().getList("skyObjects",10).isEmpty(),"Disabling atmosphere clears the scene");}
  if(age==635)server(p->{MapAtmosphere.get(p.serverLevel()).configure(p.serverLevel(),MapAtmosphere.preset("nuketown_zombies"));MapAtmosphere.applyPlayerPreset(p,"nuketown_zombies");});
  if(age==645){check(MapAtmosphereClient.settings().getList("skyObjects",10).getCompound(0).getString("type").equals("nuclear_cloud"),"Player receives nuclear cloud with the preset");server(p->{var departure=new CompoundTag();departure.putString("arrivalAtmospherePreset","moon");me.cryo.zombierool.block.system.TeleporterTravel.applyArrivalAtmosphere(p,departure,new CompoundTag());});}
  if(age==655){var rows=MapAtmosphereClient.settings().getList("skyObjects",10);check(rows.size()==1&&rows.getCompound(0).getString("type").equals("earth"),"Teleporter replaces nuclear cloud with Moon scene on the actual client");server(p->{var departure=new CompoundTag();departure.putString("arrivalAtmospherePreset","none");me.cryo.zombierool.block.system.TeleporterTravel.applyArrivalAtmosphere(p,departure,new CompoundTag());});}
  if(age==665){check(MapAtmosphereClient.settings().getList("skyObjects",10).isEmpty(),"Teleporter removes the scene with None preset");bloodCache();done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" sky object checks");mc.stop();}
 }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
 void bloodCache()throws Exception{
  var mc=Minecraft.getInstance();var pos=mc.player.blockPosition().offset(1,-1,1);mc.level.setBlock(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
  var origin=net.minecraft.world.phys.Vec3.atLowerCornerOf(pos).add(.5,1,.5);var pieces=me.cryo.zombierool.util.BloodSurfaceGeometry.clip(mc.level,origin,net.minecraft.core.Direction.UP,.35f,0,1);
  check(!pieces.isEmpty(),"Blood cache fixture has actual support geometry");
  var stamps=java.util.List.of(new BloodSurfaceRenderer.Stamp(origin,net.minecraft.core.Direction.UP,pieces,0x550000,255));var pose=new com.mojang.blaze3d.vertex.PoseStack();var buffers=mc.renderBuffers().bufferSource();BloodSurfaceRenderer.clear();BloodSurfaceRenderer.render(stamps,pose,buffers);
  var groups=BloodSurfaceRenderer.class.getDeclaredField("previousGroups");groups.setAccessible(true);var first=groups.get(null);BloodSurfaceRenderer.render(stamps,pose,buffers);check(groups.get(null)==first,"Unchanged blood reuses grouped geometry and textures");
  mc.level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);BloodSurfaceRenderer.render(stamps,pose,buffers);check(((java.util.Map<?,?>)groups.get(null)).isEmpty(),"Removing blood support immediately invalidates cached geometry");BloodSurfaceRenderer.clear();
 }
}
