package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Temporary channel leases compose; explicit Lua/door changes supersede old leases. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class SpawnChannelActions {
    private record State(boolean unlocked,boolean locked){}
    private record Lease(boolean enabled,ServerLevel level,long expires,boolean redistribute){}
    private static final Map<Integer,State> ORIGINAL=new HashMap<>();
    private static final Map<Integer,List<Lease>> LEASES=new HashMap<>();
    private SpawnChannelActions(){}
    public static Set<Integer> parse(String text){
        Set<Integer> result=new LinkedHashSet<>();
        if(text==null||text.length()>256)return result;
        for(String token:text.split(",")){try{int n=Integer.parseInt(token.trim());if(n>=0&&n<=10000&&result.size()<64)result.add(n);}catch(NumberFormatException ignored){}}
        return result;
    }
    public static void external(int channel){ORIGINAL.remove(channel);LEASES.remove(channel);}
    private static State state(int channel){return new State(WaveManager.UNLOCKED_CHANNELS.contains(channel),WaveManager.LOCKED_CHANNELS.contains(channel));}
    private static void put(int channel,State state){WaveManager.UNLOCKED_CHANNELS.remove(channel);WaveManager.LOCKED_CHANNELS.remove(channel);if(state.unlocked)WaveManager.UNLOCKED_CHANNELS.add(channel);if(state.locked)WaveManager.LOCKED_CHANNELS.add(channel);}
    private static void apply(ServerLevel level,int channel,boolean enabled,int seconds,boolean redistribute){
        if(seconds==0){external(channel);put(channel,new State(enabled,!enabled));return;}
        ORIGINAL.computeIfAbsent(channel,SpawnChannelActions::state);
        LEASES.computeIfAbsent(channel,c->new ArrayList<>()).add(new Lease(enabled,level,level.getGameTime()+seconds*20L,redistribute));put(channel,new State(enabled,!enabled));
    }
    public static void apply(ServerLevel level,CompoundTag settings){
        int seconds=Math.max(0,Math.min(86400,settings.getInt("channelDuration")));
        boolean redistribute=settings.getBoolean("redistributeMobs");
        var before=redistribute?SpawnRedistribution.activeSpawners(level.getServer()):null;
        for(int n:parse(settings.getString("enableChannels")))apply(level,n,true,seconds,redistribute);
        for(int n:parse(settings.getString("disableChannels")))apply(level,n,false,seconds,redistribute);
        if(before!=null)SpawnRedistribution.redistribute(before);
    }
    public static void reset(){LEASES.clear();ORIGINAL.clear();SpawnRedistribution.reset();}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){reset();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END)return;
        boolean recycle=LEASES.values().stream().flatMap(List::stream).anyMatch(l->l.redistribute&&l.level.getGameTime()>=l.expires);
        var before=recycle?SpawnRedistribution.activeSpawners(event.getServer()):null;
        for(int channel:List.copyOf(LEASES.keySet())){
            var leases=LEASES.get(channel);boolean changed=leases.removeIf(l->l.level.getGameTime()>=l.expires);
            if(!changed)continue;
            if(leases.isEmpty()){put(channel,ORIGINAL.remove(channel));LEASES.remove(channel);}
            else{Lease last=leases.get(leases.size()-1);put(channel,new State(last.enabled,!last.enabled));}
        }
        if(before!=null)SpawnRedistribution.redistribute(before);
    }
}
