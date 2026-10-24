package local.optionaltest;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
@Mod("optionaltest")
public final class OptionalClientSmokeTest {
 int age; boolean done;
 public OptionalClientSmokeTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 void tick(TickEvent.ClientTickEvent e){
  if(done||e.phase!=TickEvent.Phase.END)return;
  var mc=Minecraft.getInstance();
  org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
  if(mc.getOverlay()!=null||++age<30)return;
  done=true;
  try {
   if(net.minecraftforge.fml.ModList.get().isLoaded("tacz"))throw new AssertionError("TaCZ must be absent");
   if(!net.minecraftforge.fml.ModLoader.isLoadingStateValid())throw new AssertionError("Mod loading error screen");
   var data=new me.cryo.zombierool.config.WorldConfig().save(new net.minecraft.nbt.CompoundTag());
   var screen=new me.cryo.zombierool.client.gui.WorldConfigMenuScreen(new me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket(data,false,1));
   mc.setScreen(screen);
   Class<?> tab=java.util.Arrays.stream(screen.getClass().getDeclaredClasses()).filter(c->c.getSimpleName().equals("Tab")).findFirst().orElseThrow();
   var open=screen.getClass().getDeclaredMethod("openTab",tab);open.setAccessible(true);
   for(Object value:tab.getEnumConstants()) {open.invoke(screen,value);System.out.println("OPTIONAL CLIENT TAB PASS: "+value);}
   System.out.println("OPTIONAL CLIENT PASS: startup and all 12 map config sections without TaCZ");
  }catch(Throwable t){t.printStackTrace();System.out.println("OPTIONAL CLIENT FAILED: "+t);}
  mc.stop();
 }
}
