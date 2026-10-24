package local.skingalleryclient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;
@Mod("skingalleryclient") public final class LunarFinalVisualCheck {
    private boolean requested,done;private int ticks;
    public LunarFinalVisualCheck(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(70);org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"LunarFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;ticks++;
        try{
            if(ticks==80)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),15.5,65,77.2,0,0);});
            if(ticks==140){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("D:/Dev/ZombieRool/build/zombie-presets/lunar-penitencier-3.png"));}System.out.println("LUNAR FINAL VISUAL PASS");done=true;mc.stop();}
        }catch(Throwable t){t.printStackTrace();done=true;mc.stop();}
    }
}
