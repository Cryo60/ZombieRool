package me.cryo.zombierool.gameplay;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.*;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

/** A session-only pause: world simulation stops, networking and the vote remain live. */
public final class MatchPause {
    public static final ResourceLocation ID=new ResourceLocation("zombierool","match_pause");
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ID,()->"1","1"::equals,"1"::equals);
    private record Request(String action){}
    private record Update(CompoundTag state){}
    private static MinecraftServer owner;
    private static volatile boolean paused;
    private static boolean voting, target;
    private static String requester="";
    private static long deadline, cooldown, pausedAt;
    private static final Set<UUID> electorate=new HashSet<>(), yes=new HashSet<>(), no=new HashSet<>();
    private MatchPause(){}
    public static boolean frozen(){return paused;}
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,(m,b)->b.writeUtf(m.action,16),b->new Request(b.readUtf(16)),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->request(ctx.getSender(),m.action));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Update.class,(m,b)->b.writeNbt(m.state),b->new Update(b.readNbt()),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.MatchPauseClient.receive(m.state)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(String action){CHANNEL.sendToServer(new Request(action));}
    private static List<ServerPlayer> players(MinecraftServer server){return server.getPlayerList().getPlayers().stream().filter(p->!p.isSpectator()).toList();}
    private static void reset(MinecraftServer server){owner=server;paused=false;voting=false;yes.clear();no.clear();electorate.clear();deadline=cooldown=pausedAt=0;requester="";}
    public static void request(ServerPlayer p,String action){
        if(p==null)return;var server=p.server;if(owner!=server)reset(server);
        if(action.equals("state")){sync(server);return;}
        if(!WaveManager.isGameRunning()||p.isSpectator()){p.displayClientMessage(Component.literal("Pause is available to participants during a running match."),true);return;}
        if(action.equals("pause")||action.equals("resume")){
            boolean requested=action.equals("pause");
            if(voting){sync(server);return;}
            if(requested==paused){sync(server);return;}
            if(System.currentTimeMillis()<cooldown){p.displayClientMessage(Component.literal("Please wait a few seconds before starting another vote."),true);return;}
            requester=p.getName().getString();
            electorate.clear();for(var member:players(server))electorate.add(member.getUUID());
            yes.clear();no.clear();yes.add(p.getUUID());target=requested;voting=true;deadline=System.currentTimeMillis()+30_000;
            announce(server,p.getName().getString()+" requested to "+(target?"pause":"resume")+" the match.");evaluate(server);sync(server);
        }else if(voting&&electorate.contains(p.getUUID())&&!yes.contains(p.getUUID())&&!no.contains(p.getUUID())){
            if(action.equals("yes"))yes.add(p.getUUID());else if(action.equals("no"))no.add(p.getUUID());else return;
            evaluate(server);sync(server);
        }
    }
    /** Called from tickServer even while simulation and Forge gameplay ticks are suspended. */
    public static void pulse(MinecraftServer server){
        if(owner!=server){reset(server);return;}
        if(!WaveManager.isGameRunning()){if(paused||voting){setPaused(server,false);voting=false;sync(server);}return;}
        if(voting){var online=new HashSet<UUID>();for(var p:players(server))online.add(p.getUUID());boolean changed=electorate.retainAll(online);yes.retainAll(electorate);no.retainAll(electorate);evaluate(server);if(changed)sync(server);}
        if(paused){for(var p:server.getPlayerList().getPlayers())p.resetLastActionTime();}
    }
    private static void evaluate(MinecraftServer server){
        if(!voting)return;
        int needed=electorate.size()/2+1;
        if(!electorate.isEmpty()&&yes.size()>=needed){voting=false;setPaused(server,target);announce(server,paused?"Match paused.":"Match resumed.");sync(server);}
        else if(electorate.isEmpty()||no.size()>electorate.size()-needed||System.currentTimeMillis()>=deadline){voting=false;cooldown=System.currentTimeMillis()+10_000;announce(server,"Vote failed.");sync(server);}
    }
    private static void setPaused(MinecraftServer server,boolean value){
        if(paused==value)return;
        if(value)pausedAt=System.currentTimeMillis();
        else if(pausedAt>0){long elapsed=System.currentTimeMillis()-pausedAt;for(var level:server.getAllLevels())for(var e:level.getAllEntities())if(e instanceof net.minecraft.world.entity.LivingEntity living)me.cryo.zombierool.integration.TacZIntegration.compensatePause(living,elapsed);pausedAt=0;}
        paused=value;WaveManager.setGamePaused(value);
    }
    private static void announce(MinecraftServer server,String message){server.getPlayerList().broadcastSystemMessage(Component.literal(message),false);}
    private static void sync(MinecraftServer server){
        for(var p:server.getPlayerList().getPlayers()){
            var n=new CompoundTag();n.putString("requester",requester);n.putInt("no",no.size());n.putInt("total",electorate.size());n.putBoolean("paused",paused);n.putBoolean("voting",voting);n.putBoolean("target",target);n.putInt("yes",yes.size());n.putInt("needed",electorate.size()/2+1);n.putLong("remaining",Math.max(0,deadline-System.currentTimeMillis()));n.putBoolean("canVote",voting&&electorate.contains(p.getUUID())&&!yes.contains(p.getUUID())&&!no.contains(p.getUUID()));
            CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Update(n));
        }
    }
}
