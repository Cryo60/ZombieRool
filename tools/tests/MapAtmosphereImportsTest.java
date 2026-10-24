package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.client.gui.*;
import me.cryo.zombierool.client.screens.TeleporterChargeOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class MapAtmosphereImportsTest {
    boolean requested,done;int age,checks;volatile String failure,savedId;String sky,sound;long arrival;
    final BlockPos from=new BlockPos(1500,65,0),to=new BlockPos(1510,65,0);
    public MapAtmosphereImportsTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+label);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){e.printStackTrace();failure=e.toString();}});}
    void button(String label){for(var child:java.util.List.copyOf(Minecraft.getInstance().screen.children()))if(child instanceof Button b&&b.getMessage().getString().equals(label)){b.onPress();return;}throw new AssertionError("Button missing: "+label);}
    void field(String name,Object value)throws Exception{var f=WaveManager.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    long arrived()throws Exception{var f=TeleporterChargeOverlay.class.getDeclaredField("arrived");f.setAccessible(true);return f.getLong(null);}
    void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+name+".png"));}}
    TeleporterSystem.Pad pad(net.minecraft.server.level.ServerLevel l,BlockPos pos,String id,String destination,boolean receiver){l.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);l.setBlock(pos,TeleporterSystem.PLATFORM.get().defaultBlockState(),2);var pad=(TeleporterSystem.Pad)l.getBlockEntity(pos);var n=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);n.putString("id",id);n.putString("destination",destination);n.putString("channel","earth_moon");n.putString("role",receiver?"RECEIVER":"SOURCE");n.putBoolean("requiresPower",false);pad.configure(n);pad.onLoad();return pad;}
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==40)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);MapAtmosphere.get(p.serverLevel()).configure(p.serverLevel(),MapAtmosphere.defaults());});
        if(age==60)MapAtmosphereScreen.request(null);
        if(age==75){boolean copy=false,enabled=false;for(var child:mc.screen.children())if(child instanceof Button b){copy|=b.getMessage().getString().equals("Copier les réglages");enabled|=b.getMessage().getString().equals("Ciel personnalisé");}check(!copy&&!enabled,"Preset page removes redundant copy and custom sky controls");button("Nouveau preset…");}
        if(age==77)for(var child:mc.screen.children())if(child instanceof EditBox b)b.setValue("Imported atmosphere");
        if(age==79)button("Nouveau preset");
        if(age==90)((MapAtmosphereScreen)mc.screen).importFile(java.nio.file.Path.of(System.getProperty("atmosphere.skyInput","D:/Dev/ZombieRool/src/main/resources/assets/zombierool/textures/environment/ashfall_sky.png")),true);
        if(age==130){var n=MapAtmosphereClient.settings();sky=n.getString("skyTexture");check(sky.startsWith("zombierool:world_sky/imports/")&&MapAtmosphereClient.skyIds().contains(sky)&&n.getBoolean("skyEnabled"),"PNG upload is selected and available immediately without a pack reload");if(System.getProperty("atmosphere.skyInput")!=null)check(n.getString("skyLayout").equals("cube")&&n.getString("skyCoordinates").equals("y_up"),"Cross import selects the cube atlas and upright Y-up coordinates automatically");shot("imported-sky");((MapAtmosphereScreen)mc.screen).importFile(java.nio.file.Path.of("D:/Dev/ZombieRool/src/main/resources/assets/zombierool/sounds/environment/ambient_distant_alarm_0.ogg"),false);}
        if(age==170){var n=MapAtmosphereClient.settings();sound=n.getList("sources",10).getCompound(0).getString("sound");check(sound.startsWith("zombierool:custom/imports/")&&DynamicSoundLoader.hasDynamicSound(sound),"Ogg upload is immediately playable and added to ambience sources");shot("imported-audio");button("Terminé");}
        if(age==185)server(p->{var d=MapAtmosphere.get(p.serverLevel());savedId=d.settings().getString("presetId");check(d.availablePresets().contains(savedId)&&d.resolvePreset(savedId).getString("skyTexture").equals(sky),"Imported resources are included in the saved custom preset");var root=p.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("zombierool");check(java.nio.file.Files.exists(root.resolve("assets/skyboxes/"+sky.substring("zombierool:world_sky/".length())+".png"))&&java.nio.file.Files.exists(root.resolve("audio/"+sound.substring("zombierool:".length())+".ogg")),"Imports are stored in the world's zombierool folder for future loads");});
        if(age==190)MapAtmosphereScreen.request(null);
        if(age==205)button("Supprimer ce preset");
        if(age==210)button("Annuler");
        if(age==220)server(p->check(MapAtmosphere.get(p.serverLevel()).availablePresets().contains(savedId),"Cancel discards a pending preset deletion"));
        if(age==225)MapAtmosphereScreen.request(null);
        if(age==240)button("Supprimer ce preset");
        if(age==245)button("Terminé");
        if(age==260)server(p->{var d=MapAtmosphere.get(p.serverLevel());check(!d.availablePresets().contains(savedId)&&!MapAtmosphere.load(d.save(new CompoundTag())).availablePresets().contains(savedId),"Done deletes the custom preset persistently");try{
            var l=p.serverLevel();var a=pad(l,from,"terre","lune",false);pad(l,to,"lune","",true);var n=a.config.copy();n.putString("arrivalAtmospherePreset","moon");n.putBoolean("startRoundsOnArrival",true);n.putString("enableChannels","1");n.putString("disableChannels","0");n.putInt("unlockAfterSeconds",60);a.configure(n);
            field("gameRunning",true);field("currentWave",0);WaveManager.startIntroPhase(l);field("matchElapsedTicks",1199L);check(a.readiness().equals("message.zombierool.teleporter.wait_start"),"Exact Direct terre-to-lune setup remains locked before 60 match seconds");field("matchElapsedTicks",1200L);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(l,1500.5,65.126,.5,0,0);check(TeleporterTravel.depart(p,a).isEmpty(),"Exact Direct terre-to-lune setup starts after its 60-second unlock");
        }catch(Exception ex){throw new RuntimeException(ex);}});
        if(age==330){arrival=arrived();check(arrival>=0&&MapAtmosphereClient.settings().getString("presetId").equals("moon"),"Direct arrival applies Moon and begins one tunnel");long time=mc.level.getGameTime();mc.level.setGameTime(time+1000);TeleporterChargeOverlay.start(0);check(arrival==arrived(),"A forward world-time correction cannot defeat duplicate-arrival protection");mc.level.setGameTime(time);}
        if(age==360)server(p->check(p.getX()>1509&&WaveManager.UNLOCKED_CHANNELS.contains(1)&&WaveManager.LOCKED_CHANNELS.contains(0),"Direct arrival switches spawner channels only at the destination"));
        if(age==395){check(!TeleporterChargeOverlay.active(),"The first arrival animation has ended");long time=mc.level.getGameTime();mc.level.setGameTime(time-52);check(!TeleporterChargeOverlay.active()&&arrived()==arrival,"A 52-tick backward time correction cannot replay the tunnel after its exit flash");shot("direct-after-time-correction");}
        if(age==410){check(!TeleporterChargeOverlay.active()&&arrived()==arrival,"Arrival remains finished after subsequent client frames");server(p->check(!WaveManager.isIntroPhase()&&MapAtmosphere.effective(p).getString("presetId").equals("moon"),"Finish Outnumbered ends intro without restarting travel or losing Moon"));}
        if(age==425){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" import, deletion and exact Direct travel checks");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
