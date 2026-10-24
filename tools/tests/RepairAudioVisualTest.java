package local.atmospherevisual;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.client.RepairLoopClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
@Mod("atmospherevisual")
public class RepairAudioVisualTest {
 int age,seenStage=-1,samples;boolean requested;Object first;volatile Throwable failure;
 BlockPos pos=new BlockPos(100,80,92);
 public RepairAudioVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  try{
   if(failure!=null)throw new AssertionError(failure);
   if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
   if(mc.player==null||mc.level==null)return;age++;
   if(age==10){mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(.01);mc.getSoundManager().updateSourceVolume(net.minecraft.sounds.SoundSource.MASTER,.01f);mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(l,100.5,80,90.5,0,0);for(var at:BlockPos.betweenClosed(98,79,89,102,83,93))l.setBlock(at,at.getY()==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);var s=me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get().defaultBlockState().setValue(DefenseDoorSystem.DefenseDoorBlock.STAGE,0);l.setBlock(pos,s,3);l.setBlock(pos.above(),s.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);});}
   if(age>=30&&age<180){mc.player.setYRot(0);mc.player.setXRot(0);me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(true);
    var state=mc.level.getBlockState(pos);if(state.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock){int stage=state.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE);if(stage!=seenStage){System.out.println("REPAIR STAGE "+stage+" at "+age);seenStage=stage;}
     if(stage>0&&stage<5&&age>40){
      var engine=(SoundEngine)field(mc.getSoundManager(),"soundEngine");var map=(java.util.Map<?,?>)field(engine,"instanceToChannel");
      int floats=0,planks=0;for(var entry:map.entrySet()){
       var sound=(SoundInstance)entry.getKey();String id=sound.getLocation().toString();
       if(!id.equals("zombierool:boards_float")&&!id.equals("zombierool:repairing_plank"))continue;
       if(id.endsWith("boards_float"))floats++;else planks++;
       var handle=(ChannelAccess.ChannelHandle)entry.getValue();final int tick=age;
       handle.execute(channel->{try{int source=(Integer)field(channel,"source");int playback=org.lwjgl.openal.AL10.alGetSourcei(source,org.lwjgl.openal.AL10.AL_SOURCE_STATE);if(playback==org.lwjgl.openal.AL10.AL_PLAYING)System.out.println("REPAIR AUDIO "+tick+" stage="+stage+" id="+id+" source="+source+" playing");}catch(Throwable t){failure=t;}});
      }
      if(floats==0||planks==0)throw new AssertionError("Missing layered repair audio at stage "+stage+": "+floats+" / "+planks);
      samples++;
     }
    }
   }
   if(age==180)me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.setDown(false);
   if(age==210){if(samples<50||seenStage!=5)throw new AssertionError("Incomplete repair: "+samples+" samples stage "+seenStage);System.out.println("ATMOSPHERE VISUAL PASS: real repair audio "+samples+" source samples");mc.stop();}
  }catch(Throwable t){t.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}
 }
}
