package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.MapActorEntity;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CPlayPositionalSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;

/** Voice files stay in the map's synchronized custom audio directory. */
public final class MapActorAudio {
    public static void play(MapActorEntity actor,String action){
        if(!(actor.level() instanceof ServerLevel level))return;
        var rows=actor.profile().getCompound("sounds").getList(action,8);if(rows.isEmpty())return;
        var state=actor.getPersistentData();long now=level.getGameTime();
        String key="voice_"+action;boolean wing=action.equals("wing"),movement=action.equals("move")||wing;int delay=wing?wingInterval(actor):movement?10:action.equals("hurt")?40:action.equals("attack")?20:80;
        if(state.contains(key)&&now-state.getLong(key)<delay)return;
        if(!movement&&!action.equals("death")&&state.getLong("voice_until")>now)return;
        state.putLong(key,now);if(!movement)state.putLong("voice_until",now+(action.equals("aggro")||action.equals("slay")?100:20));
        var sound=ResourceLocation.tryParse(rows.getString(actor.getRandom().nextInt(rows.size())));if(sound==null)return;
        String subtitle=actor.profile().getCompound("dialogue").getString(sound.toString());
        boolean announcement=action.equals("spawn");int range=announcement?32:16;
        for(var player:level.players())if(player.distanceToSqr(actor)<=range*range){
            if(announcement)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CPlayGlobalSoundPacket(sound,.85f,1));
            else NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new S2CPlayPositionalSoundPacket(sound,actor.blockPosition(),movement?.25f:.85f,1));
            MapDialogue.send(player,MapActors.displayName(actor.profile()).getString(),subtitle);
        }
    }
    private static int wingInterval(MapActorEntity actor){double speed=actor.getDeltaMovement().horizontalDistanceSqr();String clip=speed>.015?"run":speed>.0004?"walk":"idle";return Math.max(10,(int)Math.ceil(actor.profile().getCompound("animations").getCompound(clip).getDouble("duration")));}
    private MapActorAudio(){}
}
