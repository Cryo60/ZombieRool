package me.cryo.zombierool.client;

import me.cryo.zombierool.gameplay.TraversalMotion;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** One attached cable loop per rider; the sound ends on arrival, death or world changes. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ZiplineSounds {
    private static final Map<Entity,CableLoop> LOOPS=new HashMap<>();
    private ZiplineSounds(){}
    private static void clear(){for(CableLoop loop:List.copyOf(LOOPS.values()))loop.end();LOOPS.clear();}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void reload(net.minecraftforge.client.event.sound.SoundEngineLoadEvent e){clear();}
    public static void start(Entity entity){
        CableLoop old=LOOPS.remove(entity);if(old!=null)old.end();
        CableLoop loop=new CableLoop(entity);LOOPS.put(entity,loop);Minecraft.getInstance().getSoundManager().play(loop);
    }
    private static final class CableLoop extends AbstractTickableSoundInstance {
        private final Entity entity;
        CableLoop(Entity entity){
            super(ZombieroolModSounds.ZIPLINE_LOOP.get(),SoundSource.BLOCKS,SoundInstance.createUnseededRandom());this.entity=entity;
            looping=true;delay=0;volume=.5f;pitch=1;attenuation=Attenuation.LINEAR;updatePosition();
        }
        private void updatePosition(){x=entity.getX();y=entity.getY()+.8;z=entity.getZ();}
        void end(){stop();LOOPS.remove(entity,this);}
        @Override public void tick(){
            var f=TraversalMotion.flight(entity);
            if(entity.isRemoved()||!entity.isAlive()||Minecraft.getInstance().level!=entity.level()||f==null||f.style!=1){end();return;}
            updatePosition();double speed=f.start.distanceTo(f.end)*20/f.duration;pitch=(float)Math.min(1.4,.75+speed*.045);
        }
    }
}
