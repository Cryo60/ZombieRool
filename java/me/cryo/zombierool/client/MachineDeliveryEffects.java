package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.MachineDeliverySystem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Mono original assets retain true left/right and vertical positioning as the machine falls. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class MachineDeliveryEffects {
    private static final Map<BlockPos,Flight> FLIGHTS=new HashMap<>();
    private static ClientLevel world;
    private record Flight(UUID token,List<MovingSound> sounds){void stop(){sounds.forEach(MovingSound::end);}}
    public static void tick(MachineDeliverySystem.Delivery d){
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}if(world==null||d.getLevel()!=world)return;
        var pos=d.getBlockPos();var previous=FLIGHTS.get(pos);
        if(!d.phase.equals("animating")||!d.falling){if(previous!=null){previous.stop();FLIGHTS.remove(pos);}return;}
        if(previous==null||!Objects.equals(previous.token,d.token)){
            if(previous!=null)previous.stop();var sounds=new ArrayList<MovingSound>();
            // Late chunk arrivals keep the positional loop without restarting the warning siren.
            if(world.getGameTime()-d.started<10){sounds.add(new MovingSound(d,ZombieroolModSounds.DELIVERY_ALARM.get(),false,false,128,.9f));sounds.add(new MovingSound(d,ZombieroolModSounds.DELIVERY_INCOMING.get(),true,false,160,1));}
            sounds.add(new MovingSound(d,ZombieroolModSounds.DELIVERY_FALL_LOOP.get(),true,true,128,.7f));
            FLIGHTS.put(pos.immutable(),new Flight(d.token,sounds));sounds.forEach(mc.getSoundManager()::play);
        }
        var random=world.random;double y=pos.getY()+d.baseOffset()+d.height(0),top=d.selected.equals(MachineDeliverySystem.PACK)?1.4:2.7;
        for(int i=0;i<28;i++){
            double angle=random.nextDouble()*Math.PI*2,x=pos.getX()+.5+Math.cos(angle)*(.5+random.nextDouble()*.18),z=pos.getZ()+.5+Math.sin(angle)*(.5+random.nextDouble()*.18);
            world.addParticle(ParticleTypes.FLAME,true,x,y+random.nextDouble()*top,z,0,.06+random.nextDouble()*.05,0);
            if(i<5)world.addParticle(ParticleTypes.LARGE_SMOKE,true,x,y+top,z,0,.08,0);
        }
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        FLIGHTS.entrySet().removeIf(entry->{boolean alive=mc.level==world&&world!=null&&world.hasChunkAt(entry.getKey())&&world.getBlockEntity(entry.getKey()) instanceof MachineDeliverySystem.Delivery d&&d.phase.equals("animating")&&d.falling&&Objects.equals(entry.getValue().token,d.token);if(!alive)entry.getValue().stop();return !alive;});
    }
    private static void clear(){FLIGHTS.values().forEach(Flight::stop);FLIGHTS.clear();world=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SubscribeEvent public static void reload(SoundEngineLoadEvent e){clear();}
    private static final class MovingSound extends AbstractTickableSoundInstance {
        final ClientLevel level;final BlockPos pos;final UUID token;final boolean moving;final float range,gain;
        MovingSound(MachineDeliverySystem.Delivery d,SoundEvent event,boolean moving,boolean repeat,float range,float gain){super(event,SoundSource.BLOCKS,SoundInstance.createUnseededRandom());level=(ClientLevel)d.getLevel();pos=d.getBlockPos();token=d.token;this.moving=moving;this.range=range;this.gain=gain;looping=repeat;attenuation=SoundInstance.Attenuation.NONE;volume=.001f;pitch=1;tick();}
        @Override public boolean canStartSilent(){return true;}
        void end(){stop();}
        @Override public void tick(){
            var mc=Minecraft.getInstance();if(mc.level!=level||mc.player==null||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof MachineDeliverySystem.Delivery d)||!d.phase.equals("animating")||!d.falling||!Objects.equals(token,d.token)){stop();return;}
            x=pos.getX()+.5;y=pos.getY()+d.baseOffset()+(moving?d.height(mc.getFrameTime())+1:1);z=pos.getZ()+.5;
            double distance=mc.gameRenderer.getMainCamera().getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z));volume=gain*(float)Math.pow(Math.max(0,1-distance/range),2);
        }
    }
}
