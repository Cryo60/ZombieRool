package local.skingalleryclient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;
@Mod("skingalleryclient") public final class SkinGalleryClientCapture {
 private boolean requested,done;private int ticks,shot;
 private final double[][] positions={{3.5,65,.2},{9.5,65,7.2},{3.5,65,7.2},{3.5,65,91.2},{3.5,65,98.2}};
 private final String[] names={"soldat-casque","five-militaire","five-scientifique","origins-casque","croise-casque"};
 public SkinGalleryClientCapture(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 private void tick(TickEvent.ClientTickEvent e){
  if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  if(!requested){requested=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(70);org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"SkinGalleryFixture");return;}
  if(mc.level==null||mc.player==null||mc.isPaused())return;ticks++;
  try{
   if(ticks==80){var p=positions[shot];mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.teleportTo(mc.getSingleplayerServer().overworld(),p[0],p[1],p[2],0,0);});}
   if(ticks==150){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("D:/Dev/ZombieRool/build/zombie-presets/ingame-"+names[shot]+".png"));}System.out.println("SKIN GALLERY FRAME: "+names[shot]);shot++;ticks=50;if(shot==positions.length){done=true;System.out.println("SKIN GALLERY CLIENT PASS");mc.stop();}}
  }catch(Throwable x){x.printStackTrace();done=true;mc.stop();}
 }
}
