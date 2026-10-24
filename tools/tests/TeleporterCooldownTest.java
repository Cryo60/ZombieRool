package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class TeleporterCooldownTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("COOLDOWN CHECK: "+text);}
    public static void run(ServerLevel level)throws Exception{
        var positions=List.of(new BlockPos(1700,80,0),new BlockPos(1704,80,0),new BlockPos(1708,80,0),new BlockPos(1712,80,0));
        var pads=new ArrayList<TeleporterSystem.Pad>();
        for(int i=0;i<positions.size();i++){
            var pos=positions.get(i);level.setBlock(pos,TeleporterSystem.PLATFORM.get().defaultBlockState(),3);
            var pad=(TeleporterSystem.Pad)level.getBlockEntity(pos);pad.onLoad();
            var c=pad.config.copy();c.putString("id","cooldown-test-"+i);c.putString("channel","main");c.putBoolean("requiresPower",false);c.putBoolean("sharedCooldown",true);c.putInt("cooldown",30);pad.configure(c);pads.add(pad);
        }
        var registry=TeleporterRegistry.get(level);
        BlockPos unloaded=positions.get(0).east(1000);var stored=pads.get(0).config.copy();stored.putString("id","unloaded-used");registry.pads.put(unloaded,stored);
        var cooldown=TeleporterTravel.class.getDeclaredMethod("cooldown",ServerLevel.class,BlockPos.class,CompoundTag.class,Set.class);cooldown.setAccessible(true);
        var cancel=TeleporterTravel.class.getDeclaredMethod("cooldown",ServerLevel.class,BlockPos.class,CompoundTag.class);cancel.setAccessible(true);
        long now=level.getGameTime(),until=now+600;
        pads.get(0).busy=true;pads.get(0).chargeUntil=now+20;
        pads.get(2).busy=true;pads.get(2).chargeUntil=now+40;
        cooldown.invoke(null,level,positions.get(0),pads.get(0).config.copy(),Set.of(positions.get(0),positions.get(1)));
        check(pads.get(0).cooldownUntil==until&&pads.get(1).cooldownUntil==until,"A to B starts cooldown only on A and B");
        check(pads.get(2).cooldownUntil==0&&pads.get(3).cooldownUntil==0&&stored.getLong("cooldownUntil")==0,"C, D and an unloaded pad sharing main remain available");
        check(!pads.get(0).busy&&pads.get(0).chargeUntil==0&&pads.get(2).busy&&pads.get(2).chargeUntil==now+40,"Completing a route cannot cancel another platform's ongoing departure");
        // A receiving pad may also be charging for a different group.
        pads.get(1).busy=true;pads.get(1).chargeUntil=now+60;
        cooldown.invoke(null,level,positions.get(0),pads.get(0).config.copy(),Set.of(positions.get(0),positions.get(1)));
        check(pads.get(1).busy&&pads.get(1).chargeUntil==now+60,"Arrival cooldown does not clear the receiver's independent charging state");
        for(var pad:pads){pad.cooldownUntil=0;pad.sync();}
        cancel.invoke(null,level,positions.get(0),pads.get(0).config.copy());
        check(pads.get(0).cooldownUntil==until&&pads.get(1).cooldownUntil==0&&pads.get(2).cooldownUntil==0,"Cancelled departure cools down only its source");
        for(var pad:pads){pad.cooldownUntil=0;pad.sync();}
        var own=pads.get(0).config.copy();own.putBoolean("sharedCooldown",false);
        cooldown.invoke(null,level,positions.get(0),own,Set.of(positions.get(0),positions.get(1)));
        check(pads.get(0).cooldownUntil==until&&pads.get(1).cooldownUntil==0,"Disabling shared cooldown retains source-only behavior");
        cooldown.invoke(null,level,positions.get(0),pads.get(0).config.copy(),Set.of(positions.get(0),unloaded));
        check(stored.getLong("cooldownUntil")==until,"Actually used unloaded pads receive persisted cooldown without a network-wide lock");
        // Timed journeys keep a record of actual destination, return and secret rooms.
        var journey=Arrays.stream(TeleporterTravel.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Journey")).findFirst().orElseThrow();
        var ctor=journey.getDeclaredConstructor(TeleporterSystem.Pad.class,List.class,String.class,boolean.class,BlockPos.class);ctor.setAccessible(true);
        var trip=ctor.newInstance(pads.get(0),List.of(),pads.get(0).config.getString("id"),false,positions.get(1));
        var used=journey.getDeclaredField("usedPads");used.setAccessible(true);
        check(((Set<?>)used.get(trip)).equals(Set.of(positions.get(0),positions.get(1))),"Timed journey records its actual source and destination rather than the whole channel");
        registry.pads.remove(unloaded);for(var pad:pads){pad.busy=false;pad.chargeUntil=0;pad.cooldownUntil=0;pad.sync();level.removeBlock(pad.getBlockPos(),false);}
        System.out.println("COOLDOWN TEST PASS");
    }
}
