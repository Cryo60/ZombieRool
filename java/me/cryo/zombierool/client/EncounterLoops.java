package me.cryo.zombierool.client;

import java.util.*;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Positional, stoppable source loops: never repeatedly overlap timed playSound calls. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class EncounterLoops {
    private static final Map<Integer,Loop> ACTIVE=new HashMap<>();
    private static Object world;
    private static Alarm alarm;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level!=world){clear();world=mc.level;}
        if(mc.level==null)return;
        ACTIVE.entrySet().removeIf(entry->entry.getValue().isStopped());
        if(!WaveManager.isGameRunning()){clear();world=mc.level;return;}
        boolean thiefRound=WaveManager.isClientSpecialWave()&&!WaveManager.isClientHellhoundFog();
        if(thiefRound&&(alarm==null||alarm.isStopped())){alarm=new Alarm();mc.getSoundManager().play(alarm);}else if(!thiefRound&&alarm!=null){alarm.end();alarm=null;}
        if(mc.level.getGameTime()%10!=0)return;
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof SpecialEnemyEntity mob&&mob.isAlive()){
            String kind=EncounterAbilities.kind(mob);
            String key=kind.equals("astronaut")?"astronaut_loop":kind.equals("george_romero")?mob.bossProfile().getBoolean("enraged")?"romero_lamp_rage":"romero_lamp_calm":null;
            if(key==null)continue;Loop previous=ACTIVE.get(mob.getId());
            if(previous!=null&&previous.key.equals(key))continue;
            if(previous!=null)previous.end();
            Loop loop=new Loop(mob,key);ACTIVE.put(mob.getId(),loop);mc.getSoundManager().play(loop);
        }
    }
    private static void clear(){for(var loop:ACTIVE.values())loop.end();ACTIVE.clear();if(alarm!=null){alarm.end();alarm=null;}world=null;}
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clear();}
    @SubscribeEvent public static void reload(net.minecraftforge.client.event.sound.SoundEngineLoadEvent e){clear();}
    /** Round-owned, so chunk tracking and the Thief's distance cannot mute it. */
    private static final class Alarm extends AbstractTickableSoundInstance{
        Alarm(){super(ZombieroolModSounds.encounter("thief_alarm"),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());looping=true;relative=true;attenuation=SoundInstance.Attenuation.NONE;volume=.25f;pitch=1;}
        void end(){stop();}
        @Override public void tick(){if(Minecraft.getInstance().level==null||!WaveManager.isGameRunning()||!WaveManager.isClientSpecialWave()||WaveManager.isClientHellhoundFog())end();}
    }
    private static final class Loop extends AbstractTickableSoundInstance{
        final SpecialEnemyEntity mob;final String key;
        Loop(SpecialEnemyEntity mob,String key){super(ZombieroolModSounds.encounter(key),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());this.mob=mob;this.key=key;looping=true;delay=0;pitch=1;volume=key.equals("thief_alarm")?.25f:.15f;if(key.equals("thief_alarm")){relative=true;attenuation=SoundInstance.Attenuation.NONE;}follow();}
        void follow(){if(key.equals("thief_alarm")){x=y=z=0;}else{x=mob.getX();y=mob.getY()+1;z=mob.getZ();}}
        void end(){stop();}
        @Override public void tick(){if(!mob.isAlive()||mob.isRemoved()||mob.level()!=Minecraft.getInstance().level||!WaveManager.isGameRunning()){end();return;}follow();}
    }
}
