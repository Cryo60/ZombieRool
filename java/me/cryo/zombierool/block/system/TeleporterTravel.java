package me.cryo.zombierool.block.system;

import java.util.*;
import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Timed journeys use server ticks and UUIDs, never player entity references. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class TeleporterTravel {
    private static final String RETURN="zr_teleporter_return",LOCK="zr_teleporter_lock";
    private static final Map<ServerLevel,List<Journey>> JOURNEYS=new WeakHashMap<>();
    private static final Map<ServerLevel,List<Departure>> DEPARTURES=new WeakHashMap<>();
    private record Departure(BlockPos source,String target,UUID payer,List<UUID> players,CompoundTag settings,boolean timed,boolean defcon,long due) {}
    private static final class Journey {
        final BlockPos source;
        final String channel,returnId;
        final List<UUID> players;
        final CompoundTag settings;
        final boolean defcon;
        long due;
        boolean secret,charging;
        String nextRoom;
        Journey(TeleporterSystem.Pad pad,List<ServerPlayer> players,String returnId,boolean defcon){
            source=pad.getBlockPos();channel=pad.channel();this.returnId=returnId;this.players=players.stream().map(ServerPlayer::getUUID).toList();settings=pad.config.copy();this.defcon=defcon;
            due=pad.getLevel().getGameTime()+Math.max(1,settings.getInt("stay"))*20L;
        }
    }
    private TeleporterTravel(){}
    public static long arrivalLock(ServerPlayer p){return p.getPersistentData().getLong(LOCK);}
    public static boolean busy(ServerLevel level,BlockPos pos){return JOURNEYS.getOrDefault(level,List.of()).stream().anyMatch(j->j.source.equals(pos))||DEPARTURES.getOrDefault(level,List.of()).stream().anyMatch(d->d.source.equals(pos));}
    private static boolean occupied(ServerPlayer p){return p.getPersistentData().contains(RETURN)||DEPARTURES.values().stream().flatMap(List::stream).anyMatch(d->d.players.contains(p.getUUID()));}
    private static List<ServerPlayer> living(ServerLevel level){return level.players().stream().filter(TeleporterSystem::validPlayer).toList();}
    private static boolean safe(ServerPlayer player,TeleporterSystem.Pad pad){
        if(!(pad.getLevel() instanceof ServerLevel destination))return false;
        Vec3 target=landing(pad);var box=player.getBoundingBox().move(target.subtract(player.position()));
        return destination.getWorldBorder().isWithinBounds(box)&&box.minY>=destination.getMinBuildHeight()&&box.maxY<destination.getMaxBuildHeight()
                &&!destination.getBlockCollisions(player,box).iterator().hasNext();
    }
    private static Vec3 landing(TeleporterSystem.Pad pad){return Vec3.atBottomCenterOf(pad.getBlockPos()).add(0,.126,0);}
    private static void move(ServerPlayer player,TeleporterSystem.Pad pad){
        ServerLevel level=(ServerLevel)pad.getLevel();Vec3 p=landing(pad);
        player.stopRiding();player.teleportTo(level,p.x,p.y,p.z,(float)pad.config.getDouble("yaw"),player.getXRot());player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        player.getPersistentData().putLong(LOCK,level.getGameTime()+40);
        player.playNotifySound(me.cryo.zombierool.init.ZombieroolModSounds.TELEPORT_IN.get(),SoundSource.PLAYERS,1,1);
        TransportPackets.charge(player,0);
    }
    private static TeleporterSystem.Pad randomDestination(ServerLevel level,TeleporterSystem.Pad source,boolean natural){
        var registry=TeleporterRegistry.get(level);
        List<String> ids=registry.pads.entrySet().stream().filter(e->!e.getKey().equals(source.getBlockPos())&&e.getValue().getBoolean("enabled")
                &&!e.getValue().getString("role").equals("SWITCH")&&e.getValue().getString("channel").equals(source.channel())
                &&(!natural||e.getValue().getString("mode").equals("DENIZEN"))).map(e->e.getValue().getString("id")).distinct().toList();
        List<TeleporterSystem.Pad> valid=new ArrayList<>();
        for(String id:ids){var target=TeleporterSystem.find(level,id);if(target!=null&&target.readiness().isEmpty()&&target.hasPower())valid.add(target);}
        return valid.isEmpty()?null:valid.get(level.random.nextInt(valid.size()));
    }
    public static String depart(ServerPlayer payer,TeleporterSystem.Pad source){
        if(!(source.getLevel() instanceof ServerLevel level)||!source.onPlatform(payer))return "message.zombierool.teleporter.stand";
        String error=source.readiness();if(!error.isEmpty())return error;
        if(source.config.getBoolean("requiresLink")&&!source.linked)return "message.zombierool.teleporter.unlinked";
        if(arrivalLock(payer)>level.getGameTime()||occupied(payer))return "message.zombierool.teleporter.busy";
        List<ServerPlayer> travelers=living(level).stream().filter(source::onPlatform).toList();
        if(travelers.isEmpty())return "message.zombierool.teleporter.stand";
        if(source.config.getBoolean("allPlayers")&&!travelers.containsAll(living(level)))return "message.zombierool.teleporter.all_players";
        if(travelers.stream().anyMatch(TeleporterTravel::occupied))return "message.zombierool.teleporter.busy";
        var registry=TeleporterRegistry.get(level);
        boolean defcon=source.config.getString("mode").equals("DEFCON")&&registry.conditionCount(source.channel())>=source.config.getInt("requiredSwitches");
        TeleporterSystem.Pad target;
        if(source.config.getString("mode").equals("DEFCON")&&!defcon)target=randomDestination(level,source,false);
        else if(source.config.getString("mode").equals("DENIZEN"))target=randomDestination(level,source,true);
        else target=TeleporterSystem.find(level,source.config.getString("destination"));
        if(target==null||target==source)return "message.zombierool.teleporter.no_destination";
        if(!target.hasPower())return "message.zombierool.power_required";
        if(travelers.stream().anyMatch(p->!safe(p,target)))return "message.zombierool.teleporter.unsafe";
        boolean timed=source.config.getInt("stay")>0&&(!source.config.getString("mode").equals("DEFCON")||defcon);
        String returnId=source.config.getString("returnId");
        TeleporterSystem.Pad receiver=timed?TeleporterSystem.find(level,returnId):null;
        if(timed&&(receiver==null||travelers.stream().anyMatch(p->!safe(p,receiver))))return "message.zombierool.teleporter.no_return";
        int cost=source.config.getInt("cost");
        if(PointManager.getScore(payer)<cost)return "message.zombierool.teleporter.no_points";
        source.busy=true;source.sync();
        int charge=Math.max(1,source.config.getInt("chargeTime"))*20;
        Departure departure=new Departure(source.getBlockPos(),target.config.getString("id"),payer.getUUID(),travelers.stream().map(ServerPlayer::getUUID).toList(),source.config.copy(),timed,defcon,level.getGameTime()+charge);
        DEPARTURES.computeIfAbsent(level,k->new ArrayList<>()).add(departure);
        travelers.forEach(p->charge(p,charge));
        return "";
    }
    private static void charge(ServerPlayer player,int ticks){
        TransportPackets.charge(player,ticks);
        player.playNotifySound(me.cryo.zombierool.init.ZombieroolModSounds.TELEPORT_START.get(),SoundSource.PLAYERS,1,1);
    }
    private static List<ServerPlayer> players(ServerLevel level,List<UUID> ids){return ids.stream().map(id->level.getServer().getPlayerList().getPlayer(id)).filter(Objects::nonNull).filter(TeleporterSystem::validPlayer).toList();}
    private static void complete(ServerLevel level,Departure departure){
        level.getChunkAt(departure.source);var source=TeleporterSystem.pad(level,departure.source);
        var travelers=players(level,departure.players);var payer=level.getServer().getPlayerList().getPlayer(departure.payer);
        var target=TeleporterSystem.find(level,departure.target);
        String error=source==null?"message.zombierool.teleporter.disabled":source.readiness(true);
        if(error.isEmpty()&&(payer==null||!TeleporterSystem.validPlayer(payer)||!travelers.contains(payer)||travelers.size()!=departure.players.size()||travelers.stream().anyMatch(p->!source.onPlatform(p))))error="message.zombierool.teleporter.cancelled";
        if(error.isEmpty()&&departure.settings.getBoolean("allPlayers")&&!travelers.containsAll(living(level)))error="message.zombierool.teleporter.all_players";
        if(error.isEmpty()&&(target==null||!target.hasPower()))error="message.zombierool.teleporter.no_destination";
        if(error.isEmpty()&&travelers.stream().anyMatch(p->!safe(p,target)))error="message.zombierool.teleporter.unsafe";
        String returnId=departure.settings.getString("returnId");var receiver=departure.timed?TeleporterSystem.find(level,returnId):null;
        if(error.isEmpty()&&departure.timed&&(receiver==null||travelers.stream().anyMatch(p->!safe(p,receiver))))error="message.zombierool.teleporter.no_return";
        int cost=departure.settings.getInt("cost");
        if(error.isEmpty()&&PointManager.getScore(payer)<cost)error="message.zombierool.teleporter.no_points";
        if(!error.isEmpty()){
            for(UUID id:departure.players){var player=level.getServer().getPlayerList().getPlayer(id);if(player!=null){TransportPackets.charge(player,-1);player.displayClientMessage(Component.translatable(error),true);}}
            if(source!=null){source.busy=false;source.sync();}return;
        }
        if(cost>0)PointManager.modifyScore(payer,-cost,false);
        if(departure.timed){
            JOURNEYS.computeIfAbsent(level,k->new ArrayList<>()).add(new Journey(source,travelers,returnId,departure.defcon));
            for(ServerPlayer player:travelers){CompoundTag restore=new CompoundTag();restore.putString("dimension",level.dimension().location().toString());restore.putString("receiver",returnId);restore.putLong("source",departure.source.asLong());player.getPersistentData().put(RETURN,restore);}
        }
        travelers.forEach(p->move(p,target));
        LuaScriptManager.callEvent("OnTeleporterUsed",source.config.getString("id"),target.config.getString("id"),payer.getUUID().toString());
        if(!departure.timed)cooldown(level,departure.source,departure.settings);else source.sync();
    }
    private static void cooldown(ServerLevel level,BlockPos origin,CompoundTag settings){
        var registry=TeleporterRegistry.get(level);
        registry.pads.forEach((pos,config)->{
            if(pos.equals(origin)||settings.getBoolean("sharedCooldown")&&config.getString("channel").equals(settings.getString("channel"))){
                config.putLong("cooldownUntil",Math.max(config.getLong("cooldownUntil"),level.getGameTime()+settings.getInt("cooldown")*20L));
                if(settings.getBoolean("relink")&&TeleporterRegistry.isSource(config))config.putBoolean("linked",false);
            }
        });registry.setDirty();
        for(TeleporterSystem.Pad pad:TeleporterSystem.loaded(level))if(pad.getBlockPos().equals(origin)||settings.getBoolean("sharedCooldown")&&pad.channel().equals(settings.getString("channel"))){
            pad.busy=false;pad.cooldownUntil=Math.max(pad.cooldownUntil,level.getGameTime()+settings.getInt("cooldown")*20L);
            if(settings.getBoolean("relink")&&pad.role().equals("SOURCE"))pad.linked=false;
            pad.sync();
        }
    }
    private static boolean returnPlayers(ServerLevel level,Journey trip){
        TeleporterSystem.Pad receiver=TeleporterSystem.find(level,trip.returnId);
        if(receiver==null){level.getChunkAt(trip.source);receiver=TeleporterSystem.pad(level,trip.source);if(receiver==null)return false;}
        boolean complete=true;
        for(UUID uuid:trip.players){
            ServerPlayer player=level.getServer().getPlayerList().getPlayer(uuid);
            if(player==null)continue; // Persistent return tag recovers disconnected travelers on login.
            if(!TeleporterSystem.validPlayer(player)){player.getPersistentData().remove(RETURN);continue;}
            if(!player.getPersistentData().contains(RETURN))continue;
            if(!safe(player,receiver)){complete=false;continue;}
            move(player,receiver);player.getPersistentData().remove(RETURN);
        }
        return complete;
    }
    private static String secretDestination(ServerLevel level,Journey trip){
        if(trip.secret||!trip.settings.getString("mode").equals("KINO")||trip.settings.getString("secretIds").isBlank()||level.random.nextDouble()>=trip.settings.getDouble("secretChance"))return null;
        String[] ids=trip.settings.getString("secretIds").split(",");String id=ids[level.random.nextInt(ids.length)].trim();var room=TeleporterSystem.find(level,id);
        if(room==null||players(level,trip.players).stream().anyMatch(p->!safe(p,room)))return null;
        return id;
    }
    public static void endTrips(ServerLevel level,String channel){for(Journey j:JOURNEYS.getOrDefault(level,List.of()))if(j.channel.equals(channel)){j.secret=true;j.charging=false;j.due=level.getGameTime();}}
    public static void reset(ServerLevel level){for(Departure d:DEPARTURES.getOrDefault(level,List.of()))for(ServerPlayer p:players(level,d.players))TransportPackets.charge(p,-1);DEPARTURES.remove(level);for(Journey j:List.copyOf(JOURNEYS.getOrDefault(level,List.of())))returnPlayers(level,j);JOURNEYS.remove(level);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(ServerLevel level:event.getServer().getAllLevels()){
            for(Departure d:List.copyOf(DEPARTURES.getOrDefault(level,List.of())))if(level.getGameTime()>=d.due){complete(level,d);DEPARTURES.get(level).remove(d);}
        }
        for(ServerLevel level:event.getServer().getAllLevels())for(Journey trip:List.copyOf(JOURNEYS.getOrDefault(level,List.of()))) {
            if(level.getGameTime()<trip.due){
                if(level.getGameTime()%20==0)for(UUID id:trip.players){var p=level.getServer().getPlayerList().getPlayer(id);if(p!=null)p.displayClientMessage(Component.translatable("message.zombierool.teleporter.return_countdown",(trip.due-level.getGameTime()+19)/20),true);}
                continue;
            }
            if(!trip.charging){
                trip.nextRoom=secretDestination(level,trip);trip.charging=true;
                int charge=Math.max(1,trip.settings.getInt("chargeTime"))*20;trip.due=level.getGameTime()+charge;
                players(level,trip.players).stream().filter(p->p.getPersistentData().contains(RETURN)).forEach(p->charge(p,charge));continue;
            }
            if(trip.nextRoom!=null){
                var room=TeleporterSystem.find(level,trip.nextRoom);var travelers=players(level,trip.players).stream().filter(p->p.getPersistentData().contains(RETURN)).toList();
                if(room!=null&&travelers.stream().allMatch(p->safe(p,room))){travelers.forEach(p->move(p,room));trip.secret=true;trip.charging=false;trip.nextRoom=null;trip.due=level.getGameTime()+trip.settings.getInt("secretStay")*20L;continue;}
                trip.nextRoom=null;
            }
            if(!returnPlayers(level,trip)){trip.secret=true;trip.due=level.getGameTime()+20;continue;}
            if(trip.defcon)TeleporterRegistry.get(level).clearConditions(trip.channel);
            cooldown(level,trip.source,trip.settings);JOURNEYS.get(level).remove(trip);
            LuaScriptManager.callEvent("OnTeleporterTripEnded",trip.channel);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.getPersistentData().contains(RETURN))return;
        CompoundTag n=player.getPersistentData().getCompound(RETURN);
        ResourceLocation id=ResourceLocation.tryParse(n.getString("dimension"));if(id==null)return;
        ServerLevel level=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,id));if(level==null)return;
        var receiver=TeleporterSystem.find(level,n.getString("receiver"));
        if(receiver==null){BlockPos source=BlockPos.of(n.getLong("source"));level.getChunkAt(source);receiver=TeleporterSystem.pad(level,source);}
        if(receiver!=null&&safe(player,receiver)){move(player,receiver);player.getPersistentData().remove(RETURN);}
    }
}
