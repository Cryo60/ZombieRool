package local.atmospherevisual;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class VehicleDecorVisualTest {
 boolean requested,done;int age,checks;volatile String failure;final BlockPos pos=new BlockPos(0,65,15);
 record Case(String vehicle,String era,String state,String dca){}
 final java.util.List<Case> cases=new java.util.ArrayList<>();
 public VehicleDecorVisualTest(){for(String vehicle:java.util.List.of("tank","plane","truck","helicopter"))for(String era:vehicle.equals("helicopter")?java.util.List.of("modern"):java.util.List.of("legacy","modern"))for(String state:AmbientScenery.states(vehicle))cases.add(new Case(vehicle,era,state,""));for(String style:java.util.List.of("flak88","light20","quad14","modern"))cases.add(new Case("dca","legacy","normal",style));MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
 void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
 void press(String key){String text=net.minecraft.network.chat.Component.translatable(key).getString();for(var widget:java.util.List.copyOf(Minecraft.getInstance().screen.children()))if(widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(text)){b.onPress();return;}throw new AssertionError("Missing button: "+text);}
 void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
  if(failure!=null)throw new AssertionError(failure);
  if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
  if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
  if(age==30)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);l.setDayTime(6000);for(int x=-18;x<=18;x++)for(int z=-6;z<=38;z++){l.setBlock(new BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);for(int y=65;y<77;y++)if(!l.isEmptyBlock(new BlockPos(x,y,z)))l.setBlock(new BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}p.teleportTo(l,14,73,32,140,22);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());});
  for(int i=0;i<cases.size();i++){int start=60+i*30;var c=cases.get(i);String name=c.vehicle+"-"+c.era+"-"+c.state+c.dca;
   if(age==start)server(p->{var l=p.serverLevel();l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);var block=MapDeviceSystem.ALL_TYPES.get(c.vehicle.equals("dca")?"ambient_dca":"decor_"+c.vehicle).get();l.setBlock(pos,block.defaultBlockState(),3);var d=(MapDeviceSystem.Device)l.getBlockEntity(pos);check(d!=null&&MapDeviceSystem.DEVICE.get().isValid(d.getBlockState()),name+" owns a valid block entity");var n=d.config();n.putBoolean("requires_power",false);n.putBoolean("enabled",true);n.getCompound("scenery").putString("variant",c.state);n.getCompound("scenery").putString("era",c.era);n.getCompound("scenery").putString("dcaModel",c.dca);if(!c.vehicle.equals("dca")){n.getCompound("scenery").putDouble("yaw",25);n.getCompound("scenery").putDouble("pitch",7);n.getCompound("scenery").putDouble("roll",-5);}d.configure(n);var copy=new MapDeviceSystem.Device(pos,d.getBlockState());copy.load(d.saveWithoutMetadata());check(copy.scenery.equals(d.scenery),name+" survives save/reload");});
   if(age==start+22){var d=(MapDeviceSystem.Device)mc.level.getBlockEntity(pos);check(d!=null&&d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE),name+" visible while powered");try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}check(mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation("zombierool","textures/item/decor_"+(c.vehicle.equals("dca")?"tank":c.vehicle)+".png")).isPresent(),name+" has an inventory icon");}
  }
  int finish=60+cases.size()*30;
  if(age==finish)server(p->{p.teleportTo(p.serverLevel(),0,67,20,180,10);p.serverLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);p.serverLevel().setBlock(pos,MapDeviceSystem.ALL_TYPES.get("decor_tank").get().defaultBlockState(),3);var d=(MapDeviceSystem.Device)p.serverLevel().getBlockEntity(pos);MapDevicePackets.open(p,d);});
  if(age==finish+10){check(mc.screen instanceof me.cryo.zombierool.client.gui.AmbientSceneryScreen,"Placed tank opens the decorative settings menu");check(mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractSliderButton).count()>=3,"Yaw pitch and roll have independent sliders");press("gui.zombierool.scenery.variant.normal");press("gui.zombierool.scenery.era.legacy");press("gui.zombierool.device.save");}
  if(age==finish+25)server(p->{var d=(MapDeviceSystem.Device)p.serverLevel().getBlockEntity(pos);check(d.scenery.getString("variant").equals("broken")&&d.scenery.getString("era").equals("modern"),"Menu saves the selected damaged modern vehicle through the permission checked packet");});
  if(age==finish+40){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" vehicle decoration checks");mc.stop();}
 }catch(Throwable e){done=true;e.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+e);mc.stop();}}
}
