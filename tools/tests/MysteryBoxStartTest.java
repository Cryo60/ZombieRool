package local.strathprobe;

import me.cryo.zombierool.gameplay.MysteryBoxManager;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.scripting.LuaScriptManager;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import java.util.*;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.block.system.MysteryBoxSystem;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("strathprobe")
public final class MysteryBoxStartTest {
    public MysteryBoxStartTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    private static java.lang.reflect.Field playerField(String named,String srg)throws Exception{java.lang.reflect.Field f;try{f=net.minecraft.server.players.PlayerList.class.getDeclaredField(named);}catch(NoSuchFieldException e){f=net.minecraft.server.players.PlayerList.class.getDeclaredField(srg);}f.setAccessible(true);return f;}
    @SuppressWarnings("unchecked") private void start(ServerStartedEvent event){var level=event.getServer().overworld();try{
        var wanted=new BlockPos(-119,92,100);var state=level.getBlockState(wanted);
        if(!(state.getBlock() instanceof MysteryBoxBlock))throw new AssertionError("Requested coordinates do not contain a Mystery Box");
        var main=state.getValue(MysteryBoxBlock.PART)?MysteryBoxManager.getOppositeOtherPartPos(wanted,state.getValue(MysteryBoxBlock.FACING)):wanted;
        var alt=main.offset(10,0,0);var facing=Direction.EAST;var other=MysteryBoxManager.getOtherPartPos(alt,facing);
        level.setBlock(alt,MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxBlock.FACING,facing).setValue(MysteryBoxBlock.PART,false),2);
        level.setBlock(other,MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxBlock.FACING,facing).setValue(MysteryBoxBlock.PART,true),2);
        var config=WorldConfig.get(level);config.addMysteryBoxPosition(alt);config.addMysteryBoxPosition(other);
        var server=event.getServer();
        var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.set(null,true);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"BoxProbe"));
        level.addNewPlayer(player);player.setGameMode(GameType.SURVIVAL);player.setPos(-119,92,105);
        ((List<ServerPlayer>)playerField("players","f_11196_").get(server.getPlayerList())).add(player);
        ((Map<UUID,ServerPlayer>)playerField("playersByUUID","f_11197_").get(server.getPlayerList())).put(player.getUUID(),player);
        LuaScriptManager.loadScripts(level);
        var pulse=new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server);
        var manager=MysteryBoxManager.get(level);
        for(int round=0;round<16;round++){
            LuaScriptManager.callEvent("OnGameStart");
            manager.setupInitialMysteryBox(level,0);
            manager.forceLocation(level,alt,false);
            server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
            LuaScriptManager.onServerTick(pulse);
            if(manager.getRegisteredLocationsCount()<2||!level.getBlockState(main).getValue(MysteryBoxBlock.ACTIVE)||level.getBlockState(alt).getValue(MysteryBoxBlock.ACTIVE)||manager.isLocked)throw new AssertionError("Wrong initial location at match "+round);
            manager=MysteryBoxManager.load(manager.save(new CompoundTag()));
            level.getDataStorage().set("zombierool_mysterybox_manager",manager);
        }
        System.out.println("BOX CHECK: 16 match initializations and saved-manager reloads always select -119 92 100 despite another eligible location");
        manager.forceLocation(level,alt,false);
        if(!level.getBlockState(alt).getValue(MysteryBoxBlock.ACTIVE)||manager.isLocked)throw new AssertionError("The starting preference locked subsequent movement");
        System.out.println("BOX CHECK: Subsequent relocation remains available and unlocked");
        server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
        LuaScriptManager.onServerTick(pulse);
        if(!level.getBlockState(alt).getValue(MysteryBoxBlock.ACTIVE))throw new AssertionError("One-shot Lua preference repeated");
        System.out.println("BOX PASS: original release, real Lua next-tick start over 16 matches, manager reloads, unlocked later movement");
    }catch(Throwable e){e.printStackTrace();}finally{event.getServer().halt(false);}}
}
