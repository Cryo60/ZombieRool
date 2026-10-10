package me.cryo.zombierool.gameplay;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CMatchAtmospherePacket;
import me.cryo.zombierool.entity.MrChiefEntity;
@Mod.EventBusSubscriber(modid="zombierool")
public final class MatchAtmosphereSync {
    private static final java.util.Map<ServerLevel,java.lang.ref.WeakReference<MrChiefEntity>> parties=new java.util.WeakHashMap<>();
    public static boolean active(ServerLevel level){var ref=parties.get(level);var chief=ref==null?null:ref.get();return chief!=null&&!chief.isRemoved()&&chief.isPartying();}
    public static void begin(MrChiefEntity chief){if(chief.level() instanceof ServerLevel level)parties.put(level,new java.lang.ref.WeakReference<>(chief));}
    private static final java.util.Map<ServerLevel,S2CMatchAtmospherePacket> last=new java.util.WeakHashMap<>();
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        long start=-1,end=-1;
        var ref=parties.get(level);var chief=ref==null?null:ref.get();
        if(chief!=null&&!chief.isRemoved()&&chief.isPartying()){start=chief.partyStart();end=start+chief.partyDuration();}else parties.remove(level);
        var packet=new S2CMatchAtmospherePacket(WaveManager.remainingEnemies(),start,end);
        if(!packet.equals(last.get(level))||level.getGameTime()%20==0){last.put(level,packet);NetworkHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),packet);}
    }
}
