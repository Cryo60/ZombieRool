package local.atmospherevisual;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class CerberusScourgeVisualTest {
    boolean requested,done;int age,checks;volatile String failure;java.util.UUID meal;
    final BlockPos pos=new BlockPos(0,66,15);
    public CerberusScourgeVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==25)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);l.setDayTime(6000);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());try{var f=WaveManager.class.getDeclaredField("gameRunning");f.setAccessible(true);f.setBoolean(null,true);}catch(Exception e){throw new RuntimeException(e);}me.cryo.zombierool.config.WorldConfig.get(l).setReducedGore(false);for(var at:BlockPos.betweenClosed(-7,64,8,7,73,26))l.setBlock(at,at.getY()==64?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);p.teleportTo(l,4.5,67,22,140,12);l.setBlock(pos.north(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);l.setBlock(pos,MapAuthorSystem.CERBERUS.get().defaultBlockState().setValue(CerberusSystem.WallBlock.FACING,Direction.SOUTH),3);((CerberusSystem.Head)l.getBlockEntity(pos)).onLoad();});
        for(int i=0;i<4;i++){int start=45+i*135;final int kind=i;
            if(age==start)server(p->{var l=p.serverLevel();var head=(CerberusSystem.Head)l.getBlockEntity(pos);head.reset();var n=head.config();n.putInt("chewTime",4);n.putInt("souls",8);head.configure(n);AbstractZombieRoolEntity body;
                if(kind==3)body=ZombieroolModEntities.GEIST.get().create(l);else{var z=ZombieroolModEntities.ZOMBIE.get().create(l);z.setCustomSkin("@preset/zombies/scourge/"+new int[]{1,5,7}[kind]);body=z;}
                body.getPersistentData().putBoolean("zr_special_spawner",true);body.getPersistentData().putBoolean("zr_debug_spawn",true);body.setNoAi(true);body.setEmergenceTicks(0);body.getPersistentData().putBoolean("zombierool:no_gore",true);body.setPos(.5,65,19);l.addFreshEntity(body);meal=body.getUUID();body.hurt(l.damageSources().playerAttack(p),10000);check(body.isCerberusFeeding(),"Cerberus accepts a real death of Scourge archetype "+kind);
            });
            if(age==start+40){var body=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(e->e.getUUID().equals(meal)).findFirst().orElse(null);check(body instanceof AbstractZombieRoolEntity z&&z.isCerberusFeeding(),"Body remains present through bites for archetype "+kind);shot("feeding-"+kind+"-early");}
            if(age==start+90)shot("feeding-"+kind+"-swallowed");
            if(age==start+125)server(p->{var head=(CerberusSystem.Head)p.serverLevel().getBlockEntity(pos);check(head.collected==1&&p.serverLevel().getEntity(meal)==null,"One completed Scourge meal consumes the body exactly once: "+kind);});
        }
        if(age==590){check(org.lwjgl.opengl.GL11.glGetError()==0,"Four Scourge feeding sequences render without OpenGL errors");done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" Scourge Cerberus checks");mc.stop();}
    }catch(Throwable e){done=true;e.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+e);mc.stop();}}
}
