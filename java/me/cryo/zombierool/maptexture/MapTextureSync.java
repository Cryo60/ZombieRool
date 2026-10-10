package me.cryo.zombierool.maptexture;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CMapTextureSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Arrays;

@Mod.EventBusSubscriber(modid="zombierool")
public final class MapTextureSync {
    private static final Map<net.minecraft.server.MinecraftServer,S2CMapTextureSlotPacket[]> previous=new WeakHashMap<>();
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player){var level=player.server.overworld();MapTextures.syncFolder(level);refresh(level,player);}
    }
    /** Sends changed slots to everybody and a full snapshot to a newly joining player. */
    public static void refresh(ServerLevel level,ServerPlayer joining){
        var server=level.getServer();var root=server.getWorldPath(LevelResource.ROOT);String[] names=MapTextures.readSlots(root);
        var old=previous.get(server);var next=new S2CMapTextureSlotPacket[MapTextures.SLOTS];
        for(int slot=0;slot<MapTextures.SLOTS;slot++){
            byte[][] faces;
            try{faces=MapTexturePixels.read(root.resolve("zombierool/custom_blocks"),names[slot]);}
            catch(Exception e){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Cannot load map texture {}",names[slot],e);try{faces=MapTexturePixels.read(root,"");}catch(java.io.IOException impossible){throw new IllegalStateException(impossible);}}
            next[slot]=new S2CMapTextureSlotPacket(slot,names[slot],MapTextures.soundId(slot),faces);
            boolean changed=old==null||!old[slot].name().equals(names[slot])||!old[slot].sound().equals(next[slot].sound())||!Arrays.deepEquals(old[slot].faces(),faces);
            final var packet=next[slot];
            if(changed)NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),packet);
            else if(joining!=null)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->joining),packet);
        }
        previous.put(server,next);
    }
}
