package me.cryo.zombierool.client;

import java.util.*;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import me.cryo.zombierool.gameplay.AmbientScenery;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Device tick driven, never render driven: sound also works when looking away. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class AmbientScenerySounds {
    private record Key(BlockPos pos,int aircraft) {}
    private static final Map<Key,Engine> ENGINES=new HashMap<>();
    private static final Map<BlockPos,Shots> SHOTS=new HashMap<>();
    private static ClientLevel world;
    private static final class Shots {long start,cycle=Long.MIN_VALUE;final Map<Integer,SoundInstance> voices=new HashMap<>();}
    public static void tickDevice(MapDeviceSystem.Device d){
        if(!AmbientScenery.kind(d.kind()))return;
        var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;}
        if(world==null||d.getLevel()!=world||mc.player==null)return;
        if(!active(d)||d.volume<=0)return;
        double seconds=(world.getGameTime()-d.sceneryStart)/20.0;
        if(d.kind()==MapDeviceSystem.Kind.VEHICLE){if(d.scenery.getString("variant").equals("fire")&&Math.floorMod(world.getGameTime()+d.getBlockPos().asLong(),80)==0)world.playLocalSound(d.getBlockPos().getX()+.5,d.getBlockPos().getY()+1,d.getBlockPos().getZ()+.5,net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,SoundSource.AMBIENT,Math.min(.2f,d.volume),.9f,false);return;}
        if(d.kind()==MapDeviceSystem.Kind.AIRCRAFT){
            if(d.scenery.getString("variant").equals("necropolis")){
                ENGINES.entrySet().removeIf(row->{if(!row.getKey().pos().equals(d.getBlockPos()))return false;row.getValue().end();return true;});
                var shots=SHOTS.remove(d.getBlockPos());if(shots!=null)shots.voices.values().forEach(mc.getSoundManager()::stop);
                return;
            }
            for(int i=0;i<d.scenery.getInt("amount");i++){
                var key=new Key(d.getBlockPos().immutable(),i);var at=position(d,i,seconds);
                if(!AmbientScenery.aircraftVisible(d.scenery,i,seconds)||at.distanceTo(mc.gameRenderer.getMainCamera().getPosition())>d.scenery.getDouble("soundRange"))continue;
                var engine=ENGINES.get(key);
                if((engine==null||engine.isStopped())&&ENGINES.size()<24){engine=new Engine(d,i);ENGINES.put(key,engine);mc.getSoundManager().play(engine);}
            }
        }
        if(d.kind()!=MapDeviceSystem.Kind.DCA&&d.kind()!=MapDeviceSystem.Kind.AIRCRAFT)return;
        var shots=SHOTS.computeIfAbsent(d.getBlockPos().immutable(),p->new Shots());
        if(shots.start!=d.sceneryStart){shots.cycle=Long.MIN_VALUE;shots.start=d.sceneryStart;}
        long cycle=d.kind()==MapDeviceSystem.Kind.DCA?AmbientScenery.dcaCycle(d,seconds):AmbientScenery.shotCycle(seconds,d.interval);
        if(shots.cycle==cycle)return;shots.cycle=cycle;
        if(d.kind()==MapDeviceSystem.Kind.DCA){if(!AmbientScenery.dcaFires(d,cycle))return;play(shots,0,ZombieroolModSounds.SCENERY_FLAK_FIRE.get(),Vec3.atLowerCornerOf(d.getBlockPos()).add(.5,1.4,.5),.8f*d.volume,1,d.scenery.getDouble("soundRange"));}
        else if(!d.scenery.getString("variant").equals("airship"))for(int i=0;i<d.scenery.getInt("amount");i++){
            if(AmbientScenery.aircraftFiring(d,i,seconds))play(shots,i,ZombieroolModSounds.SCENERY_AIRCRAFT_GUN.get(),position(d,i,seconds),.22f*d.volume,1.05f,d.scenery.getDouble("soundRange"));
        }
    }
    private static void play(Shots shots,int index,SoundEvent sound,Vec3 at,float gain,float pitch,double range){
        var mc=Minecraft.getInstance();double distance=mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);float volume=gain*(float)Math.pow(Math.max(0,1-distance/range),1.5);
        if(volume<=.002)return;
        var old=shots.voices.remove(index);if(old!=null)mc.getSoundManager().stop(old);
        var instance=new SimpleSoundInstance(sound.getLocation(),SoundSource.AMBIENT,Math.min(1.5f,volume),pitch,SoundInstance.createUnseededRandom(),false,0,SoundInstance.Attenuation.NONE,at.x,at.y,at.z,false);
        shots.voices.put(index,instance);mc.getSoundManager().play(instance);
    }
    private static boolean active(MapDeviceSystem.Device d){return !d.isRemoved()&&d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE);}
    private static Vec3 position(MapDeviceSystem.Device d,int index,double seconds){return AmbientScenery.aircraftPosition(d.scenery,index,seconds).add(d.getBlockPos().getX()+.5,d.getBlockPos().getY(),d.getBlockPos().getZ()+.5);}
    private static boolean valid(BlockPos pos){return world!=null&&world.hasChunkAt(pos)&&world.getBlockEntity(pos) instanceof MapDeviceSystem.Device d&&AmbientScenery.kind(d.kind())&&active(d)&&d.volume>0;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level!=world){clear();world=mc.level;return;}
        ENGINES.entrySet().removeIf(row->{if(!valid(row.getKey().pos)||row.getValue().isStopped()){row.getValue().end();return true;}return false;});
        SHOTS.entrySet().removeIf(row->{if(valid(row.getKey()))return false;row.getValue().voices.values().forEach(mc.getSoundManager()::stop);return true;});
    }
    private static void clear(){var sounds=Minecraft.getInstance().getSoundManager();ENGINES.values().forEach(Engine::end);ENGINES.clear();SHOTS.values().forEach(s->s.voices.values().forEach(sounds::stop));SHOTS.clear();world=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SubscribeEvent public static void reload(SoundEngineLoadEvent e){clear();}
    private static final class Engine extends AbstractTickableSoundInstance {
        final MapDeviceSystem.Device device;final int index;final ClientLevel owner;
        Engine(MapDeviceSystem.Device d,int index){super(ZombieroolModSounds.SCENERY_AIRCRAFT_ENGINE.get(),SoundSource.AMBIENT,SoundInstance.createUnseededRandom());device=d;this.index=index;owner=(ClientLevel)d.getLevel();looping=true;delay=0;attenuation=Attenuation.NONE;volume=.001f;tick();}
        @Override public boolean canStartSilent(){return true;}
        void end(){stop();}
        @Override public void tick(){
            var mc=Minecraft.getInstance();if(mc.level!=owner||!valid(device.getBlockPos())||owner.getBlockEntity(device.getBlockPos())!=device||index>=device.scenery.getInt("amount")||device.scenery.getString("variant").equals("necropolis")){stop();return;}
            double time=(owner.getGameTime()-device.sceneryStart)/20.0;
            var at=position(device,index,time);double distance=mc.gameRenderer.getMainCamera().getPosition().distanceTo(at),range=device.scenery.getDouble("soundRange");
            if(!AmbientScenery.aircraftVisible(device.scenery,index,time)||distance>range+8){stop();return;}
            x=at.x;y=at.y;z=at.z;pitch=device.scenery.getString("variant").equals("airship")?.63f:.92f+(index%3)*.055f;
            volume=Math.min(1.3f,device.volume*.62f)*(float)Math.pow(Math.max(0,1-distance/range),1.5);
        }
    }
    private AmbientScenerySounds(){}
}
