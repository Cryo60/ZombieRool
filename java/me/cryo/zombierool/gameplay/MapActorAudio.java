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
        String key="voice_"+action;int delay=action.equals("hurt")?40:action.equals("attack")?20:80;
        if(state.contains(key)&&now-state.getLong(key)<delay)return;
        if(!action.equals("death")&&state.getLong("voice_until")>now)return;
        state.putLong(key,now);state.putLong("voice_until",now+(action.equals("aggro")||action.equals("slay")?100:20));
        var sound=ResourceLocation.tryParse(rows.getString(actor.getRandom().nextInt(rows.size())));if(sound==null)return;
        for(var player:level.players())if(player.distanceToSqr(actor)<=16*16)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new S2CPlayPositionalSoundPacket(sound,actor.blockPosition(),.85f,1));
    }
    private MapActorAudio(){}
}
