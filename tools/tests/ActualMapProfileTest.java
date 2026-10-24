package local.atmospherevisual;

import java.util.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.*;

/** Read-only source-world profiling: the runner always opens a disposable copy. */
@Mod("atmospherevisual")
public final class ActualMapProfileTest {
    boolean requested,done;int age,group;volatile String failure;CompoundTag original;
    final Map<BlockPos,CompoundTag> devices=new HashMap<>();
    jdk.jfr.Recording recording;
    record Timer(int start,int end,int group,long cpu){}
    final ArrayDeque<Timer> pending=new ArrayDeque<>();Timer timer;long lastFrame;int lastGroup;
    final Map<Integer,List<Double>> gpu=new HashMap<>(),cpu=new HashMap<>(),wall=new HashMap<>();
    public ActualMapProfileTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::end);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> work){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{work.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();t.printStackTrace();}});}
    void scenery(boolean enabled){server(p->{for(var d:MapDeviceSystem.devices(p.serverLevel()))if(AmbientScenery.kind(d.kind())){devices.putIfAbsent(d.getBlockPos(),d.config());var tag=devices.get(d.getBlockPos()).copy();tag.putBoolean("enabled",enabled&&tag.getBoolean("enabled"));d.configure(tag);}});}
    void shaders(boolean enabled){net.irisshaders.iris.api.v0.IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);}
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(mc.getMainRenderTarget().width!=1920){mc.getWindow().setWidth(1920);mc.getWindow().setHeight(1080);mc.resizeDisplay();}
        if(age==25){
            // Restore the saved player's viewpoint even when the test profile UUID differs.
            server(p->{try(var input=java.nio.file.Files.newInputStream(java.nio.file.Path.of(System.getProperty("atmosphere.actualWorld"),"level.dat"))){var root=net.minecraft.nbt.NbtIo.readCompressed(input).getCompound("Data").getCompound("Player");var pos=root.getList("Pos",6);var rotation=root.getList("Rotation",5);if(pos.size()==3)p.teleportTo(p.serverLevel(),pos.getDouble(0),pos.getDouble(1),pos.getDouble(2),rotation.size()==2?rotation.getFloat(0):p.getYRot(),rotation.size()==2?rotation.getFloat(1):p.getXRot());p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);}catch(java.io.IOException error){throw new RuntimeException(error);}});
        }
        if(age==160){var f=MapAtmosphereClient.class.getDeclaredField("settings");f.setAccessible(true);original=((CompoundTag)f.get(null)).copy();recording=new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));recording.setName("ZombieRool actual map rendering");recording.start();System.out.println("ACTUAL MAP GPU "+GL11.glGetString(GL11.GL_RENDERER)+" renderChunks="+mc.options.renderDistance().get()+" simulation="+mc.options.simulationDistance().get()+" viewpoint="+mc.player.position()+" atmosphere="+original.getString("presetId"));}
        if(age==190){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),"actual-map-photon.png"));}}
        if(age==200)group=1;
        if(age==280){group=0;MapAtmosphereClient.receive("preview",MapAtmosphere.defaults());scenery(false);}
        if(age==340)group=2;
        if(age==420){group=0;shaders(false);}
        if(age==480)group=3;
        if(age==560){group=0;MapAtmosphereClient.receive("preview",original);scenery(true);}
        if(age==620)group=4;
        if(age==700){group=0;collect();report();recording.stop();recording.dump(java.nio.file.Path.of(System.getProperty("atmosphere.output"),"actual-map.jfr"));recording.close();done=true;System.out.println("ATMOSPHERE VISUAL PASS: actual-map renderer profile collected without modifying the source world");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
    void start(TickEvent.RenderTickEvent e){if(done||e.phase!=TickEvent.Phase.START)return;collect();long now=System.nanoTime();if(group>0&&group==lastGroup&&lastFrame!=0)wall.computeIfAbsent(group,k->new ArrayList<>()).add((now-lastFrame)/1e6);lastFrame=now;lastGroup=group;if(group==0||timer!=null||pending.size()>7)return;timer=new Timer(GL15.glGenQueries(),GL15.glGenQueries(),group,now);GL33.glQueryCounter(timer.start,GL33.GL_TIMESTAMP);}
    void end(net.minecraftforge.client.event.RenderGuiEvent.Pre e){if(timer==null)return;GL33.glQueryCounter(timer.end,GL33.GL_TIMESTAMP);cpu.computeIfAbsent(timer.group,k->new ArrayList<>()).add((System.nanoTime()-timer.cpu)/1e6);pending.add(timer);timer=null;}
    void collect(){while(!pending.isEmpty()&&GL15.glGetQueryObjecti(pending.peek().end,GL15.GL_QUERY_RESULT_AVAILABLE)!=0){var q=pending.remove();double ms=(GL33.glGetQueryObjectui64(q.end,GL15.GL_QUERY_RESULT)-GL33.glGetQueryObjectui64(q.start,GL15.GL_QUERY_RESULT))/1e6;gpu.computeIfAbsent(q.group,k->new ArrayList<>()).add(ms);GL15.glDeleteQueries(q.start);GL15.glDeleteQueries(q.end);}}
    double percentile(List<Double> rows,double fraction){rows.sort(Double::compare);return rows.get(Math.min(rows.size()-1,(int)(rows.size()*fraction)));}
    void report(){String[] names={"","photon-full-map","photon-no-atmosphere-or-scenery","native-map","native-full-map"};for(int i=1;i<=4;i++){var g=gpu.get(i);if(g==null||g.size()<10)throw new AssertionError("Missing actual map samples for "+names[i]);System.out.println("ACTUAL MAP PROFILE "+names[i]+" samples="+g.size()+" gpu_ms="+percentile(g,.5)+" gpu_p95="+percentile(g,.95)+" cpu_ms="+percentile(cpu.get(i),.5)+" frame_ms="+percentile(wall.get(i),.5)+" ambientParticles="+AmbientParticles.activeCount());}}
}
