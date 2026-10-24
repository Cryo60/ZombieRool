package local.worldloadingtests;

import me.cryo.zombierool.gameplay.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("worldloadingtests")
public final class WorldLoadingRegressionTest {
    private net.minecraft.server.MinecraftServer server;private int ticks;
    public WorldLoadingRegressionTest(){MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void started(ServerStartedEvent e){server=e.getServer();System.out.println("WORLD LOADING: server started on copied user save");}
    private void tick(TickEvent.ServerTickEvent e){
        if(server==null||e.phase!=TickEvent.Phase.END||++ticks<20)return;
        try{
            var level=server.overworld();var defs=BuildableSystem.get(level).definitions();
            if(defs.getBoolean("tranzitDefaults"))throw new AssertionError("Obsolete automatic recipe flag survived migration");
            var locks=WardenLocks.get(level).save(new net.minecraft.nbt.CompoundTag()).getList("locks",10);
            for(var tag:locks){var n=(net.minecraft.nbt.CompoundTag)tag;var pos=net.minecraft.core.BlockPos.of(n.getLong("pos"));level.getChunkAt(pos);}
            System.out.println("WORLD LOADING PASS: copied world ticks; "+defs.getCompound("recipes").size()+" authored recipes; "+locks.size()+" saved locks");
        }catch(Throwable failure){failure.printStackTrace();}finally{server.halt(false);}
    }
}
