package local.atmospherevisual;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class HellhoundFogVisualTest {
    boolean requested,done;int age,checks;volatile String failure;net.minecraft.nbt.CompoundTag original;
    public HellhoundFogVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> task){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();}});}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+name+".png"));if(name.equals("hellhound-fog")){int count=0;long r=0,g=0,b=0;for(int y=40;y<220;y++)for(int x=200;x<1000;x++){int c=image.getPixelRGBA(x,y);r+=c&255;g+=c>>8&255;b+=c>>16&255;count++;}check(r/count>165&&g/count>165&&b/count>165,"Hellhound round renders a white fog background instead of the custom sky");}}}
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}if(mc.level==null||mc.player==null)return;age++;
        if(age==30)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(p.serverLevel(),100.5,84,106.5,180,-15);});
        if(age==50){ClientEnvironmentEffects.setFogPreset("none",0,0,0,.5f,512,false);original=MapAtmosphere.preset("die_rise");MapAtmosphereClient.receive("state",original);WaveManager.setClientGameRunning(true);WaveManager.setClientSpecialWave(false);}
        if(age==65){check(!ClientEnvironmentEffects.hasMapFog(),"Custom sky is initially visible when map fog is disabled");shot("sky-before");WaveManager.setClientSpecialWave(true);}
        if(age==80){check(ClientEnvironmentEffects.hellhoundFog()&&ClientEnvironmentEffects.hasMapFog(),"Hellhound override enables fog even when map fog is none");check(!ClientEnvironmentEffects.enableFog&&ClientEnvironmentEffects.currentFogPreset.equals("none"),"Temporary fog does not overwrite the map fog configuration");shot("hellhound-fog");WaveManager.setClientSpecialWave(false);}
        if(age==95){check(!ClientEnvironmentEffects.hasMapFog()&&MapAtmosphereClient.settings().equals(MapAtmosphere.sanitize(original)),"End of the round restores the same atmosphere preset and disabled map fog");shot("sky-restored");WaveManager.setClientSpecialWave(true);float health=mc.player.getHealth();mc.player.setHealth(0);check(!ClientEnvironmentEffects.hellhoundFog(),"Death immediately disables the special fog override");mc.player.setHealth(health);server(p->p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR));}
        if(age==110){check(!ClientEnvironmentEffects.hellhoundFog(),"Spectators do not retain the Hellhound fog");server(p->p.setGameMode(net.minecraft.world.level.GameType.CREATIVE));}
        if(age==125){WaveManager.setClientGameRunning(false);check(!ClientEnvironmentEffects.hellhoundFog()&&!ClientEnvironmentEffects.hasMapFog(),"Match end restores normal fog even if a special-wave flag is stale");ClientEnvironmentEffects.setFogPreset("custom",.2f,.3f,.4f,2,60,true);WaveManager.setClientGameRunning(true);WaveManager.setClientSpecialWave(true);check(ClientEnvironmentEffects.hellhoundFog(),"Hellhound override takes priority over outdoors-only custom fog");WaveManager.setClientSpecialWave(false);check(ClientEnvironmentEffects.enableFog&&ClientEnvironmentEffects.fogOutdoorsOnly&&ClientEnvironmentEffects.fogFarPlane==60&&ClientEnvironmentEffects.fogColorGreen==.3f,"Original custom color, distance and outdoors-only option return unchanged");WaveManager.setClientGameRunning(false);ClientEnvironmentEffects.setFogPreset("none",0,0,0,.5f,512,false);done=true;System.out.println("ATMOSPHERE VISUAL PASS: Hellhound fog "+checks+" checks");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
